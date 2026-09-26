# Queued User Messages

## Purpose
When the LLM is processing a task, user input is explicitly acknowledged, queued, and automatically chained as follow-up prompts once the current task completes. Rapid inputs are batched with length/window caps, consumption is FIFO, and cancel/stop preserves queued intent in memory without burning quota.

Queue ownership lives in **`AbstractAgent`**, not the UI — `call()` loops internally over queued messages, eliminating cross-agent contamination and UI-thread race risks ([ADR-0017](adr/0017-atomic-ui-chaining.md)).

## Rules & Use Cases (BDD)

### 1. Join Short Burst Messages ✅
- **Rule:** Messages sent within a configurable sliding window (default 10s) are joined into a single queue entry, capped at 300 combined chars using `System.lineSeparator()`. The timer resets on each merge to allow continuous rapid-fire sequencing. Incoming length restrictions are relaxed: messages >120 chars can still join if capacity permits.
```
GIVEN a user sends messages in a short sequence (≤window apart) AND the combined length is ≤300 chars
WHEN the messages are queued
THEN they are joined into a single entry with newline separators

GIVEN a message exceeds 300 combined chars OR the gap exceeds the configured window
WHEN it is queued
THEN it starts a new separate entry in the queue, resetting the window

GIVEN a queue is instantiated with a custom batch window (e.g., 250ms for tests)
WHEN messages arrive spaced by less than the configured window
THEN they merge correctly without requiring slow thread.sleep(10_000) calls in tests
```

### 2. Consume Waiting Queue (FIFO) ✅
- **Rule:** Queued messages are submitted one-by-one in FIFO order after the current task succeeds, handled internally by `AbstractAgent.call()`.
```
GIVEN we have waiting messages in the queue
WHEN the LLM finishes the work on the current message successfully
THEN the next message is submitted to the LLM and removed from the queue (FIFO)

GIVEN multiple messages are queued
WHEN the LLM processes them sequentially
THEN each is consumed individually, not batched into a single prompt
```

**Tests:** `AbstractAgentTest.testQueuedMessagesChainedFifo()` — verifies FIFO chaining end-to-end through `AbstractAgent.call()`.

### 3. Display in UI ✅
- **Rule:** A dedicated Stop button appears while working; Send/Mic remain active. `active.isWorking()` drives all UI state (no separate `actionsBar.isWorking()`).
```
GIVEN an LLM task is active
WHEN the user views the input area
THEN a dedicated Stop button is visible/enabled, while Send and Mic buttons remain fully functional

GIVEN the agent queues messages internally during chaining
WHEN the chain completes successfully
THEN no flicker occurs — the Stop button remains stable throughout all chained turns
```

### 4. Drain Queue on STOP / Error / RateLimit ✅
- **Rule:** Any abort (cancel, error, rate limit) preserves all queued messages in memory without auto-firing. Handled inside `AbstractAgent.call()` via `handleAbortAndDrain()`. Never burns quota on failure chains ([ADR-0018](adr/0018-abort-path-parity.md)).
```
GIVEN we have messages in the queue
WHEN the user hits STOP OR an API error occurs
THEN the current job stops AND the entire queue is added as a single UserMessage to the active agent's memory

GIVEN a RateLimitException occurs during processing
WHEN the internal loop catches the exception
THEN auto-fire is blocked and queue drains safely to memory instead

GIVEN the queue was drained on STOP/error
WHEN the drain completes
THEN a TOOL message shows "N queued message(s) preserved for your next request." in the chat history
```

**Tests:** `AbstractAgentTest.testAbortAddsMessageBeforeThrowing()` + `testAbortDrainsRemainingQueue()` — verifies abort drain behavior and TOOL notification end-to-end.

### 5. Compaction Survival & Clear Reset ✅
- **Rule:** Queue survives compaction; `agent.clear()` empties it.
```
GIVEN queued messages exist
WHEN compactSession runs
THEN the queued messages are excluded from compaction and survive intact

GIVEN queued messages exist
WHEN the user clicks "Clear" (calls agent.clear())
THEN the queue is emptied and no stale follow-up fires later
```

### 6. Conditional Acknowledgment Echo ✅
- **Rule:** The "Noted..." UI echo only fires when `queueMessage()` returns true (new entry created). Silent merges within the batch window/cap suppress the echo to avoid transcript spam. No UI-side volatile flags needed.
```
GIVEN the agent is working and a message is queued
WHEN the message silently merges into an existing batch (within window & ≤300 chars)
THEN no new "Noted..." acknowledgment appears in the chat history

GIVEN the agent is working and a message is queued
WHEN the message exceeds the window or length cap, forcing a new queue entry
THEN exactly one "Noted, I will respond as soon as I finished..." acknowledgment appears
```

### 7. Standing Orders Interaction ✅

- **Rule:** Queued messages are *payload*, not standing orders. They survive compaction by residing in `UserMessageQueue` (outside memory), not via the standing-orders re-injection mechanism. After `clearMemory()` restores context, the tool loop polls and adds the next queued message as a standard payload UserMessage.

**Conditional Add Strategy:** Standing orders use `hasUserText()` substring check for deduplication — if the order text is already in memory, it's not added again. This prevents duplicates within the same turn. Queued messages are always fresh payloads with no dedup needed.

```
GIVEN standing orders exist and queued messages are waiting
WHEN compactSession runs mid-turn
THEN standing orders are re-injected via clearMemory() (context)
AND queued messages survive in UserMessageQueue untouched (payload)
AND after clearMemory(), the loop polls the next queued message as a standard payload UserMessage

GIVEN a queued message contains text already present from standing orders
WHEN it's added to memory after compaction
THEN it is still added — no dedup across context vs. payload boundaries
```

**Tests:** `AbstractAgentTest.testQueuedMessagesChainedFifo()` demonstrates queue chaining through doCall (standing orders handled separately via ToolLoopRequest).

### 8. „!“-Messages: Queued-Message-Override direkt ins Agent-Memory ❌ specified (2026-09-25, Paul)

- **Zweck:** Escape-Hatch, wenn der Agent bereits arbeitet. Eine Nachricht, die mit `!` beginnt,
  umgeht die Queue vollständig und wird direkt in das `ThreadSafeMemory` des aktiven Agenten
  eingefügt.
- **Verhalten:**
  1. Gilt **nur, während der Agent arbeitet**. Wenn der Agent idle ist, startet die Eingabe wie gewohnt
     einen regulären Turn (keine Queue, kein Override nötig).
  2. Das führende `!` wird von der Nachricht entfernt.
  3. Beginnt der verbleibende Text mit `/` (Slash-Command oder Skill):
     - Lookup nach Command/Skill.
     - Wenn gefunden: `/command-name` wird durch den Command-Body ersetzt, der restliche User-Text
       wird angehängt.
     - Wenn nicht gefunden: Roher Text bleibt erhalten.
  4. Die resultierende Nachricht landet **direkt im Agent-Memory** (`ThreadSafeMemory`) und
     erscheint sofort in der Chat-History.
  5. **Kein neuer Turn, kein Follow-up:** Die Nachricht liegt im Memory; die AI sieht sie im
     nächsten Modell-Aufruf / nächsten Loop-Schritt des laufenden Turns und kann direkt reagieren.
  6. Kein `Noted...`-Echo, kein Quota-Verbrauch, kein `(queued …)`-Marker.
  7. **Homepage-Update:** Die Funktion muss auf der Homepage dokumentiert werden (user-visible).

```
GIVEN der Agent arbeitet aktuell
WHEN der User gibt eine Nachricht ein, die mit "!" beginnt (z.B. "!bitte abbrechen")
THEN wird das "!" entfernt
AND die Nachricht landet direkt im ThreadSafeMemory des aktiven Agenten
AND die Nachricht taucht sofort in der Chat-History auf
AND es wird KEIN Queue-Eintrag erzeugt und kein neuer Turn gestartet

GIVEN der Agent arbeitet aktuell
WHEN der User gibt "!/my-command rest text" ein
AND "/my-command" existiert als Command mit Body "instruction content"
THEN wird "/my-command" durch "instruction content" ersetzt
AND "instruction content rest text" landet direkt im ThreadSafeMemory
AND es wird kein separater Turn gestartet

GIVEN der Agent arbeitet NICHT
WHEN der User gibt eine Nachricht ein, die mit "!" beginnt
THEN startet ein normaler Agent-Turn mit der Nachricht (Regel greift nur bei laufendem Agent)
```

### 9. Queued-At Disclosure — Uhrzeit in onTool + Marker ❌ specified (2026-09-23, Paul)

Jeder Queue-Eintrag trägt einen **queuedAt**-Zeitstempel (Zeitpunkt des Queueings). Beim Konsum zeigt
**beides** die Uhrzeit im Format `(queued HH:mm)`:

- **onTool-Zeile:** `Reading queued User message: <text> (queued 14:32)` (`AbstractAgent`, in-loop-
  Pfad), 
- **LLM-Marker:** `[Queued Message] (queued 14:32): <text>` — auch im Follow-up-Pfad (Queue als
  Payload nach dem Compact).

Die Uhrzeit ist die Queue-Zeit, nicht die Konsum-Zeit; Burst-Join (Regel 1) zeigt die Zeit des
zusammengefassten Batches (erster Eintrag). Clock ist injizierbar (testbar).

```
GIVEN eine Message wird um 14:32 gequeued
WHEN sie in-loop (pollNext) oder als Follow-up konsumiert wird
THEN onTool-Zeile UND Marker tragen `(queued 14:32)`
AND der Message-Text bleibt unverändert hinter dem Präfix

GIVEN ein Burst (Regel 1) fasst 3 Messages zusammen
WHEN konsumiert THEN die Zeit des ersten Eintrags steht im Präfix
```

**Tests:** `AbstractAgentTest` — bestehende Queue-Tests erweitern um das Präfix-Assert (Clock injiziert).

## Data Flow
```
AIChatView.resolveOutgoingMessage() → active.queueMessage(trailing) [batching in core]
  → if isNewEntry: show "Noted..." echo (conditional on boolean return)
AbstractAgent.call(initial, monitor):
  CAS working true → loop { doCall(msg) → pollNext() } while next && success && !canceled
    catch Exception → handleAbortAndDrain(memory.add + TOOL msg) → rethrow
  finally: working false
AIChatView.handleDoneChatResponse():
  on ex/cancel: active.drainQueue() → memory.add + TOOL msg (for propagated failures)
  lockWhileWorking(false) — single unlock, no chaining logic in UI
```

## Components
- **`UserMessageQueue`** (`llmpeon-core`): thread-safe queue handling batching, FIFO polling, bulk draining. Configurable window for fast tests.
- **`AbstractAgent.messageQueue`**: core-owned instance; survives compaction (outside `AiAgent.getMemory()`).
- **`AbstractAgent.call()`**: internal do-while loop chains queued messages atomically within a single invocation.
- **`AIChatView`**: simplified — only locks/unlocks UI, reads `active.isWorking()`, delegates queue operations to agent.


## LLM Instruction for Agents
Agents run inside a message harness that buffers inputs asynchronously. Due to FIFO processing and async tool execution, older messages may arrive delayed — even while the agent is actively waiting for a response.

- **Inputs are async (FIFO, order uncertain).** For guaranteed answers: use the `askUser` tool.
- **Otherwise:** If a message is prefixed with `[Queued Message]`, it is **not** a reply to your last question. Acknowledge it briefly, re-ask your question if necessary, or treat the question as unanswered.
- *Context & Prompt adjustments:* See [PO Agent System Prompt](po.md) and `prompts/po.txt` for the agent-facing instructions solving this alignment problem.

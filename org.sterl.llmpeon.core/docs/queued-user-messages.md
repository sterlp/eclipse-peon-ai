# Queued User Messages

## Purpose
When the LLM is processing a task, user input is explicitly acknowledged, queued, and automatically chained as follow-up prompts once the current task completes. Rapid inputs are batched with length/window caps, consumption is FIFO, and cancel/stop preserves queued intent in memory without burning quota.

Queue ownership lives in **`AbstractAgent`**, not the UI — `call()` loops internally over queued messages, eliminating cross-agent contamination and UI-thread race risks ([ADR-0017](../../docs/adr/0017-atomic-ui-chaining.md)).

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
- **Rule:** Any abort (cancel, error, rate limit) preserves all queued messages in memory without auto-firing. Handled inside `AbstractAgent.call()` via `handleAbortAndDrain()`. Never burns quota on failure chains ([ADR-0018](../../docs/adr/0018-abort-path-parity.md)).
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

### 8. „!“-Messages: Urgent-Lane in der Queue ✅ done (2026-10-31 specified & gebaut, Branch `web-factory-goon`) — ersetzt die Memory-Injektion (✅ 2026-10-18, entfernt)

- **Zweck:** Escape-Hatch, wenn der Agent bereits arbeitet. Eine Nachricht, die mit `!` beginnt,
  wird dem laufenden Turn **sofort** zugestellt — nicht mehr per Memory-Injektion, sondern als
  **urgent-Eintrag in der Queue**, der mit dem nächsten Tool-Result an das Modell geht (Regel 11).
  Damit berührt der Pfad mid-turn **keine Memory**: die stillen Verlustfenster
  „Release-Hand-over“ (Turn endet zwischen letztem `pollNext()` und Release) und
  „Compact copy→clear→reseed“ sind strukturell geschlossen — die Queue überlebt den Compact
  (Regel 5) und der Hand-over ist atomar (Regel 10).
- **Verhalten:**
  1. Gilt **nur, während der Agent arbeitet** — Voraussetzung ist `active.isWorking()`. Ist der
     Agent idle, ist `!` ein normales Zeichen: die Eingabe startet einen regulären Turn mit dem
     Text **wie getippt** (unverändert, Entscheidung Paul 2026-10-18).
  2. Während der Agent arbeitet, wird das führende `!` entfernt und der Rest als **urgent-Eintrag**
     in die Queue gestellt — eigene Lane, **kein** Batching/Join mit normalen Einträgen (Regel 1
     gilt nur für die normale Lane), **kein** `Noted`-Echo (bleibt still, Paul 2026-10-31).
  3. Konsum: beim nächsten Tool-Call als Huckepack am Tool-Result (Regel 11). Endet der Turn,
     ohne dass ein Konsum geschah, gehen urgent-Einträge wie normale als Follow-up-Turn weiter —
     **FIFO über beide Lanes** nach Einfügungszeit (Paul 2026-10-31: urgent belegt nur den
     Tool-Attach-Kanal, nicht die Reihenfolge).
  4. `askUser` nimmt **alle** Queue-Einträge mit (urgent + normal) — Regel 12.
  5. Kein Quota-Verbrauch, kein neuer Turn, kein `[Queued Message]`-Doppelmarker am Turn-Ende.
  6. **`!` allein** (kein Text dahinter) im laufenden Turn wird konsumiert und verworfen — kein
     Queue-Eintrag, keine History-Zeile, kein Follow-up (unverändert).
  7. **Clean Break:** `AiAgent.injectUserMessage` und die Memory-Sonderfälle auf diesem Pfad
     entfallen — die View legt alles in die Queue.
  8. **Homepage-Update** im selben Inkrement („Send while working“ — sichtbares Verhalten ändert
     sich: Zustellung über den `queued_user_message`-Block statt direkter Context-Injektion).

```
GIVEN der Agent arbeitet aktuell
WHEN der User gibt eine Nachricht ein, die mit "!" beginnt (z.B. "!bitte abbrechen")
THEN wird das "!" entfernt
AND die Nachricht wird als urgent-Eintrag in die Queue gestellt (keine Memory-Injektion)
AND die Nachricht taucht sofort in der Chat-History auf (USER-Zeile)
AND es erscheint KEIN "Noted"-Echo
AND es wird kein neuer Turn gestartet

GIVEN der Agent arbeitet und ruft als Nächstes ein Tool auf
WHEN das Tool-Result entsteht
THEN sind ALLE urgent-Einträge im `queued_user_message`-Block dieses Results (Regel 11)
AND die urgent-Lane ist leer

GIVEN der Agent arbeitet und der Turn endet, ohne dass ein Konsum geschah
WHEN urgent-Einträge noch in der Queue sind
THEN werden sie als Follow-up-Turn konsumiert, in Einfügungsreihenfolge zusammen mit
     normalen Einträgen (FIFO über beide Lanes)

GIVEN der Agent arbeitet NICHT
WHEN der User gibt "!text" ein
THEN startet ein normaler Agent-Turn mit dem Text "!text" (unverändert)

GIVEN der Agent arbeitet aktuell
WHEN der User gibt nur "!" ein
THEN wird die Eingabe konsumiert (Input geleert, kein Queue-Eintrag, keine History-Zeile)
```

**Tests:** `OverrideMessageResolverTest` (Resolve-Logik unverändert: idle/unbekanntes
Command/Skill/Blank, Body+Rest, Rohtext-Fallback, `!` allein). Die Queue-/Attach-Mechanik wird
in Regel 11 getestet; der alte Memory-Injektionstest entfällt mit `injectUserMessage`.

### 9. Queued-At Disclosure — Uhrzeit in onTool + Marker ✅ done (2026-09-23 specified, gebaut)

Jeder Queue-Eintrag trägt einen **queuedAt**-Zeitstempel (Zeitpunkt des Queueings). Beim Konsum zeigt
**beides** die Uhrzeit — onTool-Zeile im Format `(queued HH:mm)`, LLM-Marker im Format `(HH:mm)` (2026-09-26, Paul):

- **onTool-Zeile:** `Reading queued User message: <text> (queued 14:32)` (`AbstractAgent`, in-loop-
  Pfad), 
- **LLM-Marker:** `[Queued Message] (14:32): <text>` — auch im Follow-up-Pfad (Queue als
  Payload nach dem Compact).

Die Uhrzeit ist die Queue-Zeit, nicht die Konsum-Zeit; Burst-Join (Regel 1) zeigt die Zeit des
zusammengefassten Batches (erster Eintrag). Clock ist injizierbar (testbar).

```
GIVEN eine Message wird um 14:32 gequeued
WHEN sie in-loop (pollNext) oder als Follow-up konsumiert wird
THEN onTool-Zeile trägt `(queued 14:32)` und der Marker `(14:32)`
AND der Message-Text bleibt unverändert hinter dem Präfix

GIVEN ein Burst (Regel 1) fasst 3 Messages zusammen
WHEN konsumiert THEN die Zeit des ersten Eintrags steht im Präfix
```

**Tests:** `UserMessageQueueTest.queuedLabelFormatsHHmmInClockZone` (Format, injizierte Clock) +
`AbstractAgentTest.callNullInitialWithQueuedProcessesQueueAsPayload` (Follow-up-Marker mit fixierter
14:32-Clock und `[Queued Message] (14:32):`-Assert). Die onTool-Zeile („Reading queued User message:
… (queued 14:32)") ist am IST vorhanden (`AbstractAgent:262-263`), aber nicht separat assertiert.

### 10. Turn-Slot Handover — Claim/Release atomar zur Queue ✅ done (2026-10-30)
- **Rule:** Der Turn-Slot (`working`) wird nur unter dem Monitor der `UserMessageQueue` genommen oder
  freigegeben. **Claim und Enqueue sind EIN kritischer Abschnitt** (`claimTurnSlotOrQueue`): ein Call
  findet entweder einen freien Slot und läuft selbst, oder er joint nachweisbar einem Turn, der die
  Queue noch garantiert ansieht — der Release ist atomar mit dem letzten Queue-Blick
  (`nextQueuedEntryOrReleaseSlot`). Ein Input kann dadurch nie zwischen letztem `pollNext()` und
  Release stranden (kein `done`, sichtbares Warten — P1-Fix 2026-10-30).
```
GIVEN ein Turn hält den Slot
WHEN eine Eingabe im Release-Fenster eintrifft (nach dem letzten pollNext, vor dem Release)
THEN der laufende Turn beantwortet sie noch (Hand-over), der späte Caller startet keinen eigenen Turn
→ AbstractAgentQueueReleaseRaceTest#inputArrivingWhileTheTurnReleasesItsSlotIsAnsweredByThatTurn
```
- **Grenze (bewusst):** die Claim-Seite (CAS-fail → Enqueue atomar gegen Release) ist dieselbe
  Monitor-Paar-Invariante, aber per Rendezvous nicht deterministisch erzeugbar — sie lebt als
  Code-Invariante + dieser Regel, nicht als eigener Mutations-Test. Wer den Queue-Monitor ersetzt
  (privater Lock, ConcurrentQueue), muss die Invariante neu ableiten und den Race-Test neu fahren.

### 11. Tool-Result-Huckepack für urgent-Einträge ✅ done (2026-10-31 gebaut)

Beim **nächsten Tool-Call** der laufenden Iteration werden **alle** urgent-Einträge gezogen und als
**ein** `TaggedResult`-Block (head `queued_user_message`, [tagged-results.md](tagged-results.md))
an das Tool-Result angehängt — huckepack, damit der laufende Turn sie im nächsten Modell-Call sieht,
ohne die Memory zu berühren.

- **Ein Anhang pro Iteration:** laufen mehrere Tools parallel, erhält **genau ein** Ergebnis den
  Block (das erste) — nicht jedes.
- **Atomar unter dem Queue-Monitor:** der Drain der urgent-Lane passiert im selben kritischen
  Abschnitt wie Claim/Release (Regel 10) — nie außerhalb, sonst konkurriert er mit dem Hand-over.
- **Kein Doppelt:** ein konsumierter urgent-Eintrag erscheint am Turn-Ende **nicht** erneut als
  `[Queued Message]`-Marker — der Konsum ist abschließend.
- **Disclosure (Regel 9):** je Message die onTool-Zeile `Reading queued User message: <text>
  (queued HH:mm)` — die Zustellung ist sichtbar, keine stille Injektion.
- **Kein Batching:** urgent-Einträge werden nicht verschmolzen; alle pending Einträge stehen
  gemeinsam in dem einen Block.
- **Compact:** der angehängte Block ist kein reiner Block (R-TR-2) und bleibt im Compact-Input
  erhalten — die Zustellung ist dauerhaft Teil der Konversation.

```
GIVEN die urgent-Lane hält 2 Einträge AND die Iteration ruft Tools auf
WHEN das Tool-Result entsteht
THEN enthält GENAU EIN Result den `queued_user_message`-Block mit beiden Texten,
     je Message `(queued HH:mm)` (Regel 9)
AND die urgent-Lane ist leer
AND der Block trägt Start- und End-Zeile mit dem Head (R-TR-1)

GIVEN die urgent-Lane ist leer
WHEN ein Tool-Result entsteht
THEN unverändert — kein Block, keine onTool-Zeile
```

### 12. askUser-Huckepack: alle Queue-Einträge mit der Antwort ✅ done (2026-10-31 gebaut)

Nutzt das LLM das `askUser`-Tool (Plugin-only, `AskUserTool` — die Fabrik hat bewusst keines),
reisen **alle** Queue-Einträge — urgent und normal — im **selben** Tool-Result mit: als
`queued_user_message`-TaggedResult-Block prepended, **die Antwort des Users als letzte (unterste)
Nachricht** dahinter. Sie werden dabei endgültig konsumiert — aus diesen Einträgen startet kein
zusätzlicher Follow-up-Turn.

- **Drain atomar unter dem Queue-Monitor** — eine Agent-Methode, nicht der direkte `drainQueue()`
  des Tools (sonst Race mit dem Hand-over, Regel 10).
- **Ein TextContent:** Block + Antwort werden textuell komponiert — `contents(List)` ist
  `@Experimental` und `text()` wirft bei mehr als einem Element (der Compact/Render-Pfad in
  `ChatMessageUtil.extractMessages` ruft genau `text()`).
- **Disclosure (Regel 9):** onTool-Zeile je Message — der Block selbst ist die Zustellungs-Anzeige;
  ein `[Queued Message]`-Marker (Regel 9, Follow-up-Pfad) kommt im Huckepack **nicht** vor, sonst
  stünde die Uhrzeit doppelt.
- **Fenster:** während einer offenen Frage ist das Eingabefeld ausgeblendet — neue Queue-Einträge
  können währenddessen nicht entstehen; die Regel betrifft nur Einträge, die **vor** dem askUser
  gequeued wurden.
- **Cancel:** die Frage wird abgebrochen → bisheriges Verhalten (Regel 4: Drain in die Memory,
  TOOL-Zeile „N queued message(s) preserved …") — kein Huckepack, kein Verlust.
- **Leere Queue:** unverändertes Verhalten — nur die Antwort als Tool-Result.

```
GIVEN die Queue hält 2 normale und 1 urgent Eintrag AND das LLM ruft askUser auf
WHEN der User die Frage beantwortet
THEN das Tool-Result enthält den `queued_user_message`-Block mit allen 3 Einträgen
     (je `(queued HH:mm)`, Regel 9)
AND die letzte (unterste) Nachricht ist die Antwort auf die Frage
AND die Queue ist komplett leer
AND es startet kein zusätzlicher Follow-up-Turn aus diesen Einträgen

GIVEN die Queue ist leer
WHEN askUser
THEN unverändert — nur die Antwort als Tool-Result

GIVEN eine offene Frage
WHEN der User sie cancelt
THEN Regel 4 (Drain in die Memory) — kein Huckepack, kein Verlust
```

## Data Flow
```
AIChatView.resolveOutgoingMessage() → OverrideMessageResolver.resolve(text, working, commands, skills)
  → present ("!" while working): urgent queue entry + USER history line, no echo, no new turn (R8)
  → empty: active.queueMessage(trailing) [batching in core]
  → if isNewEntry: "Noted..." echo (conditional on boolean return)
AbstractAgent.call(initial, monitor):
  claimTurnSlotOrQueue — unter dem Queue-Monitor: CAS working true (Turn übernommen)
    oder enqueue → return null (der laufende Turn antwortet)
  loop { doCall(msg) → pollNext() } while next && success && !canceled
    catch Exception → handleAbortAndDrain(memory.add + TOOL msg) → rethrow
ToolService (Tool-Result-Assembly):
  Drain der urgent-Lane unter dem Queue-Monitor → EIN `queued_user_message`-Block pro Iteration
  an genau ein Tool-Result → der nächste Modell-Call sieht ihn (R11)
AskUserTool:
  Drain ALLER Lanes (Agent-Methode, Queue-Monitor) → Block + Antwort als EIN TextContent (R12)
  Release atomar mit letztem Queue-Blick (nextQueuedEntryOrReleaseSlot);
    finally: Release, falls nicht schon per Hand-over übergeben
AIChatView.handleDoneChatResponse():
  on ex/cancel: active.drainQueue() → memory.add + TOOL msg (for propagated failures)
  lockWhileWorking(false) — single unlock, no chaining logic in UI
ChatMarkdownWidget/chat.html: appendMessage versteckt den Live-Status NICHT mehr —
  Hide nur an den natürlichen Endpunkten (END-Chunk, Lock-Ende) (streaming-display R9)
```

## Components
- **`UserMessageQueue`** (`llmpeon-core`): thread-safe queue handling batching, FIFO polling, bulk draining — plus die **urgent-Lane** für `!`-Einträge (kein Batching, eigener Konsumkanal). Configurable window for fast tests.
- **`OverrideMessageResolver`** (`llmpeon-core`, `queuedmessages`): pure function `resolve(text, agentWorking, commands, skills) → Optional<OverrideCommand>` — alleinige Stelle für die `!`-Entscheidung (Regel 8); die View führt nur aus.
- **`AbstractAgent.messageQueue`**: core-owned instance; survives compaction (outside `AiAgent.getMemory()`).
- **`AbstractAgent.call()`**: internal do-while loop chains queued messages atomically within a single invocation.
- **`ToolService`**: hängt den urgent-Block pro Iteration an genau ein Tool-Result (Regel 11).
- **`AskUserTool`** (Plugin, `parts.tools`): blockiert auf die UI-Antwort und hängt alle Queue-Einträge als Block an das Antwort-Result (Regel 12).
- **`AIChatView`**: simplified — only locks/unlocks UI, reads `active.isWorking()`, delegates queue operations to agent. Keine Memory-Injektion mehr.


## LLM Instruction for Agents
Agents run inside a message harness that buffers inputs asynchronously. Due to FIFO processing and async tool execution, older messages may arrive delayed — even while the agent is actively waiting for a response.

- **Inputs are async (FIFO, order uncertain).** For guaranteed answers: use the `askUser` tool.
- **Otherwise:** If a message is prefixed with `[Queued Message]`, it is **not** a reply to your last question. Acknowledge it briefly, re-ask your question if necessary, or treat the question as unanswered.
- **`queued_user_message` block:** a harness block with that head — attached to a tool result or to an askUser answer — carries **real user messages** that arrived while work was in progress. React to them in your current work; they are not part of the tool's output. The user's answer to `askUser` is the **last** message below such a block.
- *Context & Prompt adjustments:* See [Peon-PO (Jon)](po-agent-jon.md) and `prompts/po.md` for the agent-facing instructions solving this alignment problem.

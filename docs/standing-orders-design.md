# Standing Orders Design

> **Hinweis 2026-08-15:** Der Dynamic-Context-Flow (was in die Chat History injiziert wird,
> contains-Check, lazy-loading) ist jetzt in [context-architecture.md](context-architecture.md)
> dokumentiert. Diese Doc beschreibt den historischen Design und die Compaction-Survival-Mechanik.
> Potenzial zum Aufräumen: `ToolLoopRequest.standingOrders` ist deprecated, der neue Flow läuft
> über `turnContextSupplier`.

## Purpose

**Standing orders** are the context lines prepended to every user message: the selected
resource/project context, `AGENTS.md`, and the body of an active `/command` or `/skill`. They
must keep governing the task even when the conversation is compacted mid-tool-loop.

Before this design, a `/command` replaced the system prompt for a single turn
(`AbstractChatService.oneShotSystemPrompt`). If the model called the `compactSession` tool during a
tool chain, memory was cleared and the command context was lost. Commands and skills now share one
path and are re-injected after compaction.

## Use cases (BDD)

### 1. Happy Path — Combined User Message ✅

**Rule:** Standing orders are prepended to the user message if they aren't already in memory (`hasUserText()` check). Consecutive UserMessages merge into one via `ThreadSafeMemory.add()`.

```
GIVEN we have standing orders from the StandingOrdersBuilder
AND the user also adds a message
AND all messages are new to the history (not already present)
WHEN the user hits send
THEN one user message is added containing both the standing orders and the user text
```

**Test:** Covered by `AbstractAgent.doCall()` integration; explicit unit test in `StandingOrdersBuilderTest`.

### 2. Command/Skill Case ✅

**Rule:** A slash command or skill body is added as a *one-time* standing order via `addOneTimeOrder()`. Any trailing text after the `/command` is sent as user message. Both are joined into one UserMessage in memory, so the command context survives any subsequent compaction.

```
GIVEN the user selects a slash command (e.g., /review)
WHEN we add this as a standing order via addOneTimeOrder()
AND we add any trailing text after the command as user message to the chat
THEN the command body and trailing text are joined together into one UserMessage

GIVEN a command was active when compaction runs mid-turn
WHEN compactSession is called by the AI
THEN the command context survives in memory (it was already part of the combined UserMessage)
AND the standing orders snapshot re-injects it after clearMemory()

GIVEN a /skill is used instead of a /command
WHEN the same flow runs
THEN the skill body behaves identically — joined with trailing text, survives compaction
```

**Test:** `StandingOrdersBuilderTest.testOneTimeOrderClearedAfterBuild()` + compact survival tests. Commands/skills share one path via `addOneTimeOrder()`.

### 3. Compaction Survival ✅ (existing: `testStandingOrdersSurviveCompaction`)

**Rule:** When compaction runs mid-AI-turn, standing orders are re-injected as UserMessages before the resume message, forming one merged message with the summary at the end. The snapshot of standing orders was captured at loop start (`List.copyOf`), so it includes any commands/skills from that turn.

```
GIVEN we have a long user session and an ongoing AI turn
WHEN the AI calls the compactSession tool
THEN the standing orders are re-injected as the first UserMessage after memory.clear()
AND the compact result (resume message) is added as another UserMessage
AND ThreadSafeMemory joins them into one big UserMessage where the summary is the last part
```

**Test:** `CompactSessionToolTest.testStandingOrdersSurviveCompaction()` + `testMultipleStandingOrdersSurviveCompaction()`

### 4. Plan Handover ✅

**Rule:** When plan→dev handover is triggered, the plan file path and handover instruction are added to standing orders so they govern the dev agent's first turn. The plan content itself becomes a UserMessage in the new agent's memory.

```
GIVEN we completed a planning turn and a plan was created (saved to disk)
WHEN the user triggers handover to the dev agent
THEN the plan file path with handover instruction is added to standing orders
AND the plan content is added as a UserMessage ("chat") to the next handover agent's memory
```

**Test:** `PeonAiServiceTest.testHandoffStandingOrder()` — verifies `_handoffLine` is set on handoff, consumed once by `get()`, then cleared.

## Data flow

### Normal send (no compaction)

```
AIChatView.applySlashCommandIfPresent()
  → standingOrders.addOneTimeOrder(commandOrSkillBody)   // commands + skills, one path
AIChatView.doSendMessage()
  → standingOrders.build()                                // providers + one-time orders, then cleared
  → active.setUserContextInformations(orders)
  → active.call(message)
      → orders (not already in memory via hasUserText()) prepended to the user message
      → ToolLoopRequest.builder().standingOrders(List.copyOf(userContextInformations))...
      → toolService.executeLoop(req)
```

### Compaction mid-turn

```
toolService.executeLoop(req)
  → CompactSessionTool.compactSession()
      → AiCompressorAgent summarizes memory (compressor untouched — mechanical re-injection)
      → request.clearMemory()
          → memory.clear()
          → for each order in standingOrders: memory.add(order as UserMessage)   // re-inject
      → memory.add("Session compacted. Resume the task using the preserved context.")
      → ThreadSafeMemory merges all consecutive UserMessages into one (KV-cache friendly)
```

### Queued Messages (Payload, not Standing Orders)

```
// Queue consumption stays in the tool loop — outside of standing orders
AbstractAgent.call():
  → loop { doCall(msg) → pollNext() } while next && success && !canceled
  // After clearMemory() restores context, loop polls queued message as standard payload UserMessage
  // No mutable standing orders list required — immutable snapshot is sufficient
```

## Components

### `StandingOrdersBuilder` (`org.sterl.llmpeon.core`)
Collects persistent context from `MessageProvider`s (project/selection, AGENTS.md) plus one-time
orders (command/skill bodies). `build()` returns the combined list and **clears** the one-time
orders, so a command/skill applies to exactly one send.

### `ToolLoopRequest.standingOrders`
Immutable snapshot of the standing orders captured at loop start (`List.copyOf`). Owns the
compaction-survival contract.

### `ToolLoopRequest.clearMemory()`
Clears memory, then re-injects each standing order as a `UserMessage`. With no standing orders it
behaves like `memory.clear()`. Commands are **user instructions**, so they are re-injected as user
messages, not system messages.

### `CompactSessionTool`
Delegates the clear to `request.clearMemory()` — it knows nothing about standing orders.

## Resolved Design Decisions

1. **Per-turn semantics:** Each `ToolLoopRequest` = one AI turn. Standing orders are added at turn start, deduplicated via `hasUserText()` (substring `.contains()` check). If compaction happens mid-turn, `clearMemory()` re-injects them so processing continues correctly.
2. **KV-cache trade-off:** Merging standing orders + user text into one `UserMessage` prevents role-alternation breaks (critical for strict LLM parsers) and keeps the static prefix contiguous for cache reuse. Slight token-reuse penalty vs separate messages is accepted for stability.
3. **Snapshot staleness:** Standing orders are snapshotted once per turn (`List.copyOf`). Mid-turn context changes won't reflect until next user send, which rebuilds via `StandingOrdersBuilder`. This avoids race conditions during a single AI reasoning step.
4. **Queued Messages = Payload (Not Context):** Queue consumption stays in the tool loop. Queued messages survive compaction naturally by residing in `UserMessageQueue` (outside memory). After `clearMemory()` restores context, the loop simply polls and adds the next queued message as a standard payload UserMessage. No mutable standing orders list required — immutable snapshot is sufficient.
5. **All BDD use cases implemented and tested.** See `PeonAiServiceTest`, `CompactSessionToolTest`, `StandingOrdersBuilderTest`.

## Notes / constraints

- **Message merging:** `ThreadSafeMemory.add()` joins consecutive `UserMessage`s into one (KV-cache
  friendly). After compaction the re-injected orders and the resume message therefore appear as
  ordered `TextContent` parts inside a single user message — the order survives, the cache stays
  intact.
- **Compressor untouched:** the re-injection is mechanical (in the tool call), not a prompt
  instruction. `compressor.txt` is not changed. The command/skill body is still summarized by
  `AiCompressorAgent`; excluding it from the compressor was deliberately left out of scope (it would
  require storing standing orders as separate messages).
- **Thread safety:** the standing-orders list is immutable after `ToolLoopRequest` construction;
  `clearMemory()` delegates to the synchronized `ThreadSafeMemory`.

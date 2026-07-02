# Standing Orders Design

## Purpose

**Standing orders** are the context lines prepended to every user message: the selected
resource/project context, `AGENTS.md`, and the body of an active `/command` or `/skill`. They
must keep governing the task even when the conversation is compacted mid-tool-loop.

Before this design, a `/command` replaced the system prompt for a single turn
(`AbstractChatService.oneShotSystemPrompt`). If the model called the `compactSession` tool during a
tool chain, memory was cleared and the command context was lost. Commands and skills now share one
path and are re-injected after compaction.

## Use cases (BDD)

```
GIVEN a /command is active
WHEN the model calls the compactSession tool
THEN the command body is re-injected so it keeps governing the continuation

GIVEN a /skill is active
WHEN the model calls the compactSession tool (same as command)
THEN the skill body is re-injected
```

## Data flow

```
AIChatView.applySlashCommandIfPresent()
  → standingOrders.addOneTimeOrder(commandOrSkillBody)   // commands + skills, one path
AIChatView.doSendMessage()
  → standingOrders.build()                                // providers + one-time orders, then cleared
  → active.setUserContextInformations(orders)
  → active.call(message)
      → orders (not already in memory) prepended to the user message
      → ToolLoopRequest.builder().standingOrders(List.copyOf(userContextInformations))...
      → toolService.executeLoop(req)
          → CompactSessionTool.compactSession()
              → AiCompressorAgent summarizes memory
              → request.clearMemory()      // memory.clear() + re-inject each order as UserMessage
              → memory.add("Session compacted. Resume the task using the preserved context.")
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

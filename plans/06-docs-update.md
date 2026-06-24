# Task 6: Docs Update — Design File + Commands Doc

**Feature Plan**: [commands-as-standing-orders.md](./commands-as-standing-orders.md)
**Depends on**: [Task 4](./04-aichat-view-replace-oneshot.md)

## Goal

Update user-facing documentation and create the design reference file.

## Files

| File | Action |
|------|--------|
| `doc/design/standing-orders-design.md` | Create new design file |
| `doc/docs/setup/commands.md` | Update "Effect" section |

## Changes

### doc/design/standing-orders-design.md (NEW)

Create design document covering:

```markdown
# Standing Orders Design

## Purpose
Standing orders are context messages that are prepended to every user message and survive session compaction. They ensure critical instructions (project context, AGENTS.md, command/skill bodies) persist across the entire tool loop.

## Data Flow
```
AIChatView.doSendMessage()
  → standingOrders.build()          // includes command/skill one-time orders
  → active.setUserContextInformations(...)
  → active.call(message)
      → userContextInformations prepended to user message
      → ToolLoopRequest.builder()
            .standingOrders(userContextInformations)
            .build()
      → toolService.executeLoop(req)
          → CompactSessionTool.compactSession()
              → request.clearMemory()
                  → memory.clear()
                  → re-inject each standing order as UserMessage
              → memory.add("Session compacted...")
```

## Components

### StandingOrdersBuilder
- Collects persistent context from `MessageProvider`s (project info, AGENTS.md, user context)
- Collects one-time orders from commands/skills
- `build()` returns the combined list and clears one-time orders

### ToolLoopRequest.standingOrders
- Snapshot of standing orders at loop start
- Survives `compactSession` via `clearMemory()`

### ToolLoopRequest.clearMemory()
- Clears memory then re-injects all standing orders as UserMessages
- No-op if standing orders are empty (behaves like `memory.clear()`)

## Compaction Survival
When the LLM calls `compactSession`:
1. Memory is summarized and cleared
2. Standing orders are re-injected as UserMessages
3. "Session compacted..." message is added last
4. LLM continues with full context intact

## Thread Safety
- Standing orders list is immutable after `ToolLoopRequest` construction
- `clearMemory()` delegates to `ThreadSafeMemory.clear()` (synchronized)
- No concurrent modification risk
```

### doc/docs/setup/commands.md — Update "Effect" section

Replace the "Effect" section:

```markdown
## Effect

- Commands are injected as standing orders — they are prepended to every user message in the current tool loop.
- Standing orders survive session compaction: if the LLM compacts the chat mid-task, the command body is re-injected automatically.
- After the tool loop ends, the command is consumed and does not carry over to subsequent messages.
- Skills work the same way — their body is a one-time standing order.
```

## Verification

1. Design file exists: `doc/design/standing-orders-design.md`
2. Commands doc updated: `doc/docs/setup/commands.md` "Effect" section reflects new behavior
3. VitePress config updated if needed (`doc/.vitepress/config.ts`) — design files are not in VitePress per AGENTS.md, so likely no change
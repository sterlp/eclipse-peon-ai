# Plan: Commands as Standing Orders Surviving Compaction

## 1. Context

**Goal**: Remove the one-shot system prompt mechanism for slash commands. Instead, commands (and skills) flow through `StandingOrdersBuilder` → `ToolLoopRequest` as "standing orders" that survive `compactSession` tool calls.

**Why**: Currently `/command` replaces the system prompt for one turn. If the LLM calls `compactSession` mid-tool-chain, the command context is lost. Standing orders re-injected after compaction fix this.

## 2. Design Decisions

| Decision | Rationale |
|----------|-----------|
| `ToolLoopRequest.clearMemory()` encapsulates clear + re-inject | Single responsibility, consistent with existing `getMemory()` |
| Standing orders stored as `List<String>` on `ToolLoopRequest` | Simple, matches existing `userContextInformations` type |
| Both commands AND skills use `StandingOrdersBuilder.addOneTimeOrder()` | Unified path, no special-casing |
| Remove `setOneShotSystemPrompt` entirely | Dead code after migration |
| Standing orders captured at `call()` entry, passed to `ToolLoopRequest` | Snapshot at loop start ensures one-time orders are preserved |

## 3. Architecture Decisions

**Data flow (new)**:
```
AIChatView.doSendMessage()
  → standingOrders.build()                    // includes command/skill one-time orders
  → active.setUserContextInformations(...)     // for user message prepend
  → active.call(message)
      → userContextInformations prepended to user message
      → ToolLoopRequest.builder()
            .standingOrders(userContextInformations)  // captured for compaction survival
            .build()
      → toolService.executeLoop(req)
          → CompactSessionTool.compactSession()
              → request.clearMemory()          // clears + re-injects standing orders
              → request.getMemory().add("Session compacted...")
```

**Component boundaries**: `ToolLoopRequest` owns the standing-orders survival contract. `CompactSessionTool` delegates to `request.clearMemory()` — it doesn't know about standing orders.

## 4. Affected Files

### Core (`/llmpeon-core/`)

| File | Change |
|------|--------|
| `src/main/java/org/sterl/llmpeon/AbstractChatService.java` | Remove `oneShotSystemPrompt` field, `setOneShotSystemPrompt()`, `hasOneShotSystemPrompt()`, `consumeOneShotSystemPrompt()`. Simplify `buildStaticMessages()`. Pass `userContextInformations` as `standingOrders` to `ToolLoopRequest` builder. |
| `src/main/java/org/sterl/llmpeon/tool/ToolLoopRequest.java` | Add `@Default @Getter List<String> standingOrders`. Add `clearMemory()` method. |
| `src/main/java/org/sterl/llmpeon/tool/tools/CompactSessionTool.java` | Replace `request.getMemory().clear()` with `request.clearMemory()`. |

### Plugin (`/org.sterl.llmpeon/`)

| File | Change |
|------|--------|
| `src/org/sterl/llmpeon/parts/AIChatView.java` | `applySlashCommandIfPresent()`: replace `active.setOneShotSystemPrompt(prompt)` with `standingOrders.addOneTimeOrder(prompt)` for commands. Skills path unchanged (already uses `addOneTimeOrder`). |

### Tests

| File | Change |
|------|--------|
| `/llmpeon-core/src/test/java/org/sterl/llmpeon/tool/tools/CompactSessionToolTest.java` | Add test: standing orders survive `compactSession` — verify they're re-injected after clear. |
| `/llmpeon-core/src/test/java/org/sterl/llmpeon/AiDeveloperServiceTest.java` | Update `test_context` to verify standing orders on `ToolLoopRequest`. Add test for `clearMemory()` re-injection. |
| `/org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/StandingOrdersBuilderTest.java` | Add test: command one-time order flows through build. |

### Docs

| File | Change |
|------|--------|
| `/doc/docs/setup/commands.md` | Update "Effect" section: commands are now standing orders that survive compaction, not system prompt replacements. Update usage description. |
| `/doc/design/` | New file: `standing-orders-design.md` — documents the standing orders mechanism, compaction survival, and data flow. |

## 5. Rules & Constraints

- **Thread safety**: `ToolLoopRequest.clearMemory()` delegates to `ThreadSafeMemory.clear()` (already synchronized). Standing orders list is immutable after builder construction — no concurrent modification risk.
- **KV-cache**: Re-injecting standing orders as `UserMessage` after clear is safe — memory is empty, so no message ordering violations.
- **API contracts**: `ToolLoopRequest` builder pattern preserved. `CompactSessionTool` public signature unchanged.
- **Error handling**: If standing orders are empty, `clearMemory()` behaves identically to `memory.clear()` — no regression.

## 6. BDD Use Cases

**UC1: Command survives compaction (happy path)**
```
GIVEN user types "/review refactor this class"
AND the LLM calls compactSession during the tool chain
WHEN CompactSessionTool executes
THEN memory is cleared
AND the review command body is re-injected as a UserMessage
AND the "Session compacted. Resume..." message follows it
AND the LLM continues with the review command context intact
```
→ Test: `CompactSessionToolTest.testStandingOrdersSurviveCompaction` (unit)

**UC2: Command without compaction (happy path)**
```
GIVEN user types "/review refactor this class"
AND the LLM does NOT call compactSession
WHEN the call completes
THEN the command body was prepended to the user message
AND the next message without /command does NOT include the command
```
→ Test: `AiDeveloperServiceTest.testCommandAsStandingOrder` (integration)

**UC3: No standing orders — clearMemory is identity**
```
GIVEN ToolLoopRequest has empty standingOrders
WHEN clearMemory() is called
THEN memory is cleared with no additional messages added
```
→ Test: `CompactSessionToolTest.testClearMemoryWithoutStandingOrders` (unit)

**UC4: Multiple standing orders survive compaction**
```
GIVEN standing orders contain [project context, AGENTS.md, command body]
AND compactSession is called
WHEN clearMemory() executes
THEN all three standing orders are re-injected in order
AND the compaction resume message follows
```
→ Test: `CompactSessionToolTest.testMultipleStandingOrdersSurviveCompaction` (unit)

**UC5: Skill via slash command survives compaction**
```
GIVEN user types "/someSkill do X"
AND the skill body is added as one-time order
AND compactSession is called
WHEN clearMemory() executes
THEN the skill body is re-injected as standing order
```
→ Test: `StandingOrdersBuilderTest.testSkillOneTimeOrderSurvivesBuild` (unit)

**UC6: UI manual compact + next message restores standing orders**
```
GIVEN user clicks "Compact" button (doCompressContext)
AND memory is cleared via AbstractChatService.compressContext()
WHEN user sends next message
THEN standingOrders.build() re-adds persistent orders (project, AGENTS.md)
AND the new message includes them
```
→ Test: `AiDeveloperServiceTest.test_context` already covers this (integration)

## 8. Task order:

All done. Here's the summary:

File    Purpose
plans/commands-as-standing-orders.md    Full feature plan (saved as-is)
plans/01-tool-loop-request-standing-orders.md   Core: add standingOrders field + clearMemory() to ToolLoopRequest
plans/02-compact-session-use-clear-memory.md    Core: CompactSessionTool uses request.clearMemory() — UC1, UC3, UC4 tests
plans/03-abstract-chat-service-remove-one-shot.md   Core: remove oneShotSystemPrompt, pass standingOrders to builder — UC2 test
plans/04-aichat-view-replace-oneshot.md Plugin: replace setOneShotSystemPrompt with addOneTimeOrder
plans/05-standing-orders-builder-test.md    Plugin test: UC5 — one-time order flow through StandingOrdersBuilder
plans/06-docs-update.md Design doc + commands.md update
Execution order: 1 → 2 → 3 → mvn clean verify (core done) → 4 → 5 → mvn clean verify (plugin done) → 6

Each task references

## Status -- treat me as your long time memory of the progress

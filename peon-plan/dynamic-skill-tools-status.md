# Dynamic Skill Tools — Session Status

**Date:** 2026-05-31
**Feature:** Dynamic Skill Tools
**State:** ✅ COMPLETE

## What was implemented

### Core Interfaces
- `DynamicTool` — extends `SmartTool` with `getName()`, `getDescription()`, `execute()`, default `getToolSpecification()`. Non-reflective tool contract.
- `DynamicToolProvider` — functional interface returning `List<DynamicTool>`. Called on every `toolSpecifications()` build (no caching).

### ToolService Changes
- Added `CopyOnWriteArrayList<DynamicToolProvider> providers`
- Added `addProvider(DynamicToolProvider)` method
- `toolSpecifications()` now aggregates specs from static executors + dynamic providers
- `execute()` finds dynamic tools by name via provider iteration, injects context (withMonitor/withChatModel/withMemory), executes, resets in finally
- `findDynamicTool()` and `executeDynamicTool()` as private helpers

### SkillService Changes
- Implements `DynamicToolProvider`
- `getTools()` wraps each enabled skill as an anonymous `DynamicTool` (uses loop instead of streams to avoid Java generics type mismatch with anonymous classes)
- Tool name: `readSkill<name>` (spaces removed)
- Tool description: from YAML frontmatter `description` field
- Tool execution: returns `SkillPromptFile.readBody()` (includes path prefix)

### Other Changes
- `PeonAiService.java:131` — changed `addTool(new SkillTool(skillService))` to `addProvider(skillService)`
- `SkillTool.java` — deleted (superseded by dynamic approach)

## Tests (20 new, all passing)

| Test Class | What it verifies |
|---|---|
| `SkillToolRegistrationTest` | Skills appear as individual tools with `readSkill<name>` format and frontmatter descriptions |
| `DynamicToolExecutionTest` | Calling a skill tool returns skill body content including path prefix |
| `SkillRefreshTest` | Adding/removing a skill file updates tool list without explicit re-registration |
| `DisabledSkillTest` | Disabled skills (globally or individually) don't appear as tools |

## Test results
- **Total:** 173 tests (153 existing + 20 new)
- **Failures:** 0
- **Runner:** Eclipse JUnit test runner (core tests), `mvn clean verify` (full build)

## Bugs fixed during implementation
- `ToolService.toolSpecifications()` (no-arg) was missing dynamic tool specs — fixed to aggregate from providers

## Next steps (if any)
None — feature is complete and tested.

## Key decisions
- No caching of dynamic tools — queried fresh on every `toolSpecifications()` call
- No tool parameters — skills are "read-only" tools that return the skill body
- Context injection mirrors `SmartToolExecutor.run()` for consistency
- Anonymous classes in `getTools()` use a loop (not streams) to avoid Java generics type mismatch

## Files affected
```
llmpeon-core/src/main/java/org/sterl/llmpeon/tool/DynamicTool.java (new)
llmpeon-core/src/main/java/org/sterl/llmpeon/tool/DynamicToolProvider.java (new)
llmpeon-core/src/main/java/org/sterl/llmpeon/tool/ToolService.java (modified)
llmpeon-core/src/main/java/org/sterl/llmpeon/skill/SkillService.java (modified)
llmpeon-core/src/main/java/org/sterl/llmpeon/tool/tools/SkillTool.java (deleted)
org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonAiService.java (modified)
llmpeon-core/src/test/java/org/sterl/llmpeon/tool/SkillToolRegistrationTest.java (new)
llmpeon-core/src/test/java/org/sterl/llmpeon/tool/DynamicToolExecutionTest.java (new)
llmpeon-core/src/test/java/org/sterl/llmpeon/tool/SkillRefreshTest.java (new)
llmpeon-core/src/test/java/org/sterl/llmpeon/tool/DisabledSkillTest.java (new)
```

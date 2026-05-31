# Dynamic Skill Tools Plan

## Goal
Register each skill as its own dynamic tool in ToolService, with name `readSkill<skillName>` and description from YAML frontmatter. Skills discovered dynamically — tool list updates automatically.

## Design
- **No parameters** on skill tools — return skill body directly
- **Tool name:** `readSkill<name>` (e.g., `readSkillJavaFormatting`)
- **On-demand specs:** ToolService rebuilds dynamic tool specs from providers on every `toolSpecifications()` call
- **Provider pattern:** `DynamicToolProvider` interface; `SkillService` implements it
- **`SkillTool` deleted** — replaced by per-skill `DynamicTool` instances
- **DynamicTool wrappers** created fresh on each `getTools()` call

## Architecture
- `DynamicTool` extends `SmartTool` with `getName()`, `getDescription()`, `execute()`, default `getToolSpecification()`
- `DynamicToolProvider` returns `List<DynamicTool>`
- `ToolService.execute()` checks dynamic tools via provider iteration before MCP fallback
- Context injection mirrors `SmartToolExecutor.run()`: withMonitor/withChatModel/withMemory → execute → reset

## Files

### New
- `llmpeon-core/src/main/java/org/sterl/llmpeon/tool/DynamicTool.java`
- `llmpeon-core/src/main/java/org/sterl/llmpeon/tool/DynamicToolProvider.java`
- `llmpeon-core/src/test/java/org/sterl/llmpeon/tool/SkillToolRegistrationTest.java`
- `llmpeon-core/src/test/java/org/sterl/llmpeon/tool/DynamicToolExecutionTest.java`
- `llmpeon-core/src/test/java/org/sterl/llmpeon/tool/SkillRefreshTest.java`
- `llmpeon-core/src/test/java/org/sterl/llmpeon/tool/DisabledSkillTest.java`

### Modified
- `llmpeon-core/src/main/java/org/sterl/llmpeon/tool/ToolService.java` — add providers list, addProvider(), toolSpecifications() includes dynamic specs, execute() checks dynamic tools with context injection
- `llmpeon-core/src/main/java/org/sterl/llmpeon/skill/SkillService.java` — implement DynamicToolProvider, getTools() wraps enabled skills as DynamicTool
- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonAiService.java:131` — `addProvider(skillService)` instead of `addTool(new SkillTool(skillService))`

### Deleted
- `llmpeon-core/src/main/java/org/sterl/llmpeon/tool/tools/SkillTool.java`

## Status: DONE
- All 173 tests pass (153 existing + 20 new)
- Skills registered as individual `readSkill<name>` tools
- Tool list updates dynamically on skill refresh
- Disabled skills hidden from tool list
- No explicit re-registration needed

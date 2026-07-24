# ADR-0012: ToolService(boolean withDefaults) Constructor

**Status:** Accepted

## Context
`new ToolService()` auto-registers WebFetchTool, SearchAgentTool, ShellTool, CompactSessionTool. The scaffold agent only wants WebFetchTool from those.

## Decision
Add `ToolService(boolean withDefaults)` constructor. `new ToolService(false)` gives an empty registry. The scaffold agent gets `ToolService(false)`, then adds only: config-scoped disk tools, SkillTool, WebFetchTool, ReloadConfigTool. The existing no-arg constructor delegates to `this(true)`.

## Consequences
Small, backward-compatible API change. Prevents future default-tool leakage into scaffold agent.

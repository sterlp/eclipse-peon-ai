# ADR-0008: AiAgent.getToolService() Routing

**Status:** Accepted

## Context
`PeonAiService.getToolService()` is used by `AIChatView` (AskUserTool, ShellTool config) and `getToolStatus()`. With per-agent ToolService, these need to route to the active agent's tool service.

## Decision
Add `getToolService()` to `AiAgent` interface (default returns null; `AbstractAgent` overrides to return its field). `PeonAiService.getToolService()` returns `getActiveAgent().getToolService()`. `PeonAiService` keeps a private `sharedToolService` field for wiring that must affect all non-scaffold agents (MCP, AskUserTool, ShellTool, disk tool lifecycle). `AIChatView` calls that need the shared service use `aiService.getSharedToolService()`.

## Consequences
`AiAgent` interface gains one method. `AIChatView` needs minor adjustment for shared vs active tool service access. MCP tools are only available to agents using the shared ToolService — scaffold agent has no MCP, which is intentional.

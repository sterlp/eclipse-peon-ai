# ADR-0010: Standing Orders via PeonAiService setActiveAgent Hook

**Status:** Accepted

## Context
StandingOrdersBuilder is shared across all agents; scaffold agent needs special context injection on activation.

## Decision
In `PeonAiService.setActiveAgent()`, check if agent instanceof AiScaffoldAgent. If so: build standing orders — config dir path + output of `diskListDirectory()` on the `.peon` root — and call `agent.setUserContextInformations(List.of(orders))`. Tutorial on first activation (memory.size == 0) shown via `getScaffoldTutorial()` in `AIChatView.onAgentChange()`.

## Consequences
Special-casing in PeonAiService, but isolated to one method. Standing orders give the agent immediate awareness of its scope.

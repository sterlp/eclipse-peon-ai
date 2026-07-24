# ADR-0009: ReloadTool as Dedicated Service Tool

**Status:** Accepted

## Context
The scaffold agent needs to trigger service refreshes after creating/editing artifacts.

## Decision
New `ReloadConfigTool` class in `org.sterl.llmpeon.scaffold` package, injected with references to AgentService, SkillService, CommandService, and LlmConfig. Single `reloadConfig()` method calls refresh on all three. Created by `PeonAiService` after `agentService` exists, passed into the scaffold agent's ToolService.

## Consequences
Tight coupling between tool and services, but acceptable for a dedicated scaffold-only tool.

**Constraint:** The tool holds a direct `LlmConfig` reference. If config changes at runtime (e.g., user changes `.peon` directory), the tool must read from `configuredModel.getConfig()` at call time — never use the constructor-injected config instance. Same pattern as the disk tools' `workingDir` refresh in `AiScaffoldAgent.call()`.

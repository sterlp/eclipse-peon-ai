# ADR-0007: Scaffold Agent as Built-in Java Class with Own ToolService

**Status:** Accepted

## Context
Custom YAML agents cannot have programmatic tools injected or special standing orders. The scaffold agent needs config-scoped disk tools that are independent of the global disk-tools-enabled setting.

## Decision
`AiScaffoldAgent` is a built-in Java class in core module. Created by `PeonAiService` after all services exist, with its own `ToolService(false)` containing exactly the tools it needs. Added to `AgentService` via `addPersistentAgent()`, surviving `clearAgents()` on reload.

## Consequences
Self-contained, no tool swap logic, no name collisions, no leaking. `AgentService` needs a concept of "persistent" agents that survive `clearAgents()`.

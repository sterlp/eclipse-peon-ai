# ADR-0013: Persistent Agents in AgentService

**Status:** Accepted

## Context
`AgentService.clearAgents()` clears the map and re-adds only `planAgent` + `devAgent`. The scaffold agent is added externally via `addAgent()` but would be dropped on every `reloadAgents()` call.

## Decision
Add `addPersistentAgent(AiAgent agent)` to `AgentService`. Persistent agents are stored in a separate `persistentAgents` map. `clearAgents()` re-adds both the built-in agents (dev/plan) AND all persistent agents. `getAgents()` includes both maps.

## Consequences
AgentService gains a second map + one method. Clean separation between built-in agents (managed by constructor) and externally-added persistent agents (managed by PeonAiService).

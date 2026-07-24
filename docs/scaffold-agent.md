# Scaffold Agent

## Goal

Provide a built-in agent that lets users create, edit, and manage Peon configuration artifacts (agents, skills, commands) through natural language conversation. The agent has direct disk access to the `.peon` config directory and can reload changes immediately.

## Business Rules

### R1: Agent Registration ✅
The scaffold agent is a built-in agent appearing alongside Peon-Dev and Peon-Plan in the dropdown.

- Name: `Peon-Scaffold`
- Package: `org.sterl.llmpeon.scaffold`
- Class: `AiScaffoldAgent extends AbstractAgent`
- Registered as a persistent agent — survives `clearAgents()` on reload

**BDD:**
```
GIVEN AgentService is constructed with withDefaultAgent=true
WHEN getAgents() is called
THEN Peon-Scaffold is in the list alongside Peon-Dev and Peon-Plan

GIVEN the scaffold agent is registered as a persistent agent
WHEN reloadAgents() is called
THEN Peon-Scaffold is still in the agents map
```

### R2: Tool Access ✅
The scaffold agent has its own `ToolService(false)` with exactly:
- Config-scoped `DiskFileReadTool`, `DiskFileWriteTool`, `DiskGrepTool` (workingDir = config.getConfigDir())
- `SkillTool` (shared instance)
- `WebFetchTool`
- `ReloadConfigTool`

No Eclipse workspace tools, no ShellTool, no CompactSessionTool, no SearchAgentTool, no MCP.

**BDD:**
```
GIVEN the user switches to Peon-Scaffold
THEN the agent's ToolService has config-scoped disk tools + SkillTool + WebFetchTool + ReloadConfigTool
AND no Eclipse workspace tools, shell, compact, or searchAgent tools are available

GIVEN disk tools are disabled in the global config
WHEN the user switches to Peon-Scaffold
THEN the agent still has access to config-scoped disk tools
```

### R3: Standing Orders on Activation ✅
On each activation, inject standing orders with config dir path and `.peon` root directory listing.

**BDD:**
```
GIVEN the user switches to Peon-Scaffold
THEN standing orders are injected with config dir path and directory listing of .peon root
```

### R4: Tutorial Message ✅
On first activation in a session (memory.size == 0), show a tutorial message. Subsequent activations within the same session only get standing orders.

**BDD:**
```
GIVEN the scaffold agent is activated for the first time in a session (memory.size == 0)
THEN a tutorial message appears in chat history

GIVEN the scaffold agent is already active with chat history
WHEN the user switches away and back
THEN no tutorial message is shown (memory.size > 0)
AND standing orders are injected
```

### R5: Reload Tool ✅
Single `reloadConfig()` tool that triggers refresh on AgentService, SkillService, and CommandService. Returns summary of what was reloaded.

**BDD:**
```
GIVEN the scaffold agent is active
WHEN the user asks "Create a new agent called CodeReviewer"
THEN the agent creates ~/.peon/agents/CodeReviewer/AGENT.md
AND calls reloadConfig() to make it immediately available

GIVEN a skill was just created via the scaffold agent
WHEN the agent calls reloadConfig()
THEN SkillService.refresh() picks up the new SKILL.md
AND Peon-Scaffold is still in the agents map
```

::: tip Observer migration point
The reload notification currently uses a `Runnable` callback wired through `ReloadConfigTool` →
`PeonAiService` → `AIChatView`. If more than one consumer needs to react to config reload events
(e.g. status bar, model list), replace with an Observer pattern: services fire `ReloadEvent` on
success/failure; interested parties subscribe via listener registration in their lifecycle.
:::

### R6: Config Must Be Re-Read Per Call ✅
The scaffold agent reads `configDir` from `configuredModel.getConfig()` on every `call()` and updates its disk tools' `workingDir` accordingly. The config can change at runtime (user changes `.peon` directory in preferences). `ConfiguredChatModel.updateConfig()` replaces the config object on every preference change, so the scaffold agent always sees the latest path.

**Same applies to any tool that holds a direct `LlmConfig` reference.** `ReloadConfigTool` is constructed with an `LlmConfig` instance — if config changes at runtime, it must read from `configuredModel.getConfig()` at call time, not use the constructor-injected instance. This is the same pattern as the disk tools.

**BDD:**
```
GIVEN the scaffold agent is active with a config directory pointing to ~/.peon
WHEN the user changes the config directory in preferences to ~/.peon-custom (before the next LLM call)
THEN the scaffold agent reads the new config dir from configuredModel.getConfig() on its next call()
AND the disk tools operate on ~/.peon-custom
AND the standing orders on the next activation show the new directory

GIVEN the scaffold agent is active with a ReloadConfigTool that was constructed with the old config
WHEN the user changes the config directory and the agent calls reloadConfig()
THEN the reload uses the new config dir (read from configuredModel.getConfig() at call time)
AND the services refresh from the new directory
```

### R7: Scaffold Agent Config ✅
The scaffold agent uses `devAgentConfig()` — same model, temperature, think as Peon-Dev.

## ADRs

- [ADR-0007](adr/0007-scaffold-agent-built-in.md) — Scaffold agent as built-in Java class with own ToolService
- [ADR-0008](adr/0008-aiagent-gettoolservice-routing.md) — AiAgent.getToolService() routing
- [ADR-0009](adr/0009-reloadtool-dedicated.md) — ReloadTool as dedicated service tool
- [ADR-0010](adr/0010-standing-orders-setactiveagent-hook.md) — Standing orders via PeonAiService hook
- [ADR-0011](adr/0011-agent-template-system-prompt.md) — Agent template as system prompt resource
- [ADR-0012](adr/0012-toolservice-boolean-constructor.md) — ToolService(boolean withDefaults) constructor
- [ADR-0013](adr/0013-persistent-agents.md) — Persistent agents in AgentService
- [ADR-0014](adr/0014-system-line-separator-in-llm-strings.md) — System.lineSeparator() in LLM strings

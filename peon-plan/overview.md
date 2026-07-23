# Scaffold Agent — Design

## Goal

Provide a built-in agent that lets users create, edit, and manage Peon configuration artifacts (agents, skills, commands) through natural language conversation. The agent has direct disk access to the `.peon` config directory and can reload changes immediately.

## Developer-Agent Hints

- `AgentService.clearAgents()` (line 153) clears the map and re-adds only `planAgent` + `devAgent`. **The scaffold agent is NOT managed by AgentService** — it's created by `PeonAiService` and added via `agentService.addAgent()`. But `clearAgents()` will still drop it on reload. Two options: (a) PeonAiService re-adds it after `reloadAgents()`, or (b) AgentService gets a `addPersistentAgent()` that `clearAgents()` preserves. Use (b) — cleaner, survives all future reload paths.
- `AiAgent` interface gets a `getToolService()` method (default returns the shared toolService passed via constructor). `AbstractAgent` already holds the field — just expose it. `PeonAiService.getToolService()` routes to `getActiveAgent().getToolService()`.
- The scaffold agent creates its own `ToolService(false)` — empty registry, then adds exactly: config-scoped DiskFileReadTool, DiskFileWriteTool, DiskGrepTool, SkillTool, WebFetchTool, ReloadConfigTool. No ShellTool, CompactSessionTool, SearchAgentTool.
- `PeonAiService` keeps a private `sharedToolService` field for internal wiring (MCP, AskUserTool, ShellTool, disk tool lifecycle for dev/plan agents).
- `PeonAiService.setActiveAgent()` hook: on scaffold activation, inject standing orders + tutorial. No tool swap needed — the agent's own ToolService has the right tools.
- `ReloadConfigTool` is created by `PeonAiService` after `agentService` exists (initialization order dependency), then passed into the scaffold agent's constructor.
- `getToolStatus()` naturally uses the active agent's tool service — no special routing needed.

## Business Rules

### R1: Agent Registration ❌
The scaffold agent is a built-in agent like AiDevAgent/AiPlanAgent, appearing after all custom agents in the dropdown (sorted alphabetically — "Peon-Scaffold" sorts after "Peon-Dev"/"Peon-Plan" but custom agent names may sort later).

- Name: `Peon-Scaffold`
- Package: `org.sterl.llmpeon.scaffold` (core module)
- Class: `AiScaffoldAgent extends AbstractAgent`
- Created by `PeonAiService` (not `AgentService`) after all services exist, added via `agentService.addAgent()`.
- **Critical:** Must survive `AgentService.clearAgents()` on reload — use `addPersistentAgent()` or equivalent so `clearAgents()` preserves it alongside dev/plan agents.

### R2: Tool Access ❌
The scaffold agent has its own `ToolService(false)` containing exactly:
- Config-scoped `DiskFileReadTool` with `workingDir=config.getConfigDir()`
- Config-scoped `DiskFileWriteTool` with `workingDir=config.getConfigDir()`
- Config-scoped `DiskGrepTool` with `workingDir=config.getConfigDir()`
- `SkillTool` (shared instance, already has SkillService reference)
- `WebFetchTool` (new instance)
- `ReloadConfigTool` (dedicated, with AgentService/SkillService/CommandService/LlmConfig refs)

No other tools. No tool filter needed — `getToolFilter()` returns `p -> true` and the ToolService contents ARE the filter. MCP tools are not connected to this ToolService (scaffold doesn't need MCP).

### R3: Standing Orders on Activation ❌
When the scaffold agent is activated, inject standing orders containing:
1. Config directory path (e.g., `/Users/sterlp/.peon`)
2. Directory listing of `.peon` root (from `diskListDirectory()` — shows subdirs: agents, skills, commands)

This gives the agent immediate awareness of its scope and existing artifacts.

### R4: Tutorial Message ❌
On first activation in a session (when `agent.getMemory().size() == 0`), display a short tutorial message in chat history explaining what the agent can do. Content loaded from `scaffold-tutorial.txt` resource file. On subsequent activations within the same session, only standing orders are injected — no tutorial. This mirrors the `preloadPlanIfNeeded()` pattern in PeonAiService.

### R5: Reload Tool ❌
Single `reloadConfig()` tool that triggers refresh on all three services:
- AgentService.reloadAgents()
- SkillService.refresh(dir)
- CommandService.refresh(dir)

Returns summary of what was reloaded.

### R6: Scaffold Agent Config ❌
The scaffold agent uses the default dev config (`devAgentConfig()`). No separate model or temperature override — follows the same pattern as `AiDevAgent`. Think follows the global default.

## BDD Use Cases

```
GIVEN AgentService is constructed with withDefaultAgent=true
WHEN getAgents() is called
THEN Peon-Scaffold is in the list alongside Peon-Dev and Peon-Plan

GIVEN the scaffold agent is registered as a persistent agent
WHEN reloadAgents() is called (e.g. after creating a new custom agent via reloadConfig)
THEN Peon-Scaffold is still in the agents map (clearAgents preserves persistent agents)

GIVEN the user switches to "Peon-Scaffold" in the agent dropdown
WHEN the agent is activated for the first time in a session (memory.size == 0)
THEN a tutorial message appears in chat history explaining capabilities
AND standing orders are injected with config dir path and directory listing of .peon root
AND the scaffold agent's own ToolService is used (config-scoped disk tools + skill + web + reload)
AND no Eclipse workspace tools, shell, compact, or searchAgent tools are available

GIVEN the scaffold agent is already active with chat history
WHEN the user switches to another agent and back to Peon-Scaffold
THEN no tutorial message is shown (memory.size > 0)
AND standing orders are injected with config dir path and directory listing

GIVEN the user switches from Peon-Scaffold to Peon-Dev
WHEN Peon-Dev is activated
THEN Peon-Dev uses the shared ToolService (with workspace disk tools if enabled)
AND no ReloadConfigTool is present in Peon-Dev's tool list

GIVEN the scaffold agent is active with standing orders showing config dir and directory listing
WHEN the user asks "Create a new agent called CodeReviewer that only has grep and read tools"
THEN the agent creates ~/.peon/agents/CodeReviewer/AGENT.md with proper frontmatter
AND calls reloadConfig() to make it immediately available

GIVEN the scaffold agent is active
WHEN the user asks "List all my skills"
THEN the agent uses skillList() or diskListDirectory on ~/.peon/skills and returns the listing

GIVEN a skill was just created via the scaffold agent
WHEN the agent calls reloadConfig()
THEN SkillService.refresh() picks up the new SKILL.md
AND the skill appears in the status line dropdown
AND Peon-Scaffold is still in the agents map (not dropped by reloadAgents)

GIVEN the config directory doesn't exist yet or is empty
WHEN the scaffold agent is activated
THEN the standing orders show the directory listing (empty or with missing subdirs)
AND the agent reports which subdirectories are missing and offers to create them

GIVEN disk tools are disabled in the global config
WHEN the user switches to the Peon-Scaffold agent
THEN the agent still has access to config-scoped disk tools (independent of global setting)
AND the disk tools appear in the available tool list

GIVEN the scaffold agent is active and the user wants to create a skill
WHEN the user asks "Create a new skill called code-review based on the writing-skill"
THEN the agent calls skillRead("writing-skill") to learn the pattern
AND creates ~/.peon/skills/code-review/SKILL.md following the same structure
AND calls reloadConfig() to make it immediately available
```

## Technical Design (ADRs)

### ADR-0007: Scaffold Agent as Built-in Java Class with Own ToolService
**Status:** Proposed  
**Context:** Custom YAML agents cannot have programmatic tools injected or special standing orders. The scaffold agent needs config-scoped disk tools that are independent of the global disk-tools-enabled setting.  
**Decision:** `AiScaffoldAgent` is a built-in Java class in core module. It's created by `PeonAiService` (not `AgentService`) after all services exist, with its own `ToolService(false)` containing exactly the tools it needs. Added to `AgentService` via `addAgent()` as a persistent agent that survives `clearAgents()`.  
**Consequences:** Self-contained, no tool swap logic, no name collisions, no leaking. `AgentService` needs a concept of "persistent" agents that survive `clearAgents()`.

### ADR-0008: AiAgent.getToolService() Routing
**Status:** Proposed  
**Context:** `PeonAiService.getToolService()` is used by `AIChatView` (AskUserTool, ShellTool config) and `getToolStatus()`. With per-agent ToolService, these need to route to the active agent's tool service.  
**Decision:** Add `getToolService()` to `AiAgent` interface (default returns the shared toolService passed via constructor — `AbstractAgent` already holds the field). `PeonAiService.getToolService()` returns `getActiveAgent().getToolService()`. `PeonAiService` keeps a private `sharedToolService` field for wiring that must affect all non-scaffold agents (MCP, AskUserTool, ShellTool, disk tool lifecycle). `AIChatView` calls that need the shared service use `aiService.getSharedToolService()` (new accessor). `getToolStatus()` naturally uses the active agent's tool service.  
**Consequences:** `AiAgent` interface gains one method. `AIChatView` needs minor adjustment for shared vs active tool service access. MCP tools are only available to agents using the shared ToolService (dev/plan/custom) — scaffold agent has no MCP, which is intentional.

### ADR-0009: ReloadTool as Dedicated Service Tool
**Status:** Proposed  
**Context:** The scaffold agent needs to trigger service refreshes after creating/editing artifacts.  
**Decision:** New `ReloadConfigTool` class in `org.sterl.llmpeon.scaffold` package, injected with references to AgentService, SkillService, CommandService, and LlmConfig. Single @Tool method `reloadConfig()` that calls refresh on all three. Created by `PeonAiService` after `agentService` exists, passed into the scaffold agent's own ToolService.  
**Consequences:** Tight coupling between tool and services, but acceptable for a dedicated scaffold-only tool.

### ADR-0010: Standing Orders via PeonAiService setActiveAgent Hook
**Status:** Proposed  
**Context:** StandingOrdersBuilder is shared across all agents; scaffold agent needs special context injection on activation.  
**Decision:** In `PeonAiService.setActiveAgent()`, check if agent instanceof AiScaffoldAgent. If so:
1. Build standing orders string — config dir path + output of `diskListDirectory()` on the `.peon` root — and call `agent.setUserContextInformations(List.of(orders))`
2. If `agent.getMemory().size() == 0`, display tutorial message via `monitor.onChatResponse(new SimpleMessage(Type.INFO, tutorial))`
3. If memory size > 0, inject standing orders silently (no tutorial)
This mirrors the `preloadPlanIfNeeded()` pattern — context injection only on first activation in a session.  
**Consequences:** Special-casing in PeonAiService, but isolated to one method. Standing orders give the agent immediate awareness of its scope.

### ADR-0011: Agent Template as System Prompt Resource
**Status:** Proposed  
**Context:** The scaffold agent needs guidance on how to create AGENT.md, SKILL.md, and command files — including frontmatter structure, naming conventions, and file placement.  
**Decision:** `agent-template.txt` is loaded as a resource in the scaffold agent's `getSystemPrompt()` via `PromptLoader.withDefault(agent-template.txt)`. Contains concise frontmatter examples for agents, skills, and commands — plus directory structure instructions (e.g. `~/.peon/agents/<name>/AGENT.md`). Analogous to how `developer.txt` extends `default.txt`.  
**Consequences:** Agent gets built-in structural guidance. Existing files on disk serve as additional examples the agent can reference via disk tools.

### ADR-0012: ToolService(boolean withDefaults) Constructor
**Status:** Proposed  
**Context:** `new ToolService()` auto-registers WebFetchTool, SearchAgentTool, ShellTool, CompactSessionTool. The scaffold agent only wants WebFetchTool from those.  
**Decision:** Add a package-private `ToolService(boolean withDefaults)` constructor. `new ToolService(false)` gives an empty registry. `PeonAiService` creates `new ToolService(false)` for the scaffold agent, then adds only: config-scoped disk tools, SkillTool, WebFetchTool, ReloadConfigTool. The existing no-arg constructor delegates to `this(true)`.  
**Consequences:** Small, backward-compatible API change. Prevents future default-tool leakage into scaffold agent.

### ADR-0013: Persistent Agents in AgentService
**Status:** Proposed  
**Context:** `AgentService.clearAgents()` clears the map and re-adds only `planAgent` + `devAgent`. The scaffold agent is added externally via `addAgent()` but would be dropped on every `reloadAgents()` call.  
**Decision:** Add `addPersistentAgent(AiAgent agent)` to `AgentService`. Persistent agents are stored in a separate `persistentAgents` map. `clearAgents()` re-adds both the built-in agents (dev/plan) AND all persistent agents. `getAgents()` includes both maps. `reloadAgentConfig()` preserves persistent agents across reloads.  
**Consequences:** AgentService gains a second map + one method. Clean separation between built-in agents (managed by constructor) and externally-added persistent agents (managed by PeonAiService).

## Affected Files

### New Files
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/scaffold/AiScaffoldAgent.java` — built-in agent class with own ToolService
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/scaffold/ReloadConfigTool.java` — reload tool
- `/llmpeon-core/src/main/resources/org/sterl/llmpeon/prompts/scaffold-tutorial.txt` — tutorial content (shown on first activation in session)
- `/llmpeon-core/src/main/resources/org/sterl/llmpeon/prompts/agent-template.txt` — agent template for creating artifacts (loaded into system prompt via `PromptLoader.withDefault()`)
- `/llmpeon-core/src/test/java/org/sterl/llmpeon/scaffold/AiScaffoldAgentTest.java` — unit tests
- `/llmpeon-parent/docs/scaffold-agent.md` — this story doc
- `/homepage/src/setup/scaffold-agent.md` — user-facing docs

### Modified Files
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/agent/AiAgent.java` — add `getToolService()` method (default returns null or shared; AbstractAgent overrides to return its field)
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/agent/AbstractAgent.java` — implement `getToolService()` returning the `toolService` field
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/AgentService.java` — add `persistentAgents` map; `addPersistentAgent()` method; `clearAgents()` re-adds persistent agents; `getAgents()` includes persistent agents; `reloadAgentConfig()` preserves persistent agents
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/tool/ToolService.java` — add `ToolService(boolean withDefaults)` constructor; no-arg delegates to `this(true)`
- `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonAiService.java` — rename `toolService` to `sharedToolService`; add `getSharedToolService()`; `getToolService()` routes to active agent; create scaffold agent with own ToolService(false) after all services exist; add as persistent agent; setActiveAgent hook for standing orders + tutorial
- `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java` — use `aiService.getSharedToolService()` for AskUserTool and ShellTool wiring (these target the shared service used by dev/plan/custom agents)

## Rules & Constraints

- **Package naming:** `org.sterl.llmpeon.scaffold` across core module
- **Scaffold ToolService:** Created with `new ToolService(false)`, then populated with exactly: config-scoped DiskFileReadTool, DiskFileWriteTool, DiskGrepTool, SkillTool, WebFetchTool, ReloadConfigTool. No other tools.
- **No tool filter needed:** `AiScaffoldAgent.getToolFilter()` returns `p -> true` — the ToolService contents ARE the filter.
- **No Eclipse tools:** Workspace read/write/grep/build/test tools are never in the scaffold's ToolService.
- **No MCP for scaffold:** MCP tools are connected to the shared ToolService only. Scaffold agent has no MCP access (intentional).
- **Disk tools when globally disabled:** Config-scoped disk tools in the scaffold's ToolService are independent of `isDiskToolsEnabled()`. Global setting only affects the shared ToolService (dev/plan/custom agents).
- **AiAgent.getToolService():** Default method on interface returns `null`. `AbstractAgent` overrides to return its `toolService` field. Used by `PeonAiService.getToolService()` and `getToolStatus()`.
- **PeonAiService.getSharedToolService():** New accessor for `AIChatView` wiring (AskUserTool, ShellTool). These tools affect dev/plan/custom agents, not scaffold.
- **Persistent agents:** `AgentService.addPersistentAgent()` stores agents that survive `clearAgents()`. Separate from built-in dev/plan agents (managed by constructor fields).
- **Tutorial file:** Plain .txt loaded via PromptLoader.load("scaffold-tutorial.txt") — same pattern as developer.txt/planner.txt
- **Thread safety:** ReloadConfigTool calls are synchronous; service refreshes happen on tool execution thread. Standing orders + tutorial injection happens in UI thread (setActiveAgent is called from onAgentChange in AIChatView).

## Open Questions

1. ~~Should the tutorial message be shown every activation or only once per session?~~ → **Resolved**: Show when `agent.getMemory().size() == 0` (first activation in a session). Same pattern as `preloadPlanIfNeeded()` in PeonAiService.
2. ~~Should AiScaffoldAgent have a handover to AiDevAgent after creating artifacts?~~ → **Resolved**: No handover — scaffold is self-contained. Future feature: handover FROM other agents TO scaffold (for tuning prompts etc).
3. ~~Should we add validation when creating AGENT.md/SKILL.md?~~ → **Resolved**: No code validation. Provide `agent-template.txt` as a template loaded into the system prompt via `PromptLoader.withDefault()`, guiding the agent on structure, frontmatter, and file placement.
4. ~~How to handle disk tool name collision in shared ToolService?~~ → **Resolved**: Scaffold agent gets its own ToolService — no collision, no swap logic.
5. ~~How to handle ToolService default tools (Shell, Compact, SearchAgent) for scaffold?~~ → **Resolved**: `ToolService(boolean withDefaults)` constructor — `new ToolService(false)` gives empty registry.
6. ~~How does scaffold agent survive AgentService.clearAgents()?~~ → **Resolved**: `addPersistentAgent()` method on AgentService with separate `persistentAgents` map preserved across reloads.

# Scaffold Agent — Design

## Goal

Provide a built-in agent that lets users create, edit, and manage Peon configuration artifacts (agents, skills, commands) through natural language conversation. The agent has direct disk access to the `.peon` config directory and can reload changes immediately.

## Business Rules

### R1: Agent Registration ❌
The scaffold agent is a built-in agent like AiDevAgent/AiPlanAgent, appearing **last** in the agent dropdown after all custom agents.

- Name: `Peon-Scaffold`
- Package: `org.sterl.llmpeon.scaffold` (core module)
- Class: `AiScaffoldAgent extends AbstractAgent`

### R2: Tool Access ❌
The scaffold agent gets access to:
- Disk tools scoped to the config directory (`~/.peon`) — read, write, grep, search files
- Web fetch tool (for fetching remote skills/templates)
- A dedicated `reloadConfig` tool that refreshes AgentService, SkillService, and CommandService

All other tools (Eclipse workspace tools, shell, compactSession, etc.) are **filtered out**.

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

## BDD Use Cases

```
GIVEN the user switches to "Peon-Scaffold" in the agent dropdown
WHEN the agent is activated for the first time in a session (memory.size == 0)
THEN a tutorial message appears in chat history explaining capabilities
AND standing orders are injected with config dir path and directory listing of .peon root
AND disk tools are available scoped to ~/.peon
AND reloadConfig tool is available
AND skillRead, skillList, skillReadFile tools are available

GIVEN the scaffold agent is already active with chat history
WHEN the user switches to another agent and back to Peon-Scaffold
THEN no tutorial message is shown (memory.size > 0)
AND standing orders are injected with config dir path and directory listing

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

GIVEN the config directory doesn't exist yet or is empty
WHEN the scaffold agent is activated
THEN the standing orders show the directory listing (empty or with missing subdirs)
AND the agent reports which subdirectories are missing and offers to create them

GIVEN the user asks to fetch a remote skill template
WHEN the agent calls webFetchAsMarkdown with a URL
THEN it can write the fetched content to ~/.peon/skills/<name>/SKILL.md

GIVEN disk tools are disabled in the global config
WHEN the user switches to the Peon-Scaffold agent
THEN the agent still has access to disk tools scoped to ~/.peon
AND the disk tools appear in the available tool list

GIVEN the scaffold agent is active and the user wants to create a skill
WHEN the user asks "Create a new skill called code-review based on the writing-skill"
THEN the agent calls skillRead("writing-skill") to learn the pattern
AND creates ~/.peon/skills/code-review/SKILL.md following the same structure
AND calls reloadConfig() to make it immediately available
```

## Technical Design (ADRs)

### ADR-0007: Scaffold Agent as Built-in Java Class
**Status:** Proposed  
**Context:** Custom YAML agents cannot have programmatic tools injected or special standing orders.  
**Decision:** AiScaffoldAgent is a built-in Java class in core module, registered alongside AiDevAgent/AiPlanAgent in AgentService constructor when `withDefaultAgent=true`.  
**Consequences:** Gets full access to services via constructor injection; tool filtering overrides getToolFilter(); appears last in UI dropdown.

### ADR-0008: Reuse Existing Disk Tools with WorkingDir Swap
**Status:** Proposed  
**Context:** Existing disk tools are scoped to workspace project directory and conditionally enabled. Scaffold agent needs config-dir-scoped access regardless of global disk tools setting.  
**Decision:** Reuse the existing disk tools — don't create duplicates. Always keep them registered in `toolService` (don't remove when globally disabled). On scaffold agent activation, swap `workingDir` on the existing DiskFileReadTool, DiskFileWriteTool, DiskGrepTool to `config.getConfigDir()`. On deactivation, restore the original working directory. If global disk tools are disabled, the disk tools are still registered but **not visible to other agents** — each non-scaffold agent's tool filter excludes them. AiScaffoldAgent's tool filter allows only:
- Disk tools (reused, config-scoped): diskReadFile, diskWriteFile, diskDeleteFile, diskReplaceLines, diskEditFile, diskRenameResource, diskInsertLines, diskSearchFiles, diskListDirectory, diskGrepFiles
- Skill tools: skillRead, skillList, skillReadFile
- Web fetch: webFetchAsMarkdown
- Reload: reloadConfig
All other tools (Eclipse workspace tools, shell, compactSession, etc.) are **filtered out**.
**Consequences:** No duplicate tools in the registry. WorkingDir swap is simple and safe — only one set of disk tools. The standing orders include a `diskListDirectory()` output from the config root so the agent sees its scope immediately. Requires adding a disk tool exclusion filter to non-scaffold agents when global disk tools are disabled.

### ADR-0009: ReloadTool as Dedicated Service Tool
**Status:** Proposed  
**Context:** The scaffold agent needs to trigger service refreshes after creating/editing artifacts.  
**Decision:** New `ReloadConfigTool` class in `org.sterl.llmpeon.scaffold` package, injected with references to AgentService, SkillService, CommandService, and LlmConfig. Single @Tool method `reloadConfig()` that calls refresh on all three.  
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

## Affected Files

### New Files
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/scaffold/AiScaffoldAgent.java` — built-in agent class
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/scaffold/ReloadConfigTool.java` — reload tool
- `/llmpeon-core/src/main/resources/org/sterl/llmpeon/prompts/scaffold-tutorial.txt` — tutorial content (shown on first activation in session)
- `/llmpeon-core/src/main/resources/org/sterl/llmpeon/prompts/agent-template.txt` — agent template for creating artifacts (loaded into system prompt via `PromptLoader.withDefault()`)
- `/llmpeon-core/src/test/java/org/sterl/llmpeon/scaffold/AiScaffoldAgentTest.java` — unit tests
- `/llmpeon-parent/docs/scaffold-agent.md` — this story doc
- `/homepage/src/setup/scaffold-agent.md` — user-facing docs

### Modified Files
- `/llmpeon-core/src/main/java/org/sterl/llmpeon/AgentService.java` — register AiScaffoldAgent when withDefaultAgent=true; add to agents map after custom agents (last in list)
- `/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonAiService.java` — swap disk tools workingDir on scaffold activation/deactivation; register ReloadConfigTool after agentService creation; ensure disk tools are always registered (even when globally disabled) for scaffold access; setActiveAgent hook for standing orders + tutorial

## Rules & Constraints

- **Package naming:** `org.sterl.llmpeon.scaffold` across core module
- **Tool filtering:** AiScaffoldAgent overrides getToolFilter() to allow only: diskReadFile, diskWriteFile, diskDeleteFile, diskReplaceLines, diskEditFile, diskRenameResource, diskInsertLines, diskSearchFiles, diskListDirectory, diskGrepFiles, skillRead, skillList, skillReadFile, webFetchAsMarkdown, reloadConfig
- **No Eclipse tools:** Workspace read/write/grep/build/test tools are explicitly excluded
- **Working directory:** Existing disk tools have their `workingDir` swapped to `config.getConfigDir()` on scaffold activation, restored on deactivation
- **Disk tools always available:** Even when globally disabled in config, disk tools are registered for scaffold agent access — they are simply not visible to other agents via their tool filters
- **Tutorial file:** Plain .txt loaded via PromptLoader.load("scaffold-tutorial.txt") — same pattern as developer.txt/planner.txt
- **Thread safety:** ReloadConfigTool calls are synchronous; service refreshes happen on tool execution thread

## Open Questions

1. ~~Should the tutorial message be shown every activation or only once per session?~~ → **Resolved**: Show when `agent.getMemory().size() == 0` (first activation in a session). Same pattern as `preloadPlanIfNeeded()` in PeonAiService.
2. ~~Should AiScaffoldAgent have a handover to AiDevAgent after creating artifacts?~~ → **Resolved**: No handover — scaffold is self-contained. Future feature: handover FROM other agents TO scaffold (for tuning prompts etc).
3. ~~Should we add validation when creating AGENT.md/SKILL.md?~~ → **Resolved**: No code validation. Provide `agent-template.txt` as a template loaded into the system prompt via `PromptLoader.withDefault()`, guiding the agent on structure, frontmatter, and file placement.

# Custom Agents — Design

## Overview

Beyond the built-in agents (`AiDevAgent`, `AiPlanAgent`, `AiScaffoldAgent`) users can define
their own agents. Each agent is a directory with an `AGENT.md` (frontmatter + markdown body)
under the configured agents directory (default `~/.peon/agents`). Reuses Peon's existing
discovery, prompt-parser and read-only-tool building blocks and adds prefix/wildcard tool
filtering.

## AGENT.md format

```markdown
---
name: Docs-Assistant          # optional, defaults to directory name
description: ...              # optional
read-only: true               # optional, default false (also accepts read_only / readOnly)
include-default: true         # optional; false = no built-in system prompt
temperature: 0.8              # optional; override for this agent only
handover: some-agent          # optional; shows "Handoff → [agent]" button after work done
model: qwen3.6-27b            # optional model override
tools:                        # optional; absent = all tools
  - grep
  - read_
  - mcp__docs__search
---
<markdown body = system prompt, appended to the shared default prompt>
```

- `tools` accepts a YAML block list or inline CSV (`tools: grep, read_`).
- Absent `tools` ⇒ `null` ⇒ **all** tools; empty list ⇒ nothing.
- Keys are lower-cased during parse. `PromptYmlParser.getValue()` falls back across
  hyphen/underscore variants: `read-only` ↔ `read_only` ↔ `readonly` all resolve to the same key.

## Components (core module)

- `prompt/model/SimplePromptFile` — base class for AGENT.md parsing; keys lowercased during parse.
- `agent/CustomAgent` extends `AbstractAgent` — direct implementation (no separate service wrapper).
  - system prompt = `PromptLoader.withDefault(agentFile.readBody())`.
  - `getToolFilter()` = allowlist match **and** (`!readOnly || !isEditTool`) for built-in tools.
  - `getToolNameFilter()` = allowlist match for MCP tools.
  - `getAgentModelName()` = `agentFile.model` or `null` (fall back to default).
  - `setAgentModelName()` writes the model back into the `AGENT.md` frontmatter.
- `agent/AgentService` — mirrors `SkillService`: scans the directory for subfolders containing
  `AGENT.md`, keyed by lower-case name. Returns `CustomAgent` instances. Reloaded on config change.
  Built-in agents (Dev, Plan, Scaffold) are registered as persistent agents that survive reloads.
- `tool/ToolPolicy.enables(allowlist, name)` — `*` / prefix / exact match. Empty/null ⇒ false.
- `prompt/PromptYmlParser` — `parseFrontmatter` returns `Map<String,List<String>>` with block-list
  + inline-CSV support; `setFrontmatterValue` writer; `toolAllowlist` / `firstOrDefault` helpers.

## Tool filtering

The tool loop already filtered built-in tools by `Predicate<SmartToolExecutor>`. MCP tool specs
were appended wholesale. Added `ToolLoopRequest.toolNameFilter` (`Predicate<String>`, default all)
applied to MCP specs in `ToolService.toolSpecifications`, wired from
`AbstractAgent.getToolNameFilter()`. This lets a custom agent's allowlist govern MCP tools
too. `read-only` cannot see inside MCP tools, so read-only-ness for MCP is expressed by allowlisting
only the read tool names.

Filters stay constant within a tool loop (the `SimplePromptFile` snapshot is only swapped on config
refresh) to preserve the KV cache.

UI introspection reuses the very same filters: `AiAgent.isToolActive(SmartToolExecutor)` delegates
to `getToolFilter()` and `isMcpToolActive(String)` to `getToolNameFilter()`. The tool activity popup
(`PeonAiService.getToolStatus` → header bar) is built from these, so it shows exactly what the agent
sends — including a read-only agent's edit tools appearing as inactive. The name-only
`ToolPolicy.enables` check is a `private` helper on `CustomAgent`; it is deliberately **not** on the
`AiAgent` interface, since on its own it ignores the read-only rule.

## UI & wiring

- `ActionsBarWidget` builds the agent combo from `AgentService.getAgents()` (built-in + custom,
  sorted by name). Selecting an agent fires `onAgentChange` → `PeonAiService.setActiveAgent()`.
  `setAgents(...)` rebuilds the combo preserving the current selection by label.
- `PeonAiService` holds the `AgentService` which manages all agents. `getActiveAgent()` returns the
  currently selected one; `setActiveAgent()` switches. `updateConfig` refreshes agent definitions.
- `setModel` branches by agent type: `AiDevAgent`/`AiScaffoldAgent` ⇒ global model preference;
  `AiPlanAgent` ⇒ plan-model preference; `CustomAgent` ⇒ writes `model:` back to the `AGENT.md`
  via `SimplePromptFile.setValue()`.

## Config reload callback

`ReloadConfigTool.reloadConfig()` orchestrates refresh of all three services (agents, skills,
commands). The UI callback (`onReload`) is injected via constructor, held as a `final` field, and
fired **after** all three services succeed — not mid-way during agent reload.

`ReloadConfigTool.reloadConfig()` → `agentService.reloadAgents()` → `skillService.refresh()` →
`commandService.refresh()` → `onReload.run()` → `PeonAiService` → `AIChatView.refreshAgentUI()`.

`AgentService.reloadAgents()` takes **no** callback parameter — it is pure business logic
(reload + return boolean). The callback lives only in `ReloadConfigTool`.

::: tip Observer migration point
This uses a simple `Runnable` callback wired through `ReloadConfigTool` → `PeonAiService` →
`AIChatView`. If more than one consumer needs to react to config reload events (e.g. status bar,
model list), replace with an Observer pattern: services fire `ReloadEvent` on success/failure;
interested parties subscribe via listener registration in their lifecycle.
:::

## Config

- Preference `llm.configDirectory`, default `~/.peon`. Agents live in `<configDir>/agents`.
- `LlmConfig.AGENT_DIRECTORY` = `"agents"`. Path resolved via `config.getConfigDir().resolve(AGENT_DIRECTORY)`.
- No Claude-compat fallback for agents (only skills/commands have that).

## Model dropdown (related fixes)

- **B1** — model list empty/failed: show the agent's configured model instead of an empty combo
  (`AIChatView.showConfiguredModelFallback`).
- **B2** — configured model not in the fetched list: adopt the first list entry.
- **B3** — model change persisted for the active agent (per-agent: Dev/Scaffold ⇒ global pref,
  Plan ⇒ plan-model pref, Custom ⇒ YAML `model:` field).

## Agent model save asymmetry (Bug 6 fix)

Each agent is responsible for its own model persistence:

| Agent | `getAgentModelName()` | `setAgentModelName()` | Pref written |
|-------|----------------------|----------------------|--------------|
| `AiDevAgent` | `config.getModel()` (global) | `configuredModel.withModel()` | `PREF_MODEL` |
| `AiPlanAgent` | `config.getPlanModel()` | updates `planModel` in config | `PREF_PLAN_MODEL` |
| `AiScaffoldAgent` | `null` (inherits default) | no-op (returns `false`) | `PREF_MODEL` (via `saveModel`) |
| `CustomAgent` | `agentFile.model` | writes back to `AGENT.md` | none (file-based) |

`AbstractAgent` does **not** override `getAgentModelName()`/`setAgentModelName()` — the interface
defaults (`null` / `false`) apply to any agent that doesn't explicitly override.

# Custom Agents — Design

## Overview

Beyond the three built-in modes (`PeonMode` PLAN/DEV/AGENT) users can define their own agents.
Each agent is a directory with an `AGENT.md` (frontmatter + markdown body) under a configured
directory (default `~/.peon/agents`, with `~/.claude/agents` supported for Claude compatibility).
Ported from the ai-schulung project; reuses Peon's existing discovery, prompt-parser and
read-only-tool building blocks and adds prefix/wildcard tool filtering.

## AGENT.md format

```markdown
---
name: Docs-Assistant          # optional, defaults to directory name
description: ...              # optional
readOnly: true               # optional, default false (also accepts read-only)
include-default: true        # optional; false = no built-in system prompt
temperature: 0.8             # optional; override for this agent only
handover: some-agent         # optional; shows "Give [agent]" button after work done
model: qwen3.6-27b           # optional model override
tools:                       # optional; absent = all tools
  - grep
  - read_
  - mcp__docs__search
---
<markdown body = system prompt, appended to the shared default prompt>
```

- `tools` accepts a YAML block list or inline CSV (`tools: grep, read_`).
- Absent `tools` ⇒ `null` ⇒ **all** tools; empty list ⇒ nothing.
- Keys are lower-cased during parse. Hyphen variants accepted: `read-only` (canonical) and `readonly` resolve to the same key via fallback in `PromptYmlParser.getValue()`.

## Components (core module)

- `prompt/model/SimplePromptFile` — base class for AGENT.md parsing; keys lowercased during parse.
- `agent/CustomAgent` extends `AbstractAgent` — direct implementation (no separate service wrapper).
- `agent/AgentService` — mirrors `SkillService`: scans the directory for subfolders containing
  `AGENT.md`, keyed by lower-case name. Returns `CustomAgent` instances. Reloaded on config change.
- `tool/ToolPolicy.enables(allowlist, name)` — `*` / prefix / exact match. Empty/null ⇒ false.
- `shared/PromptYmlParser` — extended: `parseFrontmatter` now returns `Map<String,List<String>>`
  with block-list + inline-CSV support; new `setFrontmatterValue(path, key, value)` writer;
  `toolAllowlist` / `firstOrDefault` helpers. (`parseAgent` lives in `AgentPromptFile` to keep
  `shared` free of an `agent` dependency.)
- `CustomAgentService` — extends `AbstractChatService`:
  - system prompt = `PromptLoader.withDefault(agentFile.readBody())`.
  - `getToolFilter()` = allowlist match **and** (`!readOnly || !isEditTool`) for built-in tools.
  - `getToolNameFilter()` = allowlist match for MCP tools.
  - `getAgentModelName()` = `agentFile.model` or the configured model.
  - `setModelName()` pins the model onto the agent snapshot (not the shared config).

## Tool filtering

The tool loop already filtered built-in tools by `Predicate<SmartToolExecutor>`. MCP tool specs
were appended wholesale. Added `ToolLoopRequest.toolNameFilter` (`Predicate<String>`, default all)
applied to MCP specs in `ToolService.toolSpecifications`, wired from
`AbstractChatService.getToolNameFilter()`. This lets a custom agent's allowlist govern MCP tools
too. `readOnly` cannot see inside MCP tools, so read-only-ness for MCP is expressed by allowlisting
only the read tool names.

Filters stay constant within a tool loop (the `AgentPromptFile` snapshot is only swapped on config
refresh) to preserve the KV cache.

## UI & wiring

- `ActionsBarWidget` builds the agent combo from `PeonMode` labels **plus** custom-agent names.
  Selecting index `< PeonMode.values().length` fires the mode callback; higher indices fire a new
  `Consumer<AgentPromptFile>` callback. `setCustomAgents(...)` rebuilds the combo preserving the
  selection.
- `PeonAiService` holds a per-agent `CustomAgentService` cache (own memory each) and a
  `volatile activeCustomAgent`. `getActiveService()` returns it when set; `setPeonMode` clears it;
  `setActiveCustomAgent(name)` selects it. `updateConfig` refreshes definitions and syncs cached
  snapshots (dropping removed agents).
- `setModel` branches: built-in ⇒ per-mode preference; custom agent ⇒ `AgentPromptFile.model` +
  write `model:` back to the `AGENT.md` via `PromptYmlParser.setFrontmatterValue`.

## Config

- Preference `llm.agentDirectory`, `LlmConfig.agentDirectory`, default resolved by
  `LlmPreferenceInitializer.resolveDefaultDir("agents")` (native `~/.peon`, Claude-compat
  `~/.claude`). The `.aipeon` legacy name was retired in favour of `.peon`.
- Directory editable in the Peon AI preferences page.

## Model dropdown (related fixes)

- **B1** — model list empty/failed: show the agent's configured model instead of an empty combo
  (`AIChatView.showConfiguredModelFallback`).
- **B2** — configured model not in the fetched list: adopt the first list entry.
- **B3** — model change persisted for the active agent (per-mode pref, or the custom agent's YAML).

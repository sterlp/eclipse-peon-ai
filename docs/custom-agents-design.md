# Custom Agents — Design

## Overview

Beyond the two built-in agents (`AiPlanAgent` = **Peon-Plan**, `AiDevAgent` = **Peon-Dev**) users
can define their own agents. Each agent is a directory with an `AGENT.md` (frontmatter + markdown
body) under the `agent` subfolder of the config directory (default `~/.peon/agent`). Ported from
the ai-schulung project; reuses Peon's discovery, prompt-parser and read-only-tool building blocks
and adds prefix/wildcard tool filtering.

A custom agent (`CustomAgent`) and the built-ins share one `AgentService` and one dropdown, so they
are interchangeable at the call site (`AiAgent` interface).

## AGENT.md format

```markdown
---
name: Docs-Assistant          # optional, defaults to directory name
description: ...              # optional
read-only: true              # optional, default false (also accepts readOnly)
include-default: false       # optional, default false; true = prepend the shared default prompt
temperature: 0.8             # optional; override for this agent only
handover: Peon-Dev           # optional; shows "Handoff → [agent]" button after work done
model: qwen3.6-27b           # optional model override
tools:                       # optional; ABSENT = no tools, use '*' for all
  - eclipseReadFile
  - eclipseGrepFiles
  - mcp__docs__search
---
<markdown body = system prompt>
```

- System prompt = the markdown body **only**, unless `include-default: true`, which prepends the
  shared default prompt (`PromptLoader.withDefault(body)`). See `CustomAgent.getSystemPrompt()`.
- `tools` is parsed as a YAML **block list** (one `- entry` per line). Absent `tools` ⇒ `null` ⇒
  **no** tools (`ToolPolicy.enables(null, …)` returns `false`); use `- '*'` to allow all.
- Keys are lower-cased during parse. Hyphen variants resolve to the same key via the fallback in
  `PromptYmlParser.getValue()` (`read-only`, `readonly`, `readOnly` all work).

## Components (core module)

- `prompt/model/SimplePromptFile` — parsed `AGENT.md`: `frontmatter` (`Map<String,List<String>>`) +
  `body`. Accessors `firstOrDefault`, `get`, `isTrue`; writers `setValue`/`set` + `save()` re-render
  the file (used to persist a model change back into the frontmatter).
- `prompt/PromptYmlParser` — reads the leading `---` frontmatter (block list + scalar), lower-cases
  keys, derives `name` from the directory when absent. `toolAllowlist(...)` splits inline CSV, but it
  is a standalone helper (**not** wired into `CustomAgent.getTools()`).
- `agent/AiAgent` — common interface for built-ins and custom agents (`getTools()`, `isReadOnly()`,
  `handoverTo()`, `getAgentModelName()`/`setAgentModelName()`, `allowed(name)`).
- `agent/AbstractAgent` — shared call loop, memory, static-context handling, KV-cache-safe filters.
- `agent/CustomAgent extends AbstractAgent` — backed by a replaceable `SimplePromptFile` snapshot.
- `AgentService` (in `org.sterl.llmpeon`) — mirrors `SkillService`: scans the directory for
  subfolders containing `AGENT.md`, keyed by name; also holds the built-in `AiPlanAgent`/`AiDevAgent`
  when constructed `withDefaultAgent`. Tracks the `activeAgent`. Reloaded on config change.
- `tool/ToolPolicy.enables(allowlist, name)` — `*` / prefix / exact match. Empty/null ⇒ `false`.

## Tool filtering

Built-in tools are filtered by `Predicate<SmartToolExecutor>` (`getToolFilter()`); MCP tool specs are
filtered by `Predicate<String>` (`getToolNameFilter()`, wired into `ToolLoopRequest.toolNameFilter`
and applied to MCP specs in `ToolService`). `CustomAgent` implements both from its allowlist:

- `getToolFilter()` = allowlist match **and** (`!isReadOnly() || !isEditTool()`).
- `getToolNameFilter()` = allowlist match.

`read-only` cannot see inside MCP tools, so read-only-ness for MCP is expressed by allowlisting only
the read tool names. Filters stay constant within a tool loop (the `SimplePromptFile` snapshot is only
swapped on config refresh) to preserve the KV cache.

## UI & wiring

- `ActionsBarWidget` builds one agent combo from `AgentService.getAgents()` (built-ins + custom).
  Selecting an agent fires `Consumer<AiAgent>`; the **Handoff → [agent]** button appears when the
  selected agent's `handoverTo()` is non-null.
- `PeonAiService.setActiveAgent(...)` switches the active agent and preloads the saved plan if one
  exists (`preloadPlanIfNeeded()`). `getToolStatus()` powers the 🔨 tool popup (active tools per agent).
- `setModel` branches: a built-in saves to its per-agent preference; a custom agent writes `model:`
  back into its `AGENT.md` via `SimplePromptFile.save()`.

## Config

- Single **Config directory** preference (`PREF_CONFIG_DIRECTORY` = `llm.configDirectory`), default
  `~/.peon` (`LlmPreferenceInitializer.PEON_HOME`). Skills, commands and agents live in the
  `skill` / `command` / `agent` subfolders (`LlmConfig.SKILL_DIRECTORY` / `COMMAND_DIRECTORY` /
  `AGENT_DIRECTORY`, all singular).
- Editable on the **AI Peon > Peon Configuration** preferences page.

## Known gaps (code cleanup, not yet fixed)

- Absent `tools` yields **no** tools, but the `CustomAgent` Javadoc and the `AgentServiceTest`
  case name `absentToolsMeansAllTools` say "all" — the test only asserts `getTools() == null`, not
  activation. Documented behaviour: absent = none.
- Inline-CSV `tools: a, b` is **not** split by `CustomAgent.getTools()` (only the standalone
  `PromptYmlParser.toolAllowlist` splits it), so only the block-list form works for agents.
- `PromptYmlParser.stripYamlValue` does not strip trailing `# ...` comments, so a comment on a
  frontmatter line becomes part of the value. **By design / documented limitation** — comments in
  frontmatter values are unsupported (see the warning in `custom-agents.md`). The markdown body is
  never passed through `stripYamlValue`, so `#` headings in the body are unaffected. Locked in by
  `PromptYmlParserTest#stripYamlValue_keepsTrailingCommentLiterally`.

## Model dropdown (related fixes)

- **B1** — model list empty/failed: show the agent's configured model instead of an empty combo.
- **B2** — configured model not in the fetched list: adopt the first list entry.
- **B3** — model change persisted for the active agent (per-mode pref, or the custom agent's YAML).

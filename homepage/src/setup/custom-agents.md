---
title: Custom Agents
description: Define your own agents with a system prompt and a tool allowlist
---

# Custom Agents (since v2.0.0)

Next to the two built-in agents (**Peon-Plan**, **Peon-Dev**) you can define
your own agents. Each agent has its own system prompt and its own set of allowed tools, and it
shows up in the same agent dropdown right next to the built-ins.

## Where they live

Each agent is a directory with an `AGENT.md` file, living in the `agent` subfolder of your
config directory (default `~/.peon/agents`). Change the base directory via
**Window > Preferences > AI Peon > Peon Configuration** → *Config directory*.

```
~/.peon/agents/
├── sap-coder/
│   └── AGENT.md
└── reviewer/
    └── AGENT.md
```

The directory name is the default agent name (overridable with the `name:` frontmatter field).

::: tip
Changes to the `.peon` config require a restart of eclipse to be picked up.
:::

## The AGENT.md file

Frontmatter (all fields optional) followed by the markdown body, which becomes the agent's
system prompt:

```markdown
---
name: Docs-Assistant
description: Answers only from the retrieved documents
read-only: true
model: qwen3.6-27b
tools:
  - eclipseReadFile
  - eclipseGrepFiles
  - mcp__docs__search
---
You are the Docs-Assistant. Your only source of knowledge are the documents you find
with your tools. Never answer from prior knowledge.
```

::: warning No `#` comments in the frontmatter
The frontmatter parser does **not** strip trailing `# ...` comments. A line like
`model: qwen3.6-27b   # my model` is read as the literal value `qwen3.6-27b   # my model`
and breaks. Keep frontmatter values comment-free.
:::

| Field | Meaning |
|-------|---------|
| `name` | Display name in the dropdown. Defaults to the directory name. |
| `description` | Short summary. |
| `read-only` | `true` = only non-editing tools are offered (no file writes, no shell). `readOnly` is also accepted. Default: `false`. |
| `include-default` | `true` = prepend the shared built-in system prompt to this agent's body. Default: `false` (body only). |
| `temperature` | Override temperature for this agent. Float 0.0–2.0. |
| `handover` | Agent name to hand off to after work is done. Shows a **Handoff → [name]** button when set. Enables workflow chains (e.g. plan → dev → review). |
| `model` | Optional model override. Changing the model in the UI while this agent is active writes it back here. |
| `think_supported` | `true`/`false` — declares that this agent supports thinking. The chat brain button provides the runtime toggle. |
| `think_on_string` | Value sent when enabled. A level `high`/`medium`/`low`/`minimal` (OpenAI), `true` (Ollama/Anthropic), etc. **Empty → auto** ([built-in model mapping](./advanced-configuration.md#built-in-model-mapping)). Setting it (or `think_off_string`) switches the mapping off. |
| `think_off_string` | Value sent when disabled. **Empty → send nothing.** Set e.g. `false` for Ollama to force `think:false`. |
| `think_send` | *(reserved)* Show the model's reasoning and resend it next turn (Qwen, Mistral, DeepSeek). Currently the global **Send thinking back to model** setting applies to all agents; this per-agent key is parsed but not yet wired per request. |
| `think` | *(legacy alias, auto-migrated)* Read as `think_on_string` and implies `think_supported` for on-values. Old files are auto-migrated on first load. Prefer the `think_*` keys above. |
| `tools` | Allowlist of tool-name prefixes. **Omit it and the agent gets _no_ tools** — use `- '*'` to allow all. |

## Workflow Handoff

A custom agent with a `handover:` value shows a **Handoff → [Agent Name]** button next to the
input. Once the agent's work is done you click it and control transfers to the named agent. As
context it passes the saved plan (`peon-plan/overview.md`) if one exists, otherwise the agent's
last AI message — prefixed with `Handover from [previous agent]`.

This enables multi-agent workflows without autonomous mode — for example:
```
planner/AGENT.md      →  handover: Peon-Dev
dev-reviewer/AGENT.md →  handover: planner
```

The receiving agent starts a fresh conversation seeded with that handover message, so it picks
up where the previous one left off with minimal context transfer.

::: tip Same mechanism as the built-ins
The built-in **Peon-Plan** agent uses exactly this: it hands over to **Peon-Dev**.
:::

## Tool allowlist

`tools` is an allowlist of tool-name **prefixes**:

- `'*'` — allow every tool.
- a **prefix** — `eclipseRead` enables `eclipseReadFile`, `eclipseReadProjectProblems`, …; a full
  name enables exactly that tool. Works for built-in **and** MCP tools (e.g. `mcp__docs__search`).
- **field omitted** — **no tools** (use `- '*'` if you want all of them).

Use the YAML block-list form (one `- entry` per line), as in the example above.

`read-only` and `tools` combine: a read-only agent that allowlists a write tool still won't get
it, because editing tools are filtered out first. For MCP tools, restrict writes by only
allowlisting the read-only tool names.

## Selecting an agent

Pick your agent from the dropdown below the input, just like Peon-Dev or Peon-Plan. Each agent
keeps its own conversation. Edits to an `AGENT.md` are picked up on the next config refresh.

## Finding the exact tool names

The authoritative, always-up-to-date list (including connected MCP tools) is behind the **🔨
button** at the top-right of the chat view — it shows every registered tool and whether it is
active for the selected agent.

Common built-in prefixes:

| Prefix | Tools |
|--------|-------|
| `eclipse` | Workspace file read/write/search/navigation, build, tests, console, project problems — the default toolset. E.g. `eclipseReadFile`, `eclipseWriteFile`, `eclipseGrepFiles`, `eclipseSearchFiles`, `eclipseBuildProject`, `eclipseRunTests`, `eclipseReadProjectProblems`, `eclipseFindReferences`. |
| `skill` | `skillList`, `skillRead`, `skillReadFile` |
| `memory` | `memoryAdd`, `memoryReplace`, `memoryRemove` |
| `plan` | `planRead`, `planSave`, `planUpdate`, `planImplemented` |
| `disk` | Optional file/grep tools that bypass the Eclipse workspace — only registered when **Enable disk tools** is on (see [Advanced Configuration](./advanced-configuration.md)). E.g. `diskReadFile`, `diskGrepFiles`, `diskWriteFile`. |
| `mcp__` | Every tool from a connected MCP server, e.g. `mcp__docs__search`. |

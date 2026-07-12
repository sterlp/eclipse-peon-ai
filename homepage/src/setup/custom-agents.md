---
title: Custom Agents
description: Define your own agents with a system prompt and a tool allowlist
---

# Custom Agents (since v2.0.0)

Next to the three built-in modes (**Peon-Plan**, **Peon-Dev**, **Peon-Agent**) you can define
your own agents. Each agent has its own system prompt and its own set of allowed tools, and it
shows up in the same agent dropdown right next to the built-ins.

## Where they live

Each agent is a directory with an `AGENT.md` file. The default location is `~/.peon/agents`
(the `~/.claude/agents` folder is also picked up for Claude compatibility). You can change the
directory in **Window > Preferences > Peon AI > Agents directory**.

```
~/.peon/agents/
├── sap-coder/
│   └── AGENT.md
└── reviewer/
    └── AGENT.md
```

The directory name is the default agent name.

## The AGENT.md file

Frontmatter (all fields optional) followed by the markdown body, which becomes the agent's
system prompt:

```markdown
---
name: Docs-Assistant
description: Answers only from the retrieved documents
readOnly: true
model: qwen3.6-27b        # optional — overrides the selected model for this agent
tools:
  - grep
  - read_
  - mcp__docs__search
---
You are the Docs-Assistant. Your only source of knowledge are the documents you find
with your tools. Never answer from prior knowledge.
```

| Field | Meaning |
|-------|---------|
| `name` | Display name in the dropdown. Defaults to the directory name. |
| `description` | Short summary. |
| `readOnly` | `true` = only non-editing tools are offered (no file writes, no shell). Hyphenated form: `read-only`. |
| `include-default` | `false` = use only this agent's prompt (no built-in system prompt). Default: `true`. |
| `temperature` | Override temperature for this agent. Float 0.0–2.0. |
| `handover` | Agent name to hand off to after work is done. Shows "Give [name]" button when set. Enables workflow chains (e.g., plan → dev → review). |
| `model` | Optional model override. Changing the model in the UI while this agent is active writes it back here. |
| `tools` | Allowlist of tool-name prefixes. **Leave it out to allow all tools.** |

## Workflow Handoff

A custom agent with `handover:` shows a **Give [Agent Name]** button in the status bar once its work is complete. Clicking it transfers control to another agent, passing the last AI message or saved plan as context.

This enables multi-agent workflows without autonomous mode — for example:
```
planner/AGENT.md  →  handover: peon-dev
dev-reviewer/AGENT.md  →  handover: planner   # cycle back if needed
```

The receiving agent picks up where the previous one left off with minimal context transfer.

## Tool allowlist

- `*` — allow every tool.
- a **prefix** — `document_` enables `document_read`, `document_write`, …; a full name enables
  exactly that tool. Works for built-in **and** MCP tools (e.g. `mcp__docs__search`).
- **field omitted** — all tools allowed (still limited by `readOnly`).
- empty list — no tools.

Inline form works too: `tools: grep, read_`.

`readOnly` and `tools` combine: a read-only agent with `tools: [grep, write_file]` still won't
get `write_file` because it edits. For MCP tools, restrict writes by only allowlisting the
read-only tool names.

## Selecting an agent

Pick your agent from the dropdown below the input, just like Peon-Dev or Peon-Plan. Each agent
keeps its own conversation. Edits to an `AGENT.md` are picked up on the next config refresh.

## Built-in Tool Names Reference

Use these names in your tool allowlist. Prefix matching works — `disk_` enables all disk tools:

| Tool Class | Method Name (allowlist prefix) | Category |
|------------|-------------------------------|----------|
| DiskFileReadTool | `diskReadFile`, `diskSearchFiles`, `diskListDirectory` | Read |
| DiskGrepTool | `diskGrepFiles` | Search |
| DiskFileWriteTool | `diskWriteFile`, `diskDeleteFile`, `diskReplaceLines`, `diskEditFile`, `diskRenameResource`, `diskInsertLines` | Write |
| WebFetchTool | `webFetchAsMarkdown` | External |
| ShellTool | `shellRunCommand`, `readOperationSystemInformation` | System |
| SearchAgentTool | `searchAgent` | Agent |
| CompactSessionTool | `compactSession` | Context |
| SkillTool | `skillRead`, `skillList`, `skillReadFile` | Skills |

For MCP tools, the prefix is `mcp__` (e.g. `mcp__docs__search`).

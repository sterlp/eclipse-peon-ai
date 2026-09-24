---
title: Custom Agents
description: Define your own agents with a system prompt and a tool allowlist
---

# Custom Agents (since v2.0.0)

Next to the four built-in agents (**Peon-PO**, **Peon-Dev**, **Peon-Plan**, **Peon-Scaffold**) you can define
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

::: warning Quoting `extra_body`
`extra_body` is a single-line JSON string. Wrap it in **single quotes** with double quotes inside — no escaping:

```
extra_body: '{"cache_control": {"type": "ephemeral"}}'
```

Double quotes on the outside would break, because the JSON itself contains double quotes.
:::

| Field | Meaning |
|-------|---------|
| `name` | Display name in the dropdown. Defaults to the directory name. |
| `description` | Short summary. |
| `read-only` | `true` = only non-editing tools are offered (no file writes, no shell). `readOnly` is also accepted. Default: `false`. |
| `include-default` | `true` = prepend the shared built-in system prompt to this agent's body. Default: `false` (body only). |
| `temperature` | Temperature for this agent. Empty or invalid = not sent; a top-level `temperature` in `extra_body` wins. |
| `handover` | Agent name to hand off to after work is done. Shows a **Handoff → [name]** button when set. Enables workflow chains (e.g. plan → dev → review). |
| `model` | Optional model override. Changing the model in the UI while this agent is active writes it back here. |
| `url` | Optional endpoint override for this agent (e.g. a different gateway or a local instance). Omitted/blank = inherits the base connection from Peon Configuration. |
| `api_key` | Optional API-key override for this agent. Omitted/blank = inherits the base key. |
| `extra_body` | Raw JSON merged into this agent's request body — where [prompt caching](./advanced-configuration.md#extra-body--prompt-caching) is configured per agent. Omitted/blank = none. |
| `think_supported` | `true`/`false` — declares that this agent's model supports thinking. |
| `think_on_string` | Value used when supported. A level `high`/`medium`/`low`/`minimal` (OpenAI), `true` (Ollama/Anthropic), etc. **Empty → auto** ([built-in model mapping](./advanced-configuration.md#built-in-model-mapping)). Setting it (or `think_off_string`) switches the mapping off. |
| `think_off_string` | Value used when unsupported. Empty means provider default, except Ollama sends `think:false`. Set `false` for providers that need explicit off. |
| `think_send` | *(reserved)* Show the model's reasoning and resend it next turn (Qwen, Mistral, DeepSeek). Currently the global **Show and resend model thinking** setting applies to all agents; this per-agent key is parsed but not yet wired per request. |
| `think` | *(legacy alias, auto-migrated)* Read as `think_on_string` and implies `think_supported` for on-values. Old files are auto-migrated on the first write operation (e.g. model or thinking-support change). Prefer the `think_*` keys above. |
| `tools` | Allowlist of tool-name prefixes. **Omit it and the agent gets _all_ tools**; an empty list allows none. |

## Model connection per agent

A custom agent can carry a full model connection in its frontmatter — the same record and the same
resolution as the four built-in agents:

```markdown
---
name: sap-coder
url: http://localhost:8080/v1
api_key: sk-...
extra_body: '{"prompt_cache_key": "llmpeon"}'
model: gpt-5
---
You are the sap-coder. ...
```

Omitted/blank fields inherit the base connection from Peon Configuration. The extra body is sent
per request for OpenAI-family providers and baked in at build time for Anthropic (see
[Extra Body / Prompt Caching](./advanced-configuration.md#extra-body--prompt-caching)).

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
- **field omitted** — **all tools** (an empty list allows none).

Use the YAML block-list form (one `- entry` per line) or inline CSV (`tools: grep, read_`).

`read-only` and `tools` combine: a read-only agent that allowlists a write tool still won't get
it, because editing tools are filtered out first. For MCP tools, restrict writes by only
allowlisting the read-only tool names.

## Selecting an agent

Pick your agent from the dropdown below the input, just like Peon-Dev or Peon-Plan. Each agent
keeps its own conversation. Edits to an `AGENT.md` are picked up on the next config refresh.

## Ordering the agent list in UI

Place an `agent-order.txt` file next to the agent directories to control dropdown order. Each line
is a Java regex matched against agent names, applied top-to-bottom. Agents matching an earlier line
appear first; within a group they are sorted alphabetically. Unmatched agents are appended
alphabetically at the end. Lines starting with `#` are comments; invalid regexes are skipped.

```
.*Manager.*
.*Worker.*
```

This puts all `...Manager...` agents first, then `...Worker...`, then everyone else. If the file is
absent it is auto-created with `^Peon-PO$` so **Peon-PO** appears first by default.

## Finding the exact tool names

The authoritative, always-up-to-date list (including connected MCP tools) is behind the **🔨
button** at the top-right of the chat view — it shows every registered tool and whether it is
active for the selected agent.

Common built-in prefixes:

| Prefix | Tools |
|--------|-------|
| `eclipse` | Workspace file read/write/search/navigation, build, tests, console, project problems — the default toolset. E.g. `eclipseReadFile`, `eclipseWriteFile`, `eclipseGrepFiles`, `eclipseSearchFiles`, `eclipseBuildProject`, `eclipseRunJavaTests`, `eclipseReadProjectProblems`, `eclipseFindReferences`. |
| `skill` | `skillList`, `skillRead`, `skillReadFile` |
| `memory` | `memoryAdd`, `memoryReplace`, `memoryRemove` |
| `plan` | `planRead`, `planSave`, `planUpdate`, `planImplemented` |
| `disk` | Optional file/grep tools that bypass the Eclipse workspace — only registered when **Enable disk tools** is on (see [Advanced Configuration](./advanced-configuration.md)). E.g. `diskReadFile`, `diskGrepFiles`, `diskWriteFile`. The same toggle also enables `webGet` (download a URL to a disk path — status, size and path in the context, never the content). |
| `debugJava` | Java debugger — 15 actions (e.g. `debugJavaGetState`, `debugJavaStepOver`, `debugJavaSetBreakpoint`, `debugJavaSuspend`). An **edit tool**, so only offered to non-read-only agents (in practice Peon-Dev). You start the debug session in the Debug view; breakpoint set/remove work without one (stored as markers). See [Java debugger](#java-debugger). |
| `web` | `webGet` (download a URL to a disk path) — only registered when **Enable disk tools** is on, like the `disk*` tools above. |
| `shell` | `shellRunCommand` (run a shell command — mvn, npm, git; not for file I/O). |
| `lintDocs`, `nextIds` | Docs tooling — `lintDocs` and `lintDocsAndTests` (the `lintDocs` prefix matches **both**) and `nextIds` (exact name, no shared prefix). A bare `docs` prefix matches nothing. |
| `mcp__` | Every tool from a connected MCP server, e.g. `mcp__docs__search`. |

::: tip Disk tools report absolute paths
The `disk*` file tools report the **absolute** path of the affected file in their success messages
(e.g. `Created file: /home/user/project/src/Foo.java`) — in sync with the `eclipse*` tools.
:::

## File tools

Copying and renaming are separate, byte-exact operations in both file families:

| Action | Eclipse family | Disk family |
|--------|----------------|-------------|
| Copy a file | `eclipseCopyFile` | `diskCopyFile` |
| Rename / move | `eclipseRenameResource` | `diskRenameResource` |

- **Copy** duplicates a file: it creates the target and any missing parent folders, and **keeps
  the original**. If the target already exists, the copy **fails** — no overwrite, so nothing is
  clobbered silently.
- **Rename** stays a separate, **atomic** move. Don't assemble a move from copy + delete — rename
  has no window where the file exists at both (or neither) path.

## Java debugger

The `debugJava*` family (15 actions) drives a live JDT debug session: inspect the stack, variables
and exceptions, evaluate expressions, set variables, step (`debugJavaStepOver` / `In` / `Out`),
suspend, resume, and manage line and exception breakpoints. Because these are **edit tools**, they
are offered only to non-read-only agents (in practice **Peon-Dev**), never to read-only ones.

- **The session is yours to start.** The agent never launches a debug session — you do, in the
  Eclipse **Debug view**. Session-bound actions (step, evaluate, inspect, …) fail honestly when no
  session is active.
- **Breakpoints work before a session.** `debugJavaSetBreakpoint`, `debugJavaSetExceptionBreakpoint`
  and `debugJavaRemoveBreakpoint` act on JDT markers directly, so they succeed even with no session
  running; the breakpoint is installed into the VM when a session does start.
- **No per-action confirmations.** Every change is visible in the Eclipse Debug UI as it happens, so
  the agent applies it without an approval prompt.

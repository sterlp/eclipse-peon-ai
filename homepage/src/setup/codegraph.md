---
title: Codegraph
description: Built-in Codegraph integration - auto-enabled for indexed projects
---

# Codegraph

[Codegraph](https://github.com/colbymchenry/codegraph) is a local code knowledge graph: it parses your source (tree-sitter) into a SQLite/FTS5 database and serves it as an MCP server (`codegraph serve --mcp`). Peon AI integrates it as a **built-in feature** — no MCP config entry needed.

## Prerequisite (your task)

Peon does **not** install or initialize Codegraph:

1. Install the `codegraph` binary (expected at `~/.local/bin/codegraph` or anywhere on your `PATH`).
2. Run `codegraph init` in the project you want indexed — this creates `.codegraph/codegraph.db`.

## How Peon enables it

When the active project has a `.codegraph/codegraph.db` file **and** the `codegraph` binary is found, Peon starts the Codegraph server automatically with the project as working directory — **independent of the MCP toggle** and isolated from your configured MCP servers: a Codegraph failure never touches them, and toggling MCP never kills it.

The state is shown by the **Codegraph icon** in the chat header:

| Icon | Meaning |
|---|---|
| Codegraph icon (lit) | Connected — `codegraph_explore` available to all agents |
| Codegraph icon (dimmed) | Not active — binary not found or no `.codegraph` index (the tooltip explains) |
| Stop icon | Connection failed (the tooltip shows the reason) |

Switching projects switches the connection: the previous project's server is disconnected and the new one connected. Without an index there is no connection attempt.

## Using it

- **`codegraph_explore`** — one call instead of a search loop: ask a question or name the symbols you are about to change and get the verbatim source of the relevant symbols plus the call path, grouped by file. All agents (developer, planner, custom, Jon) see it by default.
- While connected, the active project's path and the server's instructions are added to the agent context.
- **Paths:** `codegraph_explore` returns **disk paths**; the `eclipse*` tools expect workspace-qualified paths (`/ProjectName/path`). Peon does **not** rewrite tool output — the agent gets the disk→workspace mapping as a per-turn hint and converts the paths of the files it wants to open. Paths outside the project are not translated at all (use the `disk*` tools there).

## Removing an old manual entry

If you configured Codegraph manually in the [MCP configuration](/setup/mcp-configuration) before the built-in existed, **delete that entry** — the name `codegraph` is now reserved for the built-in. An old entry is skipped (with a log line) and the built-in wins.

## Disabling

v1 has no UI switch. To turn Codegraph off for a project, run `codegraph uninit` (removes the index) — or uninstall the binary.

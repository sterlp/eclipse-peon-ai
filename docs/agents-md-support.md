# AGENTS.md Support

## Purpose

AGENTS.md files provide standing orders — rules and context that are automatically prepended to every AI request. They are loaded once per project and persist across all agent modes.

## File Name Resolution

The system looks for these files in the project root, in this priority order:

1. `AGENTS.MD`
2. `AGENTS.md`
3. `Agents.md`
4. `agents.md`
5. `RULES.md`
6. `rules.md`
7. `AGENT.md`
8. `CLAUDE.md`
9. `claude.md`

The first file found is used. If none exist, no standing orders are loaded.

## What Happens When It Doesn't Exist

If no AGENTS.md variant is found, the system simply skips this step — no error is raised. The status line in the UI will not show an AGENTS.md indicator.

## Content Format

The file content is sent as-is to the AI. The system prepends the file path and a separator:

```
/Project/AGENTS.md:
---

[file content]
```

This lets the AI know where the rules come from.

## Toggle

AGENTS.md loading can be toggled on/off via the status line button (the "A" icon). This preference is persisted per workspace.

# AGENTS.md Support

> **Hinweis 2026-08-16:** AGENTS.md (+ AGENTS-\<agent\>.md) wird als **Dynamic Context** in die
> Chat History injiziert via `AgentsMdContextItem` (✅ 2026-08-16): einmal pro vollem Pfad, neu bei
> Projektwechsel (anderer Pfad) oder nach Compact, nie bei Datei-Änderung — fehlende Datei →
> übersprungen. Siehe [context-architecture.md](context-architecture.md) und
> [ADR-0029](adr/0029-file-context-in-history.md).
> Diese Doc beschreibt nur die File-Resolution (welcher Name, Fallback-Reihenfolge).

## Purpose

AGENTS.md files provide standing orders — rules and context that are injected into the chat history
once per full path (Dynamic Context: re-injected after Compact or project switch, never on file change
— see [context-architecture.md](context-architecture.md)). They apply to all agent modes.

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

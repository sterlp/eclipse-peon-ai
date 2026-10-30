# Agent-Specific AGENTS-<agent>.md

## Purpose

Agent-specific AGENTS-<agent>.md files provide standing orders that apply only to a specific agent. They are loaded alongside the base AGENTS.md, allowing project-wide rules and agent-specific rules to coexist.

## Agent Name Resolution

The system derives the agent name key from the active agent's display name:

| Agent | Name Key | File Loaded |
|-------|----------|-------------|
| Dev | `DEV` | `AGENTS-DEV.md` |
| Plan | `PLAN` | `AGENTS-PLAN.md` |
| Review | `REVIEW` | `AGENTS-REVIEW.md` |
| Jon | `Jon` | `AGENTS-JON.md` |
| Custom (name="Docs-Assistant") | `Docs-Assistant` | `AGENTS-Docs-Assistant.md` |

- **Built-in agents:** the key is the display name (ADR-0079: `Dev`, `Plan`, `Review`, `Jon`).
  `Dev`/`Plan`/`Review` resolve case-insensitively (see fallback below); `Jon` tries
  `AGENTS-Jon.md`, `agents-jon.md` and `AGENTS-JON.md`.
- **Custom agents:** the display name is used as-is.
- **Clean break (2026-10-30):** the former `Peon-` prefix stripping (`Peon-PO` → `PO`) is gone —
  an old `AGENTS-PO.md` is no longer loaded for Jon; rename it to `AGENTS-JON.md`.

## Case-Insensitive Fallback

For the agent-specific file, the system tries these names in order:

1. `AGENTS-DEV.md` — uppercase, exact key
2. `AGENTS-dev.md` — lowercase, exact key
3. `AGENTS-Dev.md` — title case (if different from 1 and 2)
4. `AGENTS-Dev-Agent.md` — blanks replaced with hyphens, uppercase
5. `AGENTS-dev-agent.md` — blanks replaced with hyphens, lowercase

The first file found is used. If none exist, no agent-specific content is loaded.

## Deduplication

Content from AGENTS.md and AGENTS-<agent>.md is deduplicated by string content. If both files contain identical text, it appears only once in the standing orders.

## Agent Switch

The agent name is evaluated at request time, so switching agents immediately changes which AGENTS-<agent>.md is loaded. No restart or reload is needed.

## Examples

### Peon-Dev with both files
```
AGENTS.md: "Be concise. One question at a time."
AGENTS-DEV.md: "Write tests first. Use JUnit 5."
```
Both are included in standing orders when Peon-Dev is active.

### Peon-Plan with only agent-specific file
```
AGENTS-PLAN.md: "Write BDD use cases. Use GIVEN/WHEN/THEN."
```
Only AGENTS-PLAN.md content is included when Peon-Plan is active.

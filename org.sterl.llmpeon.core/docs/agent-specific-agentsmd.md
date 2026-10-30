# Agent-Specific AGENTS-<agent>.md

## Purpose

Agent-specific AGENTS-<agent>.md files provide standing orders that apply only to a specific agent. They are loaded alongside the base AGENTS.md, allowing project-wide rules and agent-specific rules to coexist.

## Agent Name Resolution

The system derives the agent name key from the active agent's display name:

| Agent | Name Key | File Loaded |
|-------|----------|-------------|
| Dev | `Dev` | `AGENTS-Dev.md` |
| Plan | `Plan` | `AGENTS-Plan.md` |
| Review | `Review` | `AGENTS-Review.md` |
| Jon | `Jon` | `AGENTS-Jon.md` |
| Custom (name="Docs-Assistant") | `Docs-Assistant` | `AGENTS-Docs-Assistant.md` |

- **Built-in agents:** the key is the display name (ADR-0079: `Dev`, `Plan`, `Review`, `Jon`).
  `Dev`/`Plan`/`Review` resolve case-insensitively (see fallback below); `Jon` tries
  `AGENTS-Jon.md`, `agents-jon.md` and `AGENTS-JON.md`.
- **Custom agents:** the display name is used as-is.
- **Clean break (2026-10-30):** the former `Peon-` prefix stripping (`Peon-PO` → `PO`) is gone —
  an old `AGENTS-PO.md` is no longer loaded for Jon; rename it to `AGENTS-JON.md`. It also means a
  custom agent named `Peon-Dev` is resolved by its display name only: it gets
  `AGENTS-Peon-Dev.md` / `AGENTS-PEON-DEV.md`, never the built-in Dev agent's `AGENTS-DEV.md`.

## Case-Insensitive Fallback

For the agent-specific file the system offers these names in this order
(`AgentsMdContextItem.resolveAgentNames` — verbatim first, then the case variants):

1. `AGENTS-<Agent>.md` — exactly the display name
2. `agents-<agent>.md` — lowercase prefix and lowercase name
3. `AGENTS-<Agent>.md` — title case, only when it differs from 1 and 2
4. `AGENTS-<AGENT>.md` — blanks replaced with hyphens, uppercase
5. `agents-<agent>.md` — blanks replaced with hyphens, lowercase, only when it differs from 2

For `Dev` that is exactly `AGENTS-Dev.md`, `agents-dev.md`, `AGENTS-DEV.md` — 3 and 5 collapse into the
earlier variants and are skipped.

The first file found is used. If none exist, no agent-specific content is loaded.

## Deduplication

Content from AGENTS.md and AGENTS-<agent>.md is deduplicated by string content. If both files contain identical text, it appears only once in the standing orders.

## Agent Switch

The agent name is evaluated at request time, so switching agents immediately changes which AGENTS-<agent>.md is loaded. No restart or reload is needed.

## Examples

### Docs-Assistant with both files
```
AGENTS.md: "Be concise. One question at a time."
AGENTS-Docs-Assistant.md: "Cite the rule id. Run the docs linter before finishing."
```
Both are included in standing orders when Docs-Assistant is active.

### Docs-Assistant with only the agent-specific file
```
AGENTS-Docs-Assistant.md: "One doc per feature, ids allocated by the linter."
```
Only AGENTS-Docs-Assistant.md content is included when Docs-Assistant is active.

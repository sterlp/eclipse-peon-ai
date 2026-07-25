---
title: AGENTS.md & Skills
description: Project context and reusable skill instructions for Eclipse Peon AI
---

# AGENTS.md & Skills

Two ways to give Peon AI persistent context without repeating yourself every chat.

## AGENTS.md — Project-Wide Rules

Drop an `AGENTS.md` file into your Eclipse project root. As soon as you select any
file in that project, Peon AI picks it up and injects the content as a **standing-order**
context message — so it stays in effect across the conversation, even after the context is
compacted.

Use it for project-specific stuff:

- What the project is (one line)
- Key commands — build, test, run
- Important conventions or constraints
- Links to relevant specs or docs

## AGENTS-<agent>.md — Agent-Specific Rules

For rules that apply only to a specific agent, create an `AGENTS-<agent>.md` file alongside
your `AGENTS.md`. Both files are loaded when that agent is active.

| Agent | File | Example |
|-------|------|---------|
| Peon-Dev | `AGENTS-DEV.md` | coding conventions, test patterns |
| Peon-Plan | `AGENTS-PLAN.md` | BDD format, planning style |
| Custom agent "Docs-Assistant" | `AGENTS-Docs-Assistant.md` | documentation style guide |

The agent name key is derived from the agent's display name:
- **Built-in agents:** the part after "Peon-", uppercased. "Peon-Dev" → "DEV".
- **Custom agents:** the display name as-is.

The file name is case-insensitive with fallbacks: `AGENTS-DEV.md` is tried first, then
`AGENTS-dev.md`, then title case and hyphenated variants.

**Deduplication:** if both AGENTS.md and AGENTS-<agent>.md contain the same text, it appears
only once in the standing orders.

**Agent switching:** the right file is loaded automatically when you switch agents — no restart needed.

## Recommendations

- Read https://www.sri.inf.ethz.ch/publications/gloaguen2026agentsmd
- **Keep it short.** Every line gets sent on every request.
- Write it yourself
- **Note:** the LLM doesn't need headlines or any format ...

```markdown
# my-service

Spring Boot REST API, Java 21.

## Commands
- `mvn clean verify` — build + test
- `mvn spring-boot:run` — run locally

## Conventions
- Constructor injection only, no field injection
- Use the component architecture
   - API classes are all in `src/main/java/com/.../api/model`

## Docs
- API spec: [doc/api.md](doc/api.md)
```

The first matching file in the project root is used, checked in this order:
`AGENTS.md`, `Agents.md`, `agents.md`, `RULES.md`, `rules.md`, `AGENT.md`, `CLAUDE.md`, `claude.md`.

## Skills

Skills are reusable instruction sets shared across all your projects.
They live in the `skills` subfolder of your config directory (default `~/.peon/skills`); set the
base directory in **Window > Preferences > AI Peon > Peon Configuration** → *Config directory*.
A separate Eclipse project works fine — just point the preference at the folder.

Structure:

```
my-skills/
├── eclipse-ifile-paths/
│   └── SKILL.md
├── spring-boot-patterns/
│   └── SKILL.md
└── ...
```

At startup, Peon AI reads only the `name` and `description` from each skill (~100 tokens
per skill). The full content is loaded only when the LLM decides the task matches.
Good for knowledge that isn't project-specific: Eclipse API patterns, framework recipes,
code-style rules.

Skills follow the [agentskills.io](https://agentskills.io/specification) spec.


```yaml
---
name: spring-boot-patterns
description: Spring Boot patterns for REST APIs, JPA, and testing. Use when
  working on Spring Boot projects or when the user asks about Spring conventions.
---

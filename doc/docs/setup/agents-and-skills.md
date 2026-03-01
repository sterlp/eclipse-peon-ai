---
title: AGENTS.md & Skills
description: Project context and reusable skill instructions for Eclipse Peon AI
---

# AGENTS.md & Skills

Two ways to give Peon AI persistent context without repeating yourself every chat.

## AGENTS.md

Drop an `AGENTS.md` file into your Eclipse project root. As soon as you select any
file in that project, Peon AI picks it up and injects the content into every prompt
as a system message.

Use it for project-specific stuff:

- What the project is (one line)
- Key commands — build, test, run
- Important conventions or constraints
- Links to relevant specs or docs

## Recommondations

- Read https://www.sri.inf.ethz.ch/publications/gloaguen2026agentsmd
- **Keep it short.** Every line gets sent on every request.
- write it yourself

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

Both `AGENTS.md` and `agents.md` work.

## Skills

Skills are reusable instruction sets shared across all your projects.
Configure the skills directory in **Window > Preferences > Peon AI**.
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

# Spring Boot Patterns

...
```

See the bundled `writing-skills` skill for the full authoring guide.

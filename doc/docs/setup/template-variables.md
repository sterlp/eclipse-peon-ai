---
title: Template Variables
description: Use ${variable} placeholders in AGENTS.md and skill descriptions to inject dynamic Eclipse context into every prompt.
---

# Template Variables

Peon AI supports VS Code-style `${variable}` placeholders in **AGENTS.md**, **skill descriptions**, and **agent system prompts**.
Variables are substituted at request time with live values from your Eclipse workspace — no manual copy-paste required.

## Available Variables

| Variable | Description | Example value |
|---|---|---|
| `${currentDate}` | Today's date (ISO 8601) | `2026-03-07` |
| `${skillDirectory}` | Path to skills directory for agent/skill lookups | `/workspace/skills` |
| `${tokenSize}` | Current configured token size limit (integer) | `12` |
| `${tokenWindow}` | Maximum token window size for context processing (integer) | `20` |
| `${workPath}` | Absolute file-system path to the workspace root | `/home/user/workspace` |

All template variables are provided by [`TemplateContext.java`](https://github.com/sterlp/eclipse-peon-ai/blob/main/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/template/TemplateContext.java). Variables are substituted at request time with live values from your Eclipse workspace.

::: info "Unknown variables are left as-is"
If a variable is not available it is replaced with an empty string. Unrecognised `${foo-bar}` are left unchanged.
:::

## Usage in AGENTS.md

```markdown
# Project context — always kept up to date by Peon AI.
Today: ${currentDate}

## Conventions
- Java 21, no field injection
- All tests in `src/test/java`
```

## Usage in Skill descriptions

Skill descriptions (the `description:` frontmatter field) are also processed.
This lets you make a skill context-aware without touching its body:

```yaml
---
name: my-skill
description: >
  Patterns for Java projects. Use when working on code generation.
---

# My Skill
…
```

See [AGENTS.md & Skills](agents-and-skills.md) for the full authoring guide.
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
| `${currentSelectedFile}` | Eclipse-portable path of the selected resource | `/my-project/src/Foo.java` |
| `${currentProject}` | Name of the Eclipse project | `my-project` |
| `${currentDate}` | Today's date (ISO 8601) | `2026-03-07` |
| `${selectedText}` | Text currently highlighted in the editor | `public void foo() {…}` |
| `${workPath}` | Absolute file-system path to the workspace root | `/home/user/workspace` |

Variables are split across two classes by scope:

- Generic (available everywhere): [`TemplateContext.java`](https://github.com/sterlp/eclipse-peon-ai/blob/main/org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/template/TemplateContext.java) — `${currentDate}`, `${workPath}`
- Eclipse-specific: [`EclipseTemplateContext.java`](https://github.com/sterlp/eclipse-peon-ai/blob/main/org.sterl.llmpeon/src/org/sterl/llmpeon/parts/shared/EclipseTemplateContext.java) — all workspace/selection variables

!!! info "Unknown variables are left as-is"
    If a variable is not available (e.g. `${currentSelectedFile}` when no file is selected),
    it is replaced with an empty string. Unrecognised `${names}` are left unchanged.

## Usage in AGENTS.md

```markdown
# ${currentProject}

Project context — always kept up to date by Peon AI.
Today: ${currentDate}

## Current file
Working on: ${currentSelectedFile}

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
  Patterns for ${currentProject}. Use when working on Java files.
  Current file: ${currentSelectedFile}
---

# My Skill
…
```

See [AGENTS.md & Skills](agents-and-skills.md) for the full authoring guide.

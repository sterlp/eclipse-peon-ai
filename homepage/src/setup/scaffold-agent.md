---
outline: deep
---

# Scaffold Agent

The **Scaffold Agent** is a built-in agent that helps you create and edit agents, skills, and commands using natural language — no manual YAML editing required.

## What it does

- **Create** new custom agents, skills, or commands from a simple description
- **Edit** existing agents, skills, or commands
- **List** all your agents, skills, and commands
- **Delete** agents, skills, or commands

## How to use it

1. Open the Peon AI view
2. Select **Scaffold** from the agent dropdown
3. Ask it to create, edit, or list your artifacts

## Example prompts

```
Create a CodeReviewer agent with grep and read tools
```

```
Create a skill called "Search Files" that finds files by pattern
```

```
List all my skills
```

```
Edit the CodeReviewer agent to also have the write tool
```

## How it works

The scaffold agent uses config-scoped disk tools to read and write files in your Peon AI config directory. After creating or editing any artifact, it calls `reloadConfig()` to make the changes immediately available — no restart needed.

::: tip No restart needed
Changes are picked up instantly. The scaffold agent triggers a config reload after every create/edit operation.
:::

::: warning Config directory must be set
The scaffold agent requires a valid config directory. Set it in Preferences → Peon AI → Configuration → Config Directory.
:::

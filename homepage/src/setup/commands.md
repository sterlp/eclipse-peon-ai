---
title: Commands
description: Custom slash commands for Eclipse Peon AI
---

# Commands

Commands allow you to define reusable slash commands (e.g., `/review`, `/plan`) that can be invoked in the chat interface. These commands are loaded from `.md` files in a configured directory and provide a way to standardize or automate common tasks.

Commands are basically `SKILLS` light for common stuff you don't want repeat yourself each time. Ask the LLM to create/extract them if needed.

> DISK Tools needed!

## Configuration

### Directory Setup
1. Configure the commands directory in the Peon AI preferences:
   - Navigate to **Window > Preferences > Peon AI**.
   - Set the "Commands directory" field to the path containing your command files.
   - Path resolution matches the existing skills directory behavior, including support for workspace-relative paths.

### Command Files
- Each command is defined in a separate `.md` file (e.g., `review.md`, `plan.md`).
- The filename (without the `.md` extension) becomes the command name (e.g., `/review`, `/plan`).
- Files in subdirectories or hidden files (starting with `.`) are ignored.
- the content is just he command
- header is optional

### Optional Frontmatter

### `review.md`

```markdown
Review the code and report any issues.
```


### `foo.md`

```markdown
---
name: review
---
Review the code and report any issues.
```

## Usage

1. In the chat interface, type `/` to see a list of available commands.
2. Select a command to insert its name into the chat input.
3. The command body is added as a **standing order** — prepended to your message for the current task, alongside the project context and `AGENTS.md`.

## Effect

- Commands are injected as **standing orders**: the command body is prepended to your message for the current task.
- They **survive session compaction** — if the model compacts the chat mid-task, the command is re-injected automatically, so it keeps governing the work.
- The command is consumed after that send and does **not** carry into your next message.
- **Skills behave the same way** — the skill body is a one-time standing order that also survives compaction.

## Notes

- Commands are case-insensitive.
- Tool rules remain per mode (PLAN/DEV/AGENT) as before.

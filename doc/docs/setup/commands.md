---
title: Commands
description: Custom slash commands for Eclipse Peon AI
---

# Commands

Commands allow you to define reusable slash commands (e.g., `/review`, `/plan`) that can be invoked in the chat interface. These commands are loaded from `.md` files in a configured directory and provide a way to standardize or automate common tasks.

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

### Optional Frontmatter
Add a YAML frontmatter to include a description for the command. This description appears in the slash menu.

Example:
```markdown
---
description: Review the code carefully and report findings.
---
# Review Command

Review the code and report any issues.
```

## Usage

1. In the chat interface, type `/` to see a list of available commands.
2. Select a command to insert its name into the chat input.
3. The command body replaces the system prompt for that turn. Standing orders (project context, AGENTS.md) and the skill catalog are still appended after it.

### Example
1. Create a file named `review.md` in the commands directory:
   ```markdown
   ---
   description: Review the code carefully and report findings.
   ---
   # Review Command

   Review the code and report any issues.
   ```

2. In the chat, type `/review` to use the command.

## Effect

- Commands provide a quick way to insert predefined prompts or instructions into the chat.
- They help standardize workflows and reduce repetitive typing.
- The LLM processes the command body as the system prompt for that single turn, ensuring consistent responses.

## Implementation Details

- **Preferences**: The "Commands directory" field in preferences allows you to configure the path to your command files.
- **Core**: The `CommandService` scans the directory for `.md` files, parses optional frontmatter descriptions, and creates `CommandRecord` objects for each command.
- **Chat UI**: A `SlashMenuPopup` appears while typing `/` to show available commands. The popup disappears once whitespace separates the command from the rest of the message.
- **Invocation**: When a command is invoked, the `AIChatView` parses the command name, reads the command body (with template variables processed), and sets a one-shot system prompt that replaces the base system prompt for the next LLM call only. Unknown commands show an error and abort the send operation.

## Notes

- Commands are case-insensitive.
- The command body supports template variables (e.g., `${variable}`).
- Tool rules remain per mode (PLAN/DEV/AGENT) as before.

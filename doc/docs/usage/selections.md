---
title: Context Selection
---

# Automatic Context Inclusion

When you work with Peon AI, information about your current workspace is automatically included in every chat message. You don't need to attach files or specify paths manually.

## What Gets Sent

| Element | Source | How It Appears |
|---------|--------|---------------|
| **Project** | Active Eclipse project | Project name and path |
| **Selected File** | File shown in your editor (or nothing if no file is open) | Full file path |
| **Text Selection** | Highlighted code/text | Code snippet with line numbers |

## How It Works

1. Select or open a file in the Eclipse editor
2. The status bar shows which project and file are active
3. Every message you send includes this context automatically
4. The AI can reference "the selected file" without you naming it

![Context selection](../assets/context-selection.png)

> *The status bar displays your current project and selected file — both are included in chat context.*

## Example Workflow

1. Open a Java class in the editor
2. Ask: _"What does this code do?"_
3. The AI knows which file you're referring to and responds about that specific code

No need to say "in UserContext.java" — it's already included.

## Verification

Hover over the file name in the status bar to see a tooltip confirming automatic inclusion. You can also check that the AI sees your context by asking about "the selected file" — if it responds with information about your current file, the context is being sent correctly.

## Pinning Your Project

If you navigate between files in different projects, the active project may change. Use the **pin button** (📌) in the status bar to lock your current project:

- Click 📌 to pin — navigation won't change the active project
- Click again to unpin — project follows your file selection

This is useful when you want to keep working on one project while browsing files elsewhere.

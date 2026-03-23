---
title: Agent Mode
description: Let Peon AI plan and implement changes autonomously using your project files
---

# Agent Mode (v0.1.0)

Agent mode is a two-phase loop: the AI first writes a plan, then implements it — driven by a single chat message from you.

## How it works

1. Select a project in Eclipse so Peon AI knows where to read and write files.
2. Switch the mode combo to **Peon-Agent**.
3. Describe what you want done and hit **Send** — the AI creates `peon-plan/overview.md` in your project.
4. Review the plan. If it looks good, tick **Auto** and send your last message to kick off implementation.

The planner writes `peon-plan/overview.md`. The developer agent picks it up and starts making changes. That's the whole loop.

## Auto checkbox

**Tick it only when you send your final message** — that's the trigger. Once checked and the message is sent, the developer agent starts immediately after the plan is saved. If you're not ready, leave it unchecked; you can always start implementation manually with **Start Impl.** after reviewing `peon-plan/overview.md`.

## Pin

Use the pin button in the status line to lock the active project. Handy when you're navigating between files while the agent is working — the project binding won't follow your clicks.

## Limitations (v0.1.0)

- One project at a time.
- The plan file is always `peon-plan/overview.md` in the project root.
- No automatic retry on tool errors — check the chat log if something goes wrong.

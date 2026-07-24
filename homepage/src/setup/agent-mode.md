---
title: Agent Mode
description: Let Peon AI plan a change, then hand it over for implementation
---

# Agent Mode

Agent mode is a **plan → implement** workflow built from two cooperating built-in agents:

- **Peon-Plan** — a read-only planner. It explores the project and writes a structured plan to
  `peon-plan/overview.md`. Edit tools are filtered out, so it can never change your code.
- **Peon-Dev** — the implementer. It reads the plan and makes the actual changes.

## How it works

1. Select a project in Eclipse so Peon AI knows where to read and write files.
2. Pick **Peon-Plan** from the agent dropdown.
3. Describe what you want done and hit **Send** — the planner explores the project and calls its
   `planSave` tool to write `peon-plan/overview.md`.
4. Review the plan. When it looks good, click the **Handoff → Peon-Dev** button next to the
   input. Control transfers to Peon-Dev, seeded with the saved plan.
5. Peon-Dev implements the plan. When finished it can archive the plan with `planImplemented`
   (moves it to `peon-plan/overview-done-<timestamp>.md`).

The handoff passes the saved `peon-plan/overview.md` if one exists, otherwise the planner's last
message — prefixed with `Handover from Peon-Plan`.

## The handoff button

Any agent whose configuration declares a handover target shows a **Handoff → [agent]** button.
For Peon-Plan the target is hard-wired to Peon-Dev; [custom agents](./custom-agents.md) set it
with the `handover:` frontmatter field, which lets you chain your own workflows
(e.g. plan → dev → review).

## Plan tools

The plan lives in `peon-plan/overview.md` in the project root and is managed by dedicated tools
(available to any agent that allowlists them):

| Tool | Purpose |
|------|---------|
| `planRead` | Read the current plan, if one exists. |
| `planSave` | Write/overwrite the final plan. |
| `planUpdate` | Apply a targeted edit to the plan. |
| `planImplemented` | Archive the plan with a timestamp once fully implemented. |

## Pin

Use the pin button in the status line to lock the active project. Handy when you navigate
between files while the agent works — the project binding won't follow your clicks.

## Limitations

- One project at a time.
- The plan file is always `peon-plan/overview.md` in the project root.
- No automatic retry on tool errors — check the chat log if something goes wrong.

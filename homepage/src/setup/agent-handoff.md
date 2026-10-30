---
title: Agent Handoff
description: Hand off work between agents via the Handoff button
---

# Agent Handoff

Agent handoff lets any agent pass work to another. Click the **Handoff → [Agent]** button to transfer control.

## How it works

1. Select a project in Eclipse so Peon AI knows where to read and write files.
2. Pick an agent from the dropdown (e.g. **Plan**).
3. Describe what you want done and hit **Send** — the agent works on the task.
4. When ready to hand off, click the **Handoff → [Agent]** button next to the input.
   Control transfers to the target agent, seeded with the previous agent's work.

For example, [Plan](./agents) hands off to Dev by default — the plan file
(`peon-plan/overview.md`) is passed to Dev for implementation.

## The handoff button

Any agent with a configured handover target shows a **Handoff → [Agent]** button next to the chat input.
[Plan](./agents) hands off to Dev by default.
[Custom agents](./custom-agents) set a target with the `handover:` frontmatter field, which lets you chain your own workflows
(e.g. plan → dev → review).

The handoff passes the saved `peon-plan/overview.md` if one exists, otherwise the previous agent's last
message — prefixed with `Handover from [Agent]`.

## Plan tools

The plan lives in `peon-plan/overview.md` in the project root and is managed by dedicated tools
(available to any agent that allowlists them):

| Tool | Purpose |
|------|---------|
| `planRead` | Read the current plan, if one exists. |
| `planSave` | Write/overwrite the final plan. |
| `planUpdate` | Apply a targeted edit to the plan. |
| `planImplemented` | Archive the plan with a timestamp once fully implemented. |

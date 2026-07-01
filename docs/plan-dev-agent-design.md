# Plan Agent & Dev Agent — System Design

## Overview

A two-agent pipeline where a **Plan Agent** decomposes a development goal into
structured, self-contained task increments and hands each one off sequentially
to a **Dev Agent** for implementation. All artifacts are stored in the feature
branch alongside the code they produce.

---

## Principles

- Each task is a self-contained, verifiable increment
- No parallel Dev Agents within one plan — parallelism happens at plan level
- Token efficiency is a first-class concern throughout
- The plan lives in the branch — committed alongside the code it produces
- Git is the source of truth for history — no duplication inside documents
- Human oversight is configurable, not hardcoded
- The **What** reflects human will — the Plan Agent elicits and clarifies, never invents
- The **How** is the Plan Agent's domain — humans approve, agents execute
- Planning is iterative — What, Architecture, and How may all require revision
  as new information emerges; during planning all task designs are freely revisable
- Once a task is committed, it is immutable — only pending tasks are ever re-evaluated

---

## LLM Settings

Only temperature is set — top_p left to provider defaults (Anthropic and OpenAI
treat top_p as an alternative to temperature, not a complement).

| Agent | Temperature |
|---|---|
| Plan Agent | 0.8 — creative enough for elicitation and architecture, coherent enough for task design |
| Dev Agent | 0.2 — near-deterministic; correctness over diversity |

---

## Future: Agent Specialisation

In a future iteration the Plan Agent may be split into two dedicated agents:

```
Developer / Designer
       │
       ▼
┌─────────────────────┐
│  Elicitation Agent  │  temp: 0.8 — builds What + Architecture with the human
└─────────────────────┘
       │
       ▼
┌─────────────────────┐
│  Task Design Agent  │  temp: 0.2 — breaks Architecture into task MDs
│  (reuses Dev Agent  │  Same model and tooling, planning-focused system prompt
│   with plan focus)  │
└─────────────────────┘
       │
       ▼
┌─────────────────────┐
│     Dev Agent       │  temp: 0.2 — implements each task increment
└─────────────────────┘
```

---

## Folder Structure

One plan per branch.

```
.plan/
  overview.md           ← architecture, settings, protected areas, task list
  task-01.md            ← active or pending tasks
  task-02.md
  task-01.done.md       ← accepted tasks (renamed by Plan Agent, never reloaded)
```

---

## Branching Convention

The `plan-id` is derived from the branch name suffix — no separate ID field needed.

| Branch | Purpose |
|---|---|
| `plan/{plan-id}` | Main feature branch |
| `debug/plan-{id}-task-{n}-attempt-{x}` | Failed attempt; deleted after successful retry; only last attempt kept on escalation |

---

## overview.md

```yaml
---
mode: interactive        # interactive | autonomous
max_retries_per_task: 3
verification_timeout: 300s
protected_areas:
  - Payment processing module  # component/domain level — not file level
  - Auth / Security layer      # omit section if none defined
---
```

### Sections

| # | Section | Owner | Mutability |
|---|---|---|---|
| 1 | **What** — the human's goal in precise language, co-authored in Stage 1 | Developer | Never changed by Plan Agent without explicit approval |
| 2 | **Architecture & Design Decisions** — key choices and reasoning | Plan Agent | Append-only; deletion/overwrite requires human approval |
| 3 | **How** — overall technical strategy before task breakdown | Plan Agent | Revisable at checkpoints; developer approves in interactive mode |
| 4 | **Protected Areas** — components Dev Agent must never touch | Developer + Plan Agent | Never changed without approval; may be empty |
| 5 | **Task List** — ordered task table (see Task Sizing Rules) | Plan Agent | Updated by Plan Agent at each checkpoint |

The **Task List** and individual task files are only created when `overview.md`
exceeds 40% of the context window. Below that threshold the Dev Agent works
directly from the overview — no task files needed.

| # | Task File | Status | Result |
|---|---|---|---|
| 1 | task-01.done.md | done | Auth base class created |
| 2 | task-02.md | active | — |
| 3 | task-03.md | pending | — |

---

## task-N.md

A compressed, self-contained view of one piece of work. Must contain everything
the Dev Agent needs — the Plan Agent embeds all relevant context from the overview
(naming conventions, package structure, constraints) directly into the How.

**The Dev Agent loads the task file only.** It may consult `overview.md` only if
the task explicitly references it. Frequent overview lookups signal incomplete task
design — a Plan Agent quality issue, not a Dev Agent workflow.

```markdown
---
status: pending     # pending | active | done | failed
fail-count: 0
---

## What
Functional or business outcome this increment must deliver.
A precise slice of the overview What. Never changed by the Plan Agent autonomously.

## How
Technical approach the Dev Agent must follow.
Plan Agent may revise between retries or after a cascade review.

## Files to Change
- `src/auth/LoginService.java` — add token validation method

## Files to Add
- `src/auth/TokenValidator.java` — new class, implements validation, used by the service

## Files Must Not Touch
- `src/auth/SecurityConfig.java`
- Any file under `src/payment/`

## Verification
Accepts when: [one sentence linking back to the What]
Command: `./gradlew test`
Timeout: 5 minutes
Expected: All tests green, no new compilation warnings, no PMD errors
```

---

## Plan Creation — Three Stages

### Stage 1 — What (Scope)
Plan Agent elicits the human's intent: asks clarifying questions, surfaces ambiguities,
reflects intent back in precise language. Also browses the codebase to understand the
problem and key areas to change.

### Stage 2 — Architecture
Plan Agent proposes architecture and design decisions for developer approval.
Flags anything in the What that is technically ambiguous or infeasible.
Captures naming patterns and package structure from the existing codebase for use in task MDs.

### Stage 3 — How (Task Breakdown)
Plan Agent breaks the agreed architecture into ordered task increments.
If a task requires an architectural decision not made in Stage 2, the Plan Agent
surfaces the gap immediately and triggers a mini-revision before proceeding.
All tasks remain freely revisable at this stage — no code has been committed yet.

---

## Iterative Feedback Loop

```
What  ←──────────────────────────────────────┐
  └─→  Architecture  ←────────────────────── │
          └─→  Task Design  ←──────────────  │
                  └─→  Implementation ───────┘
```

When a gap is found, the Plan Agent: identifies the affected layer → fixes it →
cascades downward to re-evaluate everything below. Committed tasks are never
re-evaluated — they are immutable history.

---

## Commit Protocol

Two commits per task, clearly separated by owner:

```
1. Dev Agent commit
   └─ Code changes only
   └─ Message: short description of what was implemented

2. Plan Agent commit  (PO acceptance — like Scrum sprint review)
   └─ Renames task-N.md → task-N.done.md
   └─ Updates Task List entry in overview.md
   └─ No code changes
   └─ Message: "plan accepted: task-N — <one-liner result>"
```

The Plan Agent's commit is the formal acceptance stamp.
`git log` reads as alternating dev/plan commits — a clean audit trail.

---

## Token Management

- **Dev Agent context**: task MD only (overview optional if explicitly referenced in task)
- **Compression trigger**: Plan Agent compresses at ≥ 70% of context window before loading the next task
- **After acceptance**: task-N.md → task-N.done.md; done files never reloaded
- **Architecture & Design Decisions**: append-only; never compressed or removed without human approval

---

## Failure Triage

```
Task fails verification
  └─ Push to: debug/plan-{id}-task-{n}-attempt-{x}
  └─ Collect: error output (last 100 lines) + git diff

  Triage — identify root cause:

  [1] Task How wrong, architecture sound
      └─ Revise task How → retry

  [2] Architecture missing or incorrect
      └─ Append fix to Architecture & Design Decisions
      └─ Cascade review all pending tasks (How revisions only; What → escalate)
      └─ Retry counter resets

  [3] What ambiguous, conflicting, or infeasible
      └─ Escalate to human regardless of mode — do not retry until resolved

  Retry steps (both modes):
      └─ Plan Agent saves revised task MD in memory
      └─ git reset --hard to last good commit  ← wipes .plan/task-N.md from disk
      └─ Delete untracked files from failed attempt
      └─ Plan Agent re-writes revised task-N.md
      └─ Increment fail-count in task MD header
      └─ [interactive] Escalate to developer before retrying
      └─ [autonomous]  Retry silently

  After 3 failures (same triage cycle):
      └─ Keep last debug branch
      └─ Escalate with: all 3 error outputs, diffs, and revised task MDs
      └─ Pause execution
```

---

## Inter-Task Checkpoint (Plan Agent Re-Entry)

After every successful Dev Agent commit, before the Plan Agent acceptance commit:

1. Check if any protected area was touched → escalate if yes
2. Check if the increment revealed an architectural gap → trigger cascade if yes
3. Evaluate whether pending task How sections need re-scoping
4. Rename task-N.md → task-N.done.md; update Task List with one-liner result
5. **[interactive]** Present diff summary to developer; wait for approval or adjustments
6. **[autonomous]** Continue silently
7. Plan Agent commits plan housekeeping (acceptance commit)

---

## End-of-Plan Wrap-Up

1. Mark all tasks done in Task List
2. Delete all task MD files — `overview.md` is the only remaining plan artifact
3. Compress `overview.md` — remove working notes; keep What, How, Architecture
   Decisions, Protected Areas, Task List
4. Plan Agent commits: `plan complete: <plan-id>`

---

## Agent Modes

### Interactive
- Plan Agent pauses at every checkpoint; developer reviews diff and approves
- Developer owns all What changes; Plan Agent proposes How changes for approval
- Task failure → immediate escalation before any retry

### Autonomous
- Plan Agent continues silently through checkpoints
- Plan Agent may revise How sections; never touches What sections
- Task failure → triage, self-revise, retry up to 3 times
- Both modes: protected area touched → hard stop, escalate to human

---

## Settings Reference

| Setting | Value |
|---|---|
| Mode | `interactive` or `autonomous` |
| Plan Agent temperature | 0.8 |
| Dev Agent temperature | 0.2 |
| top_p | Not set — provider defaults |
| Max retries per task | 3 per triage cycle |
| Verification timeout | 5 minutes |
| Task file threshold | overview.md > 40% of context window |
| Task target size | ≤ 40% of context window |
| Compression trigger | ≥ 70% of context window |
| Dev Agent context | task MD only (overview optional) |
| Parallelism | Plan-level only |
| plan-id | Derived from branch name suffix |
| Cascade review scope | Pending tasks only |

# Plan Agent & Dev Agent — System Design

## Overview

A two-agent pipeline where a **Plan Agent** decomposes a development goal into
structured, self-contained task increments and hands each one off sequentially
to a **Dev Agent** for implementation. All artifacts are stored in the feature
branch alongside the code they produce.

---

## Idea / Principles

- Each task is a self-contained, verifiable increment
- No parallel Dev Agents within one plan — parallelism happens at plan level
- Token efficiency is a first-class concern for throughout
- The plan is part of the codebase — stored in the branch, committed with the code
- Human oversight is configurable, not hardcoded
- Git is the source of truth for history — no duplication inside documents
- The **What** always reflects human will — the Plan Agent elicits and clarifies, never invents
- The **How** is the Plan Agent's domain 
— humans approve, agents execute
- Planning is iterative, not a one-way waterfall — What, Architecture, and How may all
  require revision as new information emerges during implementation
- task breakdown may change plan and architecture, during planning, with human approval - revisit finished tasks needed

---

## LLM Settings

Only temperature is set. Top_p is left to provider defaults.
*Anthropic and OpenAI treat top_p as an alternative to temperature, not a
complement — combining both is not recommended by either vendor.*

| Agent | Temperature | Rationale |
|---|---|---|
| Plan Agent | 0.8 | Creative enough for elicitation, architecture, and task design while remaining coherent |
| Dev Agent | 0.2 | Near-deterministic code generation; correctness over diversity |

---

## Future idea: Agent Specialisation

The current single Plan Agent handles two very different types of work:
1. **Elicitation and Architecture** — open-ended, collaborative, creative (benefits from higher temperature)
2. **Task Breakdown** — structured, precise, technical (benefits from lower temperature)

In a future iteration these may be split into dedicated agents:

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
│  (reuses Dev Agent  │  Same model and tooling as Dev Agent, different system
│   with plan focus)  │  prompt focused on planning rather than implementation
└─────────────────────┘
       │
       ▼
┌─────────────────────┐
│     Dev Agent        │  temp: 0.2 — implements each task increment
└─────────────────────┘
```

---

## Folder Structure

Each branch may have only one plan right now.

```
.plan/
  overview.md           ← master document (architecture, settings, protected areas,
                           task list)
  task-01.md            ← active or pending task files
  task-02.md
  task-01.done.md       ← archived after commit (one-liner, never reloaded into context)
```

All `.plan/` files live inside the feature branch.
Never load `.done.md` files back into agent context.

---

## overview.md — Header Schema

```yaml
---
mode: interactive        # interactive | autonomous
max_retries_per_task: 3
verification_timeout: 300s
protected_areas:
  - Payment processing module
  - Auth / Security layer
  - Database migration scripts
---
<the Plan itself>
```

---

## Plan Creation Phase (Before Any Code)

The Plan Agent works through three sequential stages before writing any task:

### Stage 1 — Scope (What)
The Plan Agent collaborates with the developer or designer to produce the What.
This is an elicitation phase — the Plan Agent asks clarifying questions, surfaces
ambiguities, and reflects back the human's intent in precise language.
The Plan Agent's role here is **scribe and clarifier**, not inventor.
The resulting What must represent the human's will as accurately and completely as possible.
The plan agent has to browse the current code structure and to understand the problem and the key areas to change.

### Stage 2 — Architecture
With the What agreed, the Plan Agent proposes the architecture and key design decisions.
The developer reviews and approves. The Plan Agent must flag here if anything in the
What appears technically ambiguous, conflicting, or infeasible — surfacing this now
is far cheaper than discovering it during a task increment.
Based on the current code structure the naming patterns and packages structure is discovered and but into the overview for the subtasks.

### Stage 3 — How (Task Breakdown)
With architecture agreed, the Plan Agent breaks the work into ordered task increments.
During task design, the Plan Agent may discover that a task requires an architectural
decision not made in Stage 2. When this happens the Plan Agent must **surface the gap
immediately** rather than silently work around it — triggering a mini-revision of the
Architecture section before proceeding.

---

## Iterative Feedback Loop

Planning is not a one-way waterfall. Changes can propagate upward:

```
What  ←──────────────────────────────────────┐
  └─→  Architecture  ←────────────────────── │
          └─→  Task Design (How)  ←────────  │
                  └─→  Implementation ───────┘
                           │
                     may reveal gaps in
                     any layer above
```

When a gap is discovered at any layer, the Plan Agent must:
1. Identify which layer needs the fix (task How, Architecture, or What)
2. Apply the fix at the correct layer
3. **Cascade downward**: re-evaluate everything below the changed layer

---

## overview.md — Sections

### 1. What
The agreed, immutable goal of this plan — the human's will captured in precise language.
Co-authored by the developer/designer and Plan Agent during Stage 1.
**Never changed unilaterally by the Plan Agent**, in either mode.
Changes require explicit developer approval or input, even in autonomous mode.

### 2. Architecture & Design Decisions
Key architectural choices and the reasoning behind them.
This section is usually **append-only** — decisions are only deleted or overwritten with human approval, if a prior decision is superseded.

### 3. How
The Plan Agent's current strategy for achieving the What — the overall technical
approach before it is broken into tasks.
In autonomous mode, the Plan Agent may revise this section at checkpoints.
In interactive mode, the developer approves any changes.

### 4. Protected Areas
Component or domain areas that must not be changed by the Dev Agent.
Defined at component/domain level — not at file level during the Plan phase.
If changes are required in a protected area, **always escalate to human**,
regardless of mode.
It is possible that no protected areas are defined by the developer.

### 5. Task List
Ordered list of all tasks. Plan Agent ensures linear ordering —
later tasks may depend on earlier ones, never the reverse.
**Tasks list is optional and are only created if the overview.md of the Plan exceeds 40% of the context size.**

| # | Task File  | Status  | Result                  |
|---|------------|---------|-------------------------|
| 1 | task-01.done.md | done    | Auth base class created |
| 2 | task-02.md | active  | —                       |
| 3 | task-03.md | pending | —                       |

### 6. What Was Done  *(end-of-plan only)*
Compressed summary written by the developer agent as commit message. Short list of key actions taken.

---

## task-N.md — Schema

Each task carries its own What and How.
It is a compressed view on one pice of work which needs to be done. The task What conains only the relevant slice from the overview and maybe further information by the human which are only relevant for this task. The task How is the Plan Agent's technical instruction to the Dev Agent for this increment only.

The Dev Agent **reads only the task file**. It implements the How to satisfy the What.
If a task implementation fails, the Plan agent has to write a new task plan.

```markdown
---
status: pending          # pending | active | done | failed
fail-count: 0
---

## What
The functional or business outcome this increment must deliver.
A precise slice of the overview What.
Owned by the developer. Never changed by the Plan Agent autonomously.
In interactive mode: developer must approve any change.
In autonomous mode: Plan Agent may not touch this section.

## How
The technical approach the Dev Agent must follow to achieve the What.
The Plan Agent may revise this section between retries or after a cascade review
triggered by an architectural change.

## Files to Change
- `src/auth/LoginService.java` — add token validation method

## Files to Add
- `src/auth/TokenValidator.java` — new class

## Files Must Not Touch
- `src/auth/SecurityConfig.java`
- Any file under `src/payment/`

## Verification
Accepts when: [one sentence linking back to the task What]
Command: `./gradlew test`
Timeout: 5 minutes
Expected: All tests green, no new compilation warnings, no pmd errors
```

---

## Ownership Summary

| Section | Created by | Mutable by Plan Agent | Mutable by Dev Agent |
|---|---|---|---|
| overview — What | Developer + Plan Agent (elicitation) | Never without approval | Never |
| overview — Architecture & Decisions | Plan Agent (approved by developer) | Append-only | Never |
| overview — How | Plan Agent | Yes, at checkpoints | Never |
| overview — Protected Areas | Developer + Plan Agent | Never without approval | Never |
| task — What | Plan Agent (derived from overview What) | Never without approval | Never |
| task — How | Plan Agent | Yes, between retries and after cascade | Never |
| task — Files Must Not Touch | Plan Agent | Yes, at checkpoints | Never |
| task — Verification | Plan Agent | Yes, at checkpoints | Never |

The developer agent commits its work on success leaving the commit message for the plan agent. 

---

## Task Sizing Rules

| Plan Size vs. Context Window     | Action                                                      |
|----------------------------------|-------------------------------------------------------------|
| Full plan fits in context        | No task files created — single-shot dev run                 |
| Individual task ≤ 40% of window  | Normal single task                                          |
| Individual task 40–70% of window | Single task, flagged as large                               |
| Individual task > 70% of window  | Plan Agent splits into two tasks with a verifiable midpoint |

---

## Context & Token Management

- **Dev Agent loads only**: current `task-N.md` — may read the overview.md if needed, nothing else
- **Compression trigger**: when context reaches 70% of window during a task,
  Plan Agent compresses completed content before loading the next task
- **Before each commit on success**: rename `task-N.md` → `task-N.done.md`;
  update Task List in `overview.md`; append done to the task name - by the plan agent 
- **Architecture & Design Decisions**: append-only, never compressed or removed only with human approval 
- **Commit messages** are the canonical record of what was done — git is the
  history, not the document
- **Cascade review** loads only pending task MDs — done files are never reloaded

---

## Failure Triage Protocol

When a task fails verification, the Plan Agent triages before deciding how to respond:

```
Task fails
  └─ Triage: what is the root cause?

  [1] Task-level problem — the How was wrong but architecture is sound
      └─ Revise task How, retry (up to 3 attempts)

  [2] Architecture-level problem — a design decision was missing or incorrect
      └─ Append correction to Architecture & Design Decisions in overview.md
      └─ Trigger Architectural Change Protocol (cascade review of all pending tasks)
      └─ Retry counter resets (fresh 3 attempts with corrected architecture)

  [3] What-level problem — the task What is ambiguous, conflicting, or infeasible
      └─ Always escalate to human regardless of mode
      └─ Do not retry until human clarifies
```

**Retry limit**: 3 per task per triage cycle. Mandatory human escalation if all exhausted.

---

## Architectural Change Protocol

Triggered when the Plan Agent appends a new or revised decision to Architecture &
Design Decisions — whether during task design, a checkpoint, or a failure triage.

```
Architectural change detected
  └─ Append new decision to Architecture & Design Decisions (never overwrite)
  └─ Load all pending task MDs (not done files — those are committed and immutable)
  └─ Re-evaluate each pending task against the new architectural decision:
       Does this task's How need revision?  → revise autonomously
       Does this task's What need revision? → escalate to human regardless of mode
  └─ Continue with current task using revised How
```

The cascade review is mandatory and must complete before the Dev Agent resumes.
Done tasks are never re-evaluated — they are committed history.

---

## Failure & Retry Protocol

```
Task verification fails
  └─ Push working state to: debug/plan-{id}-task-{n}-attempt-{x}
  └─ Collect: error output (last 100 lines) + git diff
  └─ Run triage (see Failure Triage Protocol above)

  [interactive mode]
    └─ Escalate to developer with triage result + diff + error
    └─ Developer provides guidance, may update What or How
    └─ Reset branch, apply revised task MD, retry

  [autonomous mode]
    └─ Delete previous debug branch
    └─ Reset working branch (git reset --hard to last good commit)
    └─ Delete any added files not tracked before the task started started
    └─ Apply fix at correct layer (task How or Architecture)
    └─ Run cascade review if architectural change was made
       build a new task plan plan
       increment the task error counter
    └─ Retry with revised task MD

  Attempt 3 fails (either mode, within same triage cycle)
    └─ Keep last debug branch
    └─ Escalate to human with: all 3 error outputs, all 3 diffs, all revised task MDs
    └─ Pause plan execution
```

---

## Inter-Task Checkpoint (Plan Agent Re-Entry)

Runs after every successful task commit, before loading the next task.

**Plan Agent reads:**
- Last commit message
- `overview.md` (What, Architecture, How, Task List)
- Git diff of completed task

**Plan Agent then:**
1. Checks if any protected area was touched → escalate if yes
2. Checks if the completed increment revealed any architectural gap →
   trigger Architectural Change Protocol if yes
3. Evaluates whether future task How sections need re-scoping
4. Updates Task List in `overview.md`: marks completed task as done with one-liner result
5. **[interactive]** Pauses, presents summary to developer, waits for go-ahead or adjustments
6. **[autonomous]** Continues silently, loads next task

---

## End-of-Plan Wrap-Up

After the final task commits successfully, Plan Agent performs one final pass:

1. Write **"What Was Done"** summary section in `overview.md` (one paragraph)
2. Mark all tasks as done in Task List
3. Delete all `task-N.md` and `task-N.done.md` files — `overview.md` is the
   only remaining plan artifact
4. Compress `overview.md` if needed — remove working notes, keep What, How,
   Architecture Decisions, Protected Areas, Task List, What Was Done
5. Commit `overview.md` alone with message: `plan complete: <plan-id>`

The branch then contains:
- All incremental code commits (one per task, with descriptive commit messages)
- One final `overview.md` as the permanent human-readable audit record
- Full detailed history available via `git log plan/{plan-id}`

---

## Branching Convention

| Branch Name                             | Purpose                            |
|-----------------------------------------|------------------------------------|
| `plan/{plan-id}`                        | Main feature branch for the plan   |
| `debug/plan-{id}-task-{n}-attempt-{x}` | Failed attempt preservation        |

Debug branches are deleted after a successful retry.
Only the last failed attempt branch is kept on escalation.

---

## Agent Modes

### Interactive Mode
The developer is present and available throughout the run.

- Plan Agent **pauses at every inter-task checkpoint**
- Developer can review the completed increment, inspect the diff, and inject
  adjustments before the next task loads
- Developer owns all changes to **What** sections (overview and tasks)
- Plan Agent may propose **How** revisions; developer approves
- On task failure: escalate immediately to developer before any retry
- Protected area touched: hard stop, escalate to human

### Autonomous Mode
The developer is away and will review the result at the end.

- Plan Agent continues silently through checkpoints
- Plan Agent may revise **How** sections based on failure evidence or checkpoint info
- Plan Agent **never changes What sections** — those belong to the developer
- On task failure: Plan Agent triages, self-revises at correct layer, retries up to 3 times
- On each retry: old debug branch deleted, working branch reset to last good commit
- Only the **last** failed attempt is preserved in a debug branch for human review
- Protected area touched: hard stop, escalate to human (same as interactive)

---

## Constraints & Settings Reference

| Setting | Value |
|---|---|
| Mode | `interactive` or `autonomous` (per plan) |
| Plan Agent temperature | 0.8 |
| Dev Agent temperature | 0.2 |
| top_p | Not set — left to provider defaults |
| Max retries per task | 3 per triage cycle |
| Verification timeout | 5 minutes |
| Task target size | ≤ 40% of token window |
| Compression trigger | ≥ 70% of token window |
| Dev Agent context | `overview.md` + current task MD only |
| Task ordering | Linear; enforced by Plan Agent at creation |
| Protected areas | Component-level; always escalate to human |
| Plan storage | `.plan/` folder inside the feature branch |
| Parallelism | Plan-level only — never multiple Dev Agents on one plan |
| What sections | Immutable by Plan Agent; human-owned |
| How sections | Plan Agent may revise; cascade after arch change |
| Architecture section | Append-only; never overwritten |
| Cascade review scope | Pending tasks only; done tasks are immutable |
| History | Git log only — no duplication in documents |
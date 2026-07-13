# Plan Agent & Dev Agent — System Design

## Current Implementation: Simple Handoff Model

### Overview

The plan/dev agent system uses a **two-phase, user-controlled workflow** driven by a generic
handoff mechanism (`AiAgent.handoverTo()`), currently triggered by a manual button.

```mermaid
graph LR
    A[User] -->|send| B(AiPlanAgent)
    B -->|planSave → peon-plan/overview.md| C{Handoff button}
    C -->|click "Handoff → Peon-Dev"| D(AiDevAgent)
```

### Flow

1. **Planning phase**: `AiPlanAgent` (name `Peon-Plan`, read-only — edit tools filtered out) reads
   the project context and calls `planSave` to write `peon-plan/overview.md`.
2. **Handoff decision** — governed by `AiAgent.handoverTo()`:
   - **Manual mode (current)**: the action bar shows a **Handoff → Peon-Dev** button
     (`ActionsBarWidget`, wired to `PeonAiService.onHandoff()`). Clicking it clears the target
     agent and seeds it with the saved `peon-plan/overview.md` (or the planner's last AI message
     if no plan file exists), prefixed with `Handover from Peon-Plan`.
   - **Autonomous mode (WIP)**: not wired yet (`AIChatView`: `autonomous = false // TODO`). The
     target is a *generic* handoff for every agent: the agent signals completion via a tool call
     and the run continues automatically to `handoverTo()`. This will replace the legacy
     `AgentModeService` orchestrator, which is slated for removal.
3. **Implementation phase**: `AiDevAgent` (name `Peon-Dev`) receives the plan and implements it;
   `planImplemented` archives the plan to `peon-plan/overview-done-<timestamp>.md`.

> The plan is managed by `PlanTool` (`planRead`, `planSave`, `planUpdate`, `planImplemented`),
> registered globally in `ToolService` — not gated behind a separate "Agent mode" anymore.

### Key Components

| Class | Location | Role |
|-------|----------|------|
| `AiPlanAgent` | core/agent/ | Built-in planning agent; hardcoded to handover to Peon-Dev via `handoverTo()` → returns `AiDevAgent.NAME`. Not configurable via AGENT.md. |
| `AiDevAgent` | core/agent/ | Built-in implementation agent; receives plan context on handoff. |
| `CustomAgent` | core/agent/ | User-defined agents with optional `handover: some-agent-name` frontmatter field.

### Handoff Mechanics (from code)

```java
// AiPlanAgent.handoverTo() — hardcoded target
@Override public String handoverTo() {
    return AiDevAgent.NAME;  // always "peon-dev"
}
```

**When a plan exists**: Dev agent receives the saved `peon-plan/overview.md` content as context.
**When no plan**: Only the last AI message from planning phase is transferred (intentional — avoids bloating dev context with full conversation history).

### Temperature Settings

Both built-in agents currently read `LlmConfig.devTemperature` (see `AiPlanAgent.getTemperature()`
and `AiDevAgent.getTemperature()`). Custom agents can override it per-agent via the
`temperature:` frontmatter field.

---

## PLANNED FEATURES (not yet implemented)

The following pipeline features are documented as future work:

- **Multi-stage planning** (What → Architecture → How) with human checkpoints at each stage
- **Feature branch task files** (`task-N.md`, `.done.md`) for tracking increments
- **Cascade review on failure**: automatic re-evaluation of pending tasks when a plan decision proves wrong
- **Protected areas enforcement**: components the Dev Agent must never modify
- **Commit protocol per task**: alternating dev/plan commits as acceptance stamps
- **Verification timeouts**: configurable wait period before escalating failed tests
- **Debug branches for retries** (`debug/plan-{id}-task-{n}-attempt-{x}`)

---


# Plan Agent & Dev Agent — System Design

## Current Implementation: Simple Handoff Model

### Overview

The plan/dev agent system uses a **two-phase, user-controlled workflow** with optional manual handoff.

```mermaid
graph LR
    A[User] -->|send| B(AiPlanAgent)
    B -->|saves plan.md| C{handoff button}
    C -->|click "Give Peon-Dev"| D(AiDevAgent)
```

### Flow

1. **Planning phase**: `AiPlanAgent` reads the project context and produces a structured plan in memory or saved to `plan.md`. Temperature: 0.3.
2. **Handoff decision**:
   - **Manual mode (default)**: A "Give Peon-Dev" button appears when the planning agent's work is complete. User clicks it → control transfers with context (last AI message + plan if saved).
   - **Autonomous mode (checkbox in UI, currently disabled)**: If enabled, handoff happens automatically after plan save.
3. **Implementation phase**: `AiDevAgent` receives the plan and implements it.

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

**When a plan exists**: Dev agent receives the saved `plan.md` content as context.
**When no plan**: Only the last AI message from planning phase is transferred (intentional — avoids bloating dev context with full conversation history).

### Temperature Settings

| Agent | Temperature |
|-------|-------------|
| AiPlanAgent | 0.3 — deterministic for reliable structuring |
| AiDevAgent | Configurable via `temperature:` frontmatter; defaults to global setting |

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


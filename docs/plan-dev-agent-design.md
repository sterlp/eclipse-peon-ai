# Plan Agent & Dev Agent — System Design

## Current Implementation: Simple Handoff Model

### Overview

The plan/dev agent system uses a **two-phase, user-controlled workflow** with optional manual handoff.

> An **orchestration layer** on top of this flow is designed in [Peon-PO (Jon)](po-agent-jon.md): a
> docs-owning agent that drives its own Plan/Dev instances via `jon*` tools with `planComplete` /
> `planImplemented` completion signals. The one-shot handoff described here stays the standalone path.

```mermaid
graph LR
    A[User] -->|send| B(AiPlanAgent)
    B -->|saves plan.md| C{handoff button}
    C -->|click "Give Peon-Dev"| D(AiDevAgent)
```

### Flow

1. **Planning phase**: `AiPlanAgent` reads the project context and produces a structured plan in memory or saved to `plan.md`. Temperature: configurable via the plan temperature preference.
2. **Handoff decision**:
   - **Manual mode (default)**: A "Give Peon-Dev" button appears when the planning agent's work is complete. User clicks it → control transfers with context (last AI message + plan if saved).
3. **Implementation phase**: `AiDevAgent` receives the plan and implements it.

### Key Components

| Class | Location | Role |
|-------|----------|------|
| `AiPlanAgent` | core/agent/ | Built-in planning agent; hardcoded to handover to Peon-Dev via `handoverTo()` → returns `AiDevAgent.NAME`. Not configurable via AGENT.md. |
| `AiDevAgent` | core/agent/ | Built-in implementation agent; receives plan context on handoff. |
| `CustomAgent` | core/agent/ | User-defined agents with optional `handover: some-agent-name` frontmatter field.

### Handoff Mechanics

```java
// AiPlanAgent.handoverTo() — hardcoded target
@Override public String handoverTo() {
    return AiDevAgent.NAME;  // always "peon-dev"
}
```

**When a plan exists:**
1. The **plan file path with handover instruction** is added to standing orders (governs dev agent's first turn). ✅
2. The **plan content** itself is added as a `UserMessage` ("chat") to the new handover agent's memory.

Standing orders ensure the plan path + "Handover from Peon-Plan" directive survive any compaction in the dev agent's first turn, while the full plan content provides detailed implementation guidance as payload.

**When no plan**: Only the last AI message from planning phase is transferred (intentional — avoids bloating dev context with full conversation history).

### Temperature Settings

| Agent | Temperature |
|-------|-------------|
| AiPlanAgent | Configurable via plan temperature preference |
| AiDevAgent | Configurable via dev temperature preference |

---

### Plan Tools — `planImplemented` (PlanTool)

`planImplemented` archiviert `peon-plan/overview.md` als `overview-done-<timestamp>.md`
(Timestamp `yyyy-MM-dd-HH-mm`, **Minute**-Granularität).

**R-PI1 — Kein Kollisions-Fehler beim Archivieren ✅ (2026-09-05)**
Zwei Archivierungen in derselben Minute → gleicher Dateiname → `IResource.move` schlägt fehl
("already exists").

- **GIVEN** ein Plan ist gerade archiviert (z.B. `overview-done-2026-09-05-12-59.md`) **WHEN** `planImplemented` wird erneut in derselben Minute aufgerufen **THEN** der neue Archiv-Name erhält einen Counter-Suffix (`…-12-59-1.md`, `…-12-59-2.md`, …) bis ein freier Name gefunden ist — **nie** ein "already exists"-Fehler.
- **Tag:** unit (verify planImplemented appends counter on collision, never throws "already exists")

> **Test-Einheit (2026-09-05):** Kollisions-Logik als pure core `ArchiveName.firstFreeName(stem, exists)`
> getestet (4 Tests: frei / `-1` / `-2` / springt zu `-4`); `PlanTool.planImplemented` ist ein dünner
> OSGi-Adapter (move nur auf den garantierten freien Namen) — OSGi-`exists` ist im core nicht testbar.

**WEIL:** Im Bug-Fix-Zyklus werden Pläne schnell hintereinander archiviert — Kollisionen sind real
(2× in einer Session beobachtet). Ein harter Fehler bricht den Dev-Agent-Flow.

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


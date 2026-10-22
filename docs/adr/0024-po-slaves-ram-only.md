# ADR-0024: Peon-PO slaves are RAM-only; Jon is durable; the durable handoff is the plan file

**Status:** Accepted

## Context

Peon-PO (Jon) drives his **own** Peon-Plan and Peon-Dev instances as Da Thinka and Da Mek (see
[po-agent-jon.md](../../org.sterl.llmpeon.core/docs/po-agent-jon.md), Increment 2). Every persisted agent in Peon writes a
`FileAgentHistoryStore` (one JSONL per agent `NAME`, [ADR-0019](0019-jsonl-agent-history-store.md)).
Naively giving Da Thinka and Da Mek the same treatment raises two problems:

- **Clobber.** Da Thinka and Da Mek reuse the standalone `AiPlanAgent` / `AiDevAgent` `NAME`s, so a shared history
  file would collide with the user-selectable Plan/Dev agents' own history.
- **Persistence semantics.** An agent's chat is *transient reasoning in service of Jon* — it is not a
  user-facing conversation worth restoring across restarts. Persisting it as JSON blurs that boundary
  and leaks Jon's internal delegation into the on-disk state.

## Decision

**Jon persists; Da Thinka and Da Mek do not.**

- **Jon** keeps his durable `FileAgentHistoryStore` (3-arg constructor) — his state survives restarts.
- **Slaves** are built with the **2-arg** `AiPlanAgent` / `AiDevAgent` constructor: a plain in-memory
  `ThreadSafeMemory`, **no** `FileAgentHistoryStore`, **no JSONL / no JSON of any kind**. They are lazy
  persistent singletons **in RAM** for the duration of a Jon session and reset on app restart.
- The **durable handoff artefact is the plan file** `peon-plan/overview.md` (written by Da Thinka's
  `PlanTool`). Jon reviews it and passes its **path** to Dev. Because the durable state lives in the file
  plus Jon's own persisted memory, losing their RAM context on restart is recoverable: Jon
  re-dispatches from the plan file.

## Consequences

- The shared-`NAME` clobber concern disappears **by construction** — there is no agent history file at
  all (supersedes the earlier "distinct history files" idea in R9).
- App restart drops in-flight agent context; acceptable because the plan file + Jon's state are the
  durable record. A persistent-agent variant is explicitly a **later** option ("first step").
- Agent wiring stays layer-injected via the agent factory: core tests inject disk-tool RAM agents, the
  plugin injects Eclipse-workspace-tool RAM agents — Jon-in-core remains testable headless.

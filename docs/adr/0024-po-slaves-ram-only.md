# ADR-0024: Peon-PO slaves are RAM-only; Jon is durable; the durable handoff is the plan file

**Status:** Accepted

## Context

Peon-PO (Jon) drives his **own** Peon-Plan and Peon-Dev instances as slaves (see
[po-agent-jon.md](../po-agent-jon.md), Increment 2). Every persisted agent in Peon writes a
`FileAgentHistoryStore` (one JSONL per agent `NAME`, [ADR-0019](0019-jsonl-agent-history-store.md)).
Naively giving the slaves the same treatment raises two problems:

- **Clobber.** The slaves reuse the standalone `AiPlanAgent` / `AiDevAgent` `NAME`s, so a shared history
  file would collide with the user-selectable Plan/Dev agents' own history.
- **Persistence semantics.** A slave's chat is *transient reasoning in service of Jon* — it is not a
  user-facing conversation worth restoring across restarts. Persisting it as JSON blurs that boundary
  and leaks Jon's internal delegation into the on-disk state.

## Decision

**Jon persists; his slaves do not.**

- **Jon** keeps his durable `FileAgentHistoryStore` (3-arg constructor) — his state survives restarts.
- **Slaves** are built with the **2-arg** `AiPlanAgent` / `AiDevAgent` constructor: a plain in-memory
  `ThreadSafeMemory`, **no** `FileAgentHistoryStore`, **no JSONL / no JSON of any kind**. They are lazy
  persistent singletons **in RAM** for the duration of a Jon session and reset on app restart.
- The **durable handoff artefact is the plan file** `peon-plan/overview.md` (written by the Plan slave's
  `PlanTool`). Jon reviews it and passes its **path** to Dev. Because the durable state lives in the file
  plus Jon's own persisted memory, losing the slaves' RAM context on restart is recoverable: Jon
  re-dispatches from the plan file.

## Consequences

- The shared-`NAME` clobber concern disappears **by construction** — there is no slave history file at
  all (supersedes the earlier "distinct history files" idea in R9).
- App restart drops in-flight slave context; acceptable because the plan file + Jon's state are the
  durable record. A persistent-slave variant is explicitly a **later** option ("first step").
- Slave wiring stays layer-injected via the slave factory: core tests inject disk-tool RAM slaves, the
  plugin injects Eclipse-workspace-tool RAM slaves — Jon-in-core remains testable headless.

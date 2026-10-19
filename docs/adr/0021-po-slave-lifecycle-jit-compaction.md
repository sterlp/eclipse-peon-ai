# ADR-0021: Peon-PO slave lifecycle & just-in-time compaction

**Status** · Accepted

## Context
Jon's team members (Peon-Plan "Da Thinka", Peon-Dev "Da Mek") carry a real conversation across many
delegate turns, so they must **keep their context** between calls — unlike the stateless one-shot
SearchAgent. Long-running team members will eventually overflow their window, but compacting eagerly
(or on every turn) wastes tokens and can drop the very instruction Jon is about to send. Team members
should also run leaner than Jon himself: they are throw-away workers, he is the durable one.

## Decision
- **Eager shared singletons, RAM-only:** the two team members are created once and reused for the whole Jon
  session, holding their in-RAM context across calls; they use no history file (see
  [ADR-0024](0024-po-slaves-ram-only.md)). This supersedes the original "lazy, own-history-file"
  sketch — the durable hand-off is the plan file, not the team member's memory.
- **Team members run leaner by a delegating-tool rule, not by an agent knob:** before every delegation
  Jon's tool compactes the slave when its counter is over **70 %** of the shared budget
  (`PoDelegateTool.compactSlaveBeforeDelegation`, `SLAVE_COMPACT_FACTOR = 0.7`). The agent itself knows
  nothing about being a slave. Details: [ADR-0071](0071-compact-policy-owned-by-delegation.md).
- **One scale in the tool loop:** after every tool round the compact decision compares against the **raw**
  `LlmConfig.autoCompactAfter` — direct compact above it, hint above 95 % of it
  (`HandleToolLoopCompactComponent`). No pre-turn gate, no per-agent factor
  ([ADR-0070](0070-compact-decision-in-one-component.md) as amended by 0071).
- **Standing-order survival:** Jon's outgoing message is delivered to the team member as a **standing order**
  (like a `/` command), so it survives compaction and is inserted **before** the compact result — the
  team member never compacts away the instruction it is about to act on (see
  [ADR-0010](0010-standing-orders-setactiveagent-hook.md)).

## Consequences
- Exactly two long-lived team member contexts per Jon session; the header shows each with its live token count
  and a status ball ([ADR-0025](0025-po-status-widget-named-agents.md)).
- `0.7` lives as one constant in `PoDelegateTool`; it is expected to need tuning and is easy to move.
- Display and decision use the **same** scale for every agent — raw `autoCompactAfter`
  (`tokenContextUsedInPercent` was already raw).
- `AbstractAgent.tokenContextUsedInPercent()` still caps its denominator at `min(autoCompactAfter,
  4000)` — a pre-existing UI-percent oddity, left untouched; it does not feed the compaction trigger.
- Reuses the existing standing-orders mechanism and compaction tooling rather than inventing a new
  pre-compaction channel.
- **The 0.7 rule now runs per delegation, not per turn.** That is intentional: every new task starts on a
  lean throw-away context, even in the middle of a long cycle. Since the slaves are reached only through
  `PoDelegateTool.dispatch()`, their queued follow-ups still pass the rule; only direct/headless calls
  rely on the in-loop hint (> 95 %).
- **The trigger measures fill level, not relevance.** A topic switch (new plan, unrelated task) leaves
  a team member well below 70 % while its whole context is now ballast. That case stays Jon's manual
  call via the delegate tools: `compactPlan`/`compactDev` when the same task continues but the history
  grew long, `resetPlan`/`resetDev` when the next task is unrelated and the old state would only create
  drift.

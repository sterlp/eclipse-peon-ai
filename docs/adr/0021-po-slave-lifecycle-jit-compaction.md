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
- **Per-agent compaction budget via `compactFactor`:** `AbstractAgent` carries a `compactFactor`
  (double, clamped to `(0,1]`, **default `1.0` = the full shared budget**). `compactAfterTokens()`
  returns `round(LlmConfig.autoCompactAfter × compactFactor)`. Jon's team members are constructed with
  **`compactFactor = 0.7`** (the `AiPlanAgent`/`AiDevAgent` 3-arg ctor; wired in `PeonAiService` as
  `SLAVE_COMPACT_FACTOR`), so they auto-compact at 70 % of the budget while every other agent stays at
  100 %. The factor scales with the shared budget rather than pinning an absolute token count.
- **Hard trigger only:** `compactAfterTokens()` gates the **pre-turn** auto-compaction in
  `AbstractAgent.doCall` (compact before starting a turn once the context exceeds the budget). This is
  the single place the per-agent factor takes effect.
- **Jon opts out of the hard trigger entirely (`isAutoCompact()` → `false`).** `AbstractAgent` carries an
  `isAutoCompact()` flag (default `true`); `AiPoAgent` overrides it to `false`, so the pre-turn guard in
  `doCall` never force-compacts Jon. He owns the docs *and* the shared memory, so he must keep the turn in
  which the soft 95 % hint reaches him — that turn is his chance to persist to `memory.md` (and the
  cross-session `memory*` tools) and *then* self-compact via `compactSession` on his own terms. The team members
  keep the hard trigger (they are throw-away workers); the per-task `compactSession` discipline Jon hands
  Da Mek (Peon-Dev) with the plan (`dev-build-loop.txt`) is unaffected. Accepted trade-off: if Jon ignores the
  hint his context can grow to the real model limit — that is his call as "the boss".
- **The 95 % in-loop hint stays global — deliberately unchanged.** While a team member *works*, its context
  grows within one turn; `ToolService.addCompactHintIfNeeded` nudges it to self-compact
  (`compactSession`) at 95 % of the budget. A working team member needs that hint (and the compact tools) and
  keeps them. We intentionally leave that hint keyed to the **global** `LlmConfig.autoCompactAfter`, not
  to the per-agent factor: the pre-turn hard trigger already resets each turn to a lean starting point,
  a single turn rarely grows the whole remaining budget, and threading per-agent state through
  `ToolLoopRequest` for that marginal gain is not worth the added coupling.
- **Standing-order survival:** Jon's outgoing message is delivered to the team member as a **standing order**
  (like a `/` command), so it survives compaction and is inserted **before** the compact result — the
  team member never compacts away the instruction it is about to act on (see
  [ADR-0010](0010-standing-orders-setactiveagent-hook.md)).

## Consequences
- Exactly two long-lived team member contexts per Jon session; the header shows each with its live token count
  and a status ball ([ADR-0025](0025-po-status-widget-named-agents.md)).
- `compactFactor` is one clamped constructor knob; `0.7` is expected to need tuning and is easy to move.
- The pre-turn trigger and the in-loop hint now use **different** bases for team members (hard trigger at
  `0.7 × budget`, hint at `0.95 × budget`). That is accepted: the hint firing later than the hard
  trigger for a team member is harmless because the hard trigger keeps turn-start lean.
- `AbstractAgent.tokenContextUsedInPercent()` still caps its denominator at `min(autoCompactAfter,
  4000)` — a pre-existing UI-percent oddity, left untouched; it does not feed the compaction trigger.
- Reuses the existing standing-orders mechanism and compaction tooling rather than inventing a new
  pre-compaction channel.
- **The trigger measures fill level, not relevance.** A topic switch (new plan, unrelated task) leaves
  a team member well below 70 % while its whole context is now ballast. That case stays Jon's manual
  call via the delegate tools: `compactPlan`/`compactDev` when the same task continues but the history
  grew long, `resetPlan`/`resetDev` when the next task is unrelated and the old state would only create
  drift. Deliberately **not** automated in `PoDelegateTool` — a second, tool-side trigger would compact
  mid-build (increment 3 of 5) for no gain.

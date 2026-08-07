# ADR-0020: Peon-PO orchestrates Plan/Dev as sub-agents with `planComplete` / `planImplemented` completion signals

**Status** · Proposed — **superseded** by the shipped design in
[po-agent-jon.md](../po-agent-jon.md) (§I2.x) and [ADR-0024](0024-po-slaves-ram-only.md) /
[ADR-0021](0021-po-slave-lifecycle-jit-compaction.md). The tool set below (`jonCreateDevPlan`,
`jonAskQuestion`, `jonAskDev`, `jonAskScaffold` + `planComplete`/`planImplemented` latches) was **not
built as written**: it became four intent-named delegate tools — `talkPlan`, `planWithPlanAgent`,
`askDev`, `buildWithAgent` — plus `searchAgent`, with the Dev slave owning `planImplemented` and Jon
deciding done-ness from the reply text. This ADR is kept as the original proposal record.

## Context
The current plan→dev flow ([plan-dev-agent-design.md](../plan-dev-agent-design.md)) is a **one-shot
handoff**: the plan agent finishes, a "Give Peon-Dev" button transfers context once, and the dev
agent runs independently. There is no agent that stays in control across the whole feature and reviews
the intermediate results.

Peon-PO (Jon) needs the opposite: a **parent agent** that dispatches work to Plan and Dev, receives
their replies, answers back, and regains control at a defined point to review — without the user in
the loop for every sub-step.

## Decision
Jon talks to his slaves through **`jon*` tools whose result is the slave's reply** — `jonCreateDevPlan`
and `jonAskQuestion` (both driving the **same** Plan slave), `jonAskDev` and `jonAskScaffold`. A `jon*`
call runs the target slave for one turn and returns its output as the tool result; the slave's
clarifying questions therefore reach Jon, who answers with the next call. Jon stands in for the user
during the slave's interview.

The two Plan tools share one persistent Plan slave (warm context) but differ by intent:
`jonCreateDevPlan` runs the planning workflow and ends when the Plan slave calls `planComplete()`;
`jonAskQuestion` wraps the
prompt as `Question: <text>. Just directly respond.` so the plan-oriented agent answers a one-off
question directly instead of starting an interview — complementing the stateless `SearchAgentTool`.

The slave's turn already ends by **natural stop** (`ToolService.executeLoop` breaks on a plain-text
response — there is no terminal tool), so control returns to Jon on every turn. What Jon needs is an
**atomic completion marker** to tell "done" from "clarifying question". Two **core** signals provide it,
authored fresh as **pure signal tools** (no file I/O — the Eclipse `PlanTool` is IFile-bound and stays
untouched): **`planComplete()`** (Plan slave: plan ready, carries the plan link) and
**`planImplemented()`** (Dev slave: implementation done). `planComplete` is a pure latch-setter (plan
file stays — Dev needs it); `planImplemented` is **atomic** — it archives/renames the plan to a *done*
name **and** sets the latch, via an injected **`PlanArchiver` port** (disk impl in core/tests, IFile
impl in the Eclipse plugin). The rename is **deterministic code, not a second LLM tool call**, so the
active-plan slot is reliably freed for the next session. Jon passes the tool name + "call it when
finished" in the dispatch prompt / standing order; the signal sets a **consume-once latch**
(`Optional<CompletionInfo>`). After `slave.call()` returns, the `jonAsk*` tool reads the latch, surfaces
"done + plan link" to Jon (tool result + OK/done chat message) and clears it to `Optional.empty()`. The
slaves receive Jon's context — foremost the plan link — via a **standing order** that **Jon sets
himself** through the core `AiAgent.setUserContextInformations` hook (not the Eclipse-only
`onHandoff`/`JdtUtil` path, unavailable in core); his actual prompt arrives as a normal chat message.
Jon's *own* standing orders (his `AGENTS-PO.md`) are **not** forwarded to the slaves.

**Per-agent filtering is part of the design:** the Plan slave sees only `planComplete`, the Dev slave
only `planImplemented`, **Jon no plan tools at all**, and the standalone Peon-Plan/Peon-Dev neither.

The MVP has **no input lock**: while a `jonAsk*` loop runs, Send/Mic stay active and the user's
messages are parked in Jon's existing `AbstractAgent` **message queue** (batched, compaction-safe) and
consumed FIFO at Jon's next turn — reusing the queued-user-messages feature rather than disabling input.
Stop remains the abort path ([ADR-0018](0018-abort-path-parity.md)) and drains the queue to memory.

**Grounding (existing code the design reuses):**
- `jonAsk*` runs the slave via its own `AbstractAgent.call(prompt, monitor)` — reusing memory, the
  `working` queue, auto-compact and standing orders — rather than re-implementing `executeLoop` like
  `SearchAgentTool` does. `SearchAgentTool` is the pattern (sub-agent → text tool result) but is
  one-shot; Jon's slaves are stateful (see [ADR-0021](0021-po-slave-lifecycle-jit-compaction.md)).
- Slaves are **dedicated, Jon-owned instances**, not `AgentService`'s user-selectable Peon-Plan/
  Peon-Dev — otherwise `jonAsk*` mutates the user's memory/history and the shared `working` guard
  silently queues the nested `call()`.
- **Escalation = end turn, not blocking wait.** When Jon cannot answer a slave's question he ends his
  own turn and asks the user; the persistent slave is resumed later. This is what keeps the synchronous
  loop from deadlocking against the "input disabled while working" rule.

## Consequences
- Requires an "agent-as-tool" capability: a tool that drives another `AiAgent` for one turn and
  surfaces its output — new relative to today's button/`handoverTo()` handoff (`PeonAiService.onHandoff`).
- `planComplete` / `planImplemented` are offered **only** on Jon's dedicated slave instances, each to
  exactly one slave (Plan resp. Dev), never on the standalone agents. `ToolService.addTool` **throws on
  duplicate names**, and per-turn tool-set changes break the KV-cache, so tool filters stay **static per
  agent instance**.
- `jonAsk*` must check `monitor.isCanceled()` after the slave returns and drop the result on Stop —
  ADR-0018's "no tool result on abort" only holds at model-turn granularity, not for a sub-agent nested
  inside a tool.
- Jon's `jon*` tools are **"agent tools"** that deviate from the normal tool error contract. We do
  **not** touch `ToolService` / `SmartToolExecutor` (whose `IllegalArgumentException` path reports an AI
  error via `onProblem` and returns it, while an unexpected system error escapes to the UI). Because
  **Jon is the UI** when he drives a slave, the `jon*` tools **catch every exception themselves**, report
  it via `onProblem` and **return it to Jon** as the tool result (caught message + root cause + first ~5
  stack lines) — the default error handler never fires for them, and since the tool always returns there
  is no dangling `tool_use` by construction. Jon then **summarises the failure to the user and does not
  silently retry** (guardrail standing order; a `tool_result` always triggers exactly one follow-up
  turn). Only **Jon's own** model-call failure uses ADR-0018's abort/drain path to the real UI.
  Recoverable-error handling / retries (incl. a "did the slave make progress" flag) are a future step.
- Jon owns the review step (larger plans: delegated back to the Plan agent for a gap analysis); a
  dedicated Reviewer agent is a documented future extension.
- Jon gets **no bespoke file tools**: he reuses the existing Eclipse/disk write tools behind a
  write-path-allowlist decorator ([ADR-0022](0022-write-path-allowlist-decorator.md)); the `jon*` tools
  above are the only new ones on his side.
- Keeps the standalone plan→dev flow untouched — Peon-PO is a **100 % additive** orchestration layer.
- Jon lives in **core** and is fully testable there (headless disk tools); the Eclipse plugin only
  **injects the Eclipse-workspace tools** (behind the write wrapper, [ADR-0022](0022-write-path-allowlist-decorator.md)).
  Jon never gets a shell in any layer.

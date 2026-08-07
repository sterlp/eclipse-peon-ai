# Peon-PO (Jon) — Implementation Plan (coarse)

## Increment status

- **Increment 1 — "Jon exists and can author docs" — IMPLEMENTED (2026-08-06).**
  Plan: [increment-1-jon-docs.md](increment-1-jon-docs.md). Shipped: per-agent `WriteValidator`
  (`ALLOW_ALL` default on the `AiAgent` interface, `DOCS = */docs/* + *.md`), enforced via
  `AbstractTool.validateWrite` in `DiskFileWriteTool.resolve` + every `EclipseWorkspaceWriteFileTool`
  write method; `AiPoAgent` ("Peon-PO", `po.txt` prompt) wired in `PeonAiService` with an own curated
  Eclipse read/write/grep `ToolService`. Tests: 325 core green + `EclipseWriteValidatorUnitTest` (2) green.
  **Remaining:** manual Eclipse smoke (select Peon-PO → write `docs/*.md` ok, `src/*` denied, reads ok);
  prompt tuning with the user's prompts; optional first-activation tutorial (Task 8, deferred).
- Deferred to later increments: `jon*` slave orchestration, completion signals, JIT compaction,
  configurable allowlist (R1–R14 below).

## Context

**Goal:** Build **Peon-PO (Jon)**, a docs-owning business-owner agent that designs features in `docs/`
and orchestrates his **own** Peon-Plan / Peon-Dev instances via `jon*` tools. Design is complete and
frozen in [docs/po-agent-jon.md](../docs/po-agent-jon.md) (rules R1–R14) with
[ADR-0020](../docs/adr/0020-po-agent-orchestration.md),
[ADR-0021](../docs/adr/0021-po-slave-lifecycle-jit-compaction.md),
[ADR-0022](../docs/adr/0022-write-path-allowlist-decorator.md).

**Scope guardrails:** 100 % additive — nothing in the standalone Peon-Plan/Dev/Scaffold or today's
button handoff changes. Jon lives in **`core`** and is fully unit-testable there (headless disk tools);
the Eclipse plugin only **injects** the Eclipse-workspace tools + wires the AGENTS-PO.md load. **No
shell in any layer.** We do **not** modify `ToolService` / `SmartToolExecutor`.

> **Status:** coarse plan — captures all goals & changes. Next round: **task splitting** (each work item
> below → ordered, testable slices with BDD from the story). Context budget was the reason to checkpoint
> here.

## Goals & Changes (mapped to the story rules)

### A. Agent foundation (core) — R1, R2, R4, R5
- New package `org.sterl.llmpeon.po`, class `AiPoAgent extends AbstractAgent`, name `Peon-PO`, identity
  `Jon`. Register via `addPersistentAgent()`; default active agent stays Peon-Dev.
- Own `ToolService(false)` holding: `jonCreateDevPlan`, `jonAskQuestion`, `jonAskDev`, `SearchAgentTool`
  + injected write/read file tools (write tools behind the R3 decorator). No plan*/compact/shell tools.
- System prompt = Jon's identity + guardrails (skeptical docs guardian; SOLL vs IST; design→approval
  gate R5; **error guardrail R14**: on a technical `jon*` failure, summarise to user, do not retry).
- First-activation tutorial (R4, like Peon-Scaffold).

### B. Write-path allowlist decorator (core + config) — R3, ADR-0022
- Decorator wrapping the existing disk/Eclipse write tools; matches target path against a
  comma-separated glob list. User-editable config field, preloaded `*/docs/*`. Semantics: `*/docs/*`
  (any depth, project-root-relative in Eclipse), `docs/*` (root only), `*.md`. Reads not gated.

### C. `jon*` agent tools (core) — R6, R7, R14
- `jonCreateDevPlan` / `jonAskQuestion` (drive the one Plan slave); `jonAskDev` (drive the Dev slave).
  Each runs the slave one turn via `slave.call(prompt, Jon's monitor)` (R11 shared monitor/cancel).
- **Agent-tool error contract (R14a):** each `jon*` tool catches **every** exception itself → reports
  via `monitor.onProblem(...)` **and** returns it to Jon as tool result (caught msg + root cause + first
  ~5 stack lines + "state may have changed"). Never throws → no dangling tool_use; default handler never
  fires. After `slave.call` returns, drop result if `monitor.isCanceled()` (R11 abort-mid-tool).
- Jon's **own standing-order logic (R7):** on each dispatch, set the slave's context (plan link) via
  `setUserContextInformations`; prompt rides as chat message. Jon does NOT forward his own AGENTS-PO.md.

### D. Completion signals (core) — R8
- Two fresh **pure signal** tools: `planComplete()` (Plan slave — sets consume-once latch
  `Optional<CompletionInfo>` w/ plan link, no file I/O) and `planImplemented()` (Dev slave — **atomic**:
  archive/rename plan to *done* via injected `PlanArchiver` port + set latch).
- `PlanArchiver` port: disk impl (core/tests), IFile impl (plugin).
- Per-agent static tool filter: Plan slave = planComplete only; Dev slave = planImplemented only; Jon =
  none; standalone Plan/Dev = none. `jonAsk*` reads+clears latch after `slave.call`.

### E. Slave lifecycle + JIT compaction (core) — R9, R10, R11
- Lazy persistent singletons: one Jon-owned Plan slave, one Dev slave, own history files, distinct from
  `AgentService`'s user-selectable agents. Injected **slave-factory** per layer (see F).
- JIT compaction: `slave.compressContext(monitor)` before dispatch when over threshold (default 60 %,
  needs explicit base — not the `min(autoCompactAfter,4000)` fuzzy value). Jon's message as standing
  order survives compaction.
- Non-blocking work (R11): reuse existing `AbstractAgent` message queue (no screen lock); shared monitor
  = single Stop cancels Jon + running slave.

### F. Eclipse plugin wiring (plugin only) — R1, R2, R9, R12
- Register Jon in the plugin; inject **Eclipse-workspace** file tools (behind R3 decorator).
- Slave-factory that builds Plan/Dev slaves with the **plugin-built ToolService** (share it, or
  participate in `PeonAiService`'s tool-`add` step) — core stays disk-only for tests.
- AGENTS-PO.md: generic — `AgentsMdService` already maps `Peon-PO → PO` (part-after-`Peon-`,
  uppercased); only the docs row was added, no resolver change. Verify it loads for Jon.
- Header status (R12): show `peon-plan(ctx)` / `peon-dev(ctx)` with status balls (colours deferred).

## Layer split (testability)
- **core (headless, unit-tested):** AiPoAgent, jon* tools + agent-tool error contract, completion
  signals + latch + PlanArchiver(disk), slave lifecycle + JIT compaction, write-allowlist decorator,
  message-queue reuse, shared-monitor cancel.
- **plugin (Eclipse):** Jon registration, Eclipse-tool injection, slave-factory with plugin ToolService,
  PlanArchiver(IFile), AGENTS-PO.md verification, header status widget.

## Out of scope (Future Extensions in the story)
Reviewer agent · Self-improvement via Skills (`jonAskScaffold`, own story) · Cross-agent shared memory ·
Recoverable-error handling/retries + "did the slave make progress" flag · finer-grained async
resolution · generalised handover artefact · optional `tools.md` for the tool error contract.

## Next round
Split A–F into ordered, individually testable tasks (core-first, each with the story's BDD as
acceptance). Suggested order: A → B → E → C → D → F.

---

## Resume state (post-compact handoff)

**Where we are:** Design frozen (`docs/po-agent-jon.md` R1–R14 + ADR-0020/21/22, all reviewed round on
2026-08-06). This coarse plan is written. **Not committed.** Nothing implemented yet (every rule ❌).
**Immediate next action:** task-splitting of blocks A–F (no code before the user approves the split).

**Process constraints (do not violate):** never commit/push or delete tracked files unless explicitly
asked · discuss every change before applying · ask ONE open free-text question at a time (KISS) · use
IST/SOLL/WEIL for proposals · status legend ✅done/🚧WIP/❌notstarted.

### Grounded code facts (verify before asserting, but these held on 2026-08-06)
Module `org.sterl.llmpeon.core` unless noted "(plugin)".
- **Tool loop:** `ToolService.executeLoop` ends by **natural stop** (plain-text response breaks, ~`:156`);
  **no terminal tool**. Assistant `tool_use` msg + tool results committed **together** at `:152`
  (`req.getMemory().addResult(response, tR)` → `ThreadSafeMemory.addResult` `:121-130`: adds aiMessage
  then toolResults) → no dangling by construction. `runAllTools` `:187-203` has a blanket
  `catch(Exception)` at `:194` → `onProblem` + error `tool_result` w/ full `StringUtil.getStackTrace`
  (this is only the framework **backstop**; jon tools catch themselves per R14a).
- **Per-tool error contract:** `SmartToolExecutor.run` (`tool/component/SmartToolExecutor.java`) catches
  `IllegalArgumentException` (and `ToolExecutionException` caused by IAE) → `reportProblem`/`onProblem` +
  returns `e.getMessage()` (loop continues, msg truncated >200 chars). **Any other exception → rethrown.**
  This IS the "default error handler" jon tools bypass by catching everything internally.
- **Standing orders:** `AiAgent.setUserContextInformations` (impl `AbstractAgent` ~`:257`), carried on
  `ToolLoopRequest`, **re-injected after `clearMemory()`** so they survive compaction. Use this for R7
  (plan link to slaves), NOT the Eclipse `onHandoff`/`_handoffLine` (plugin `PeonAiService`, uses
  `JdtUtil.pathOf(IFile)` — unavailable in core).
- **AGENTS-*.md:** `AgentsMdService` (plugin) `resolveAgentKey` = part after `Peon-`, uppercased →
  `Peon-PO → PO` works with **no resolver change**; docs row already added in
  `docs/agent-specific-agentsmd.md`.
- **Slaves:** constructable in core (`AgentService` `new AiDevAgent(chatModel, toolService,
  historyConfigDir)`); Eclipse tools wired **only in plugin** (`PeonAiService`: `sharedToolService = new
  ToolService()` + `EclipseWorkspaceWriteFileTool` …). R9 slave-factory must inject the plugin ToolService
  (share it or join the tool-`add` step).
- **History:** core `FileAgentHistoryStore` (java.nio); `AbstractAgent.historyFile()` →
  `configDir/state/<safeName>-history.jsonl`. Jon slaves need their **own** history files.
- **Monitor:** on `ToolLoopRequest`; injected via `SmartToolExecutor`/`AbstractTool`; precedent
  `CompactSessionTool` calls `agent.call(.., monitor)`. R11 = pass Jon's own monitor into
  `slave.call(prompt, monitor)` (one Stop cancels the chain).
- **Queue:** `AbstractAgent.messageQueue` (`UserMessageQueue`), FIFO at turn boundary, `handleAbortAndDrain`
  `:178-185`, `AbstractAgent.call` `:154-163` catch→drain→rethrow. Send/Mic stay active (no lock).
- **Compaction:** `AbstractAgent.compressContext(monitor)`; `tokenContextUsedInPercent()` caps denominator
  at `min(autoCompactAfter, 4000)` → R10's 60 % needs its own explicit base.
- **Registration:** `addPersistentAgent()` survives `clearAgents()` (like Peon-Scaffold).
- **Legacy PlanTool (plugin):** has `planImplemented()` active (archives) + `planComplete()`/`planProblem()`
  commented out — do NOT reuse/touch; R8 signals are authored fresh in core.

### Key decisions to preserve (why)
- jon tools are **agent tools**: catch every exception, `onProblem` + return to Jon (msg + root cause +
  first ~5 stack lines + "state changed"), never throw → **Jon is the UI**, so errors land at Jon not the
  real UI. We do NOT modify ToolService/SmartToolExecutor.
- Two plan tools, **one warm Plan slave**; `jonAskQuestion` = `Question: <text>. Just directly respond.`
- Completion = two atomic **pure-signal** tools + **consume-once latch** `Optional<CompletionInfo>`;
  `planImplemented` archives via injected `PlanArchiver` port (disk core / IFile plugin) — rename is
  deterministic code, not a 2nd LLM call. Static per-agent tool filter (KV-cache safe).
- R11 no screen-lock (reuse queue); shared monitor; slave streaming surfacing in Jon's chat is accepted.
- Removed from MVP → Future: `jonAskScaffold` (self-improvement, own story), Reviewer, cross-agent shared
  memory, retries + "did slave make progress" flag, finer-grained async, generalised handover, `tools.md`.

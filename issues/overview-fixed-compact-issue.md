# Plan: Fix stale compact token-counter + AGENTS-<agent>.md for PO slaves

## Status (dev, 2026-08-29)

- **inc-1 done, green** — `ThreadSafeMemory.reevaluateTokens()`, `ToolService` re-evaluation after a
  compactSession in the tool batch, `ThreadSafeMemoryTest` + 2 `AiDeveloperAgentTest` regression tests.
  Core: 417/417.
- **inc-2 done, green** — `compactSessionFallback` removed (`agent == null` → `IllegalStateException`),
  `ToolLoopRequest.standingOrders`/`clearMemory()` removed, `CompactSessionToolTest` reworked. Core: 414/414.
- **inc-3 done, green** — `PoDelegateTool` `Function<NamedAgent, List<ContextItem>>` ctor,
  `BuildPoAgentComponent` uses `AgentsMdContextItem.itemsFor(...)`, `PoDelegateToolTest` adapted,
  `PeonAiServiceTest.test_slaves_getAgentSpecificMdInTurnContext` + `AgentsMdContextItemTest.test_peon_dev`
  added. Core: 414/414, plugin suite: 107/107.

Deviations (flagged):
1. **S2 test strengthened:** the in-loop hint cannot fire right after a compact anyway (post-compact
   memory size 4 < the `size() < 10` guard) — the user-visible symptom was the **phantom pre-turn
   auto-compact** on the next turn (stale 77k > 0.7×80k). `test_inLoopCompact_noHintAfterCompact`
   therefore uses the 0.7 slave factor and asserts the compressor ran exactly once across two turns
   (RED before the fix: 5 model calls, GREEN after: 4).
2. **Plugin verification needed Maven first:** the plugin compiles against `lib/llmpeon-core.jar`
   (artifact copy via `maven-dependency-plugin`), not the workspace core — so before the Eclipse
   plugin build: `mvn clean install -pl org.sterl.llmpeon.core -am -DskipTests` +
   `mvn -o -pl org.sterl.llmpeon,releng/llmpeon-target -am package -DskipTests` (per AGENTS-DEV.md).
   The plan's "eclipseBuildProject only" step is insufficient for core→plugin changes.

Uncommitted (on `main` — no auto-commit per AGENTS.md; user decides branch/commit).

## 1. Context

Three reported symptoms (user-observed, see `issues/fact-issues.md` #3):

1. **Da Thinka (plan slave) is constantly told to call `compactSession`** — even right after it compacted.
2. **AGENTS-DEV.md is not loaded for Da Mek (dev slave).**
3. **AGENTS-PLAN.md is not loaded for Da Thinka (plan slave).**

Root causes found:

- **A (stale token counter):** `ThreadSafeMemory.totalTokenUsed` is set from the provider-reported
  `TokenUsage.totalTokenCount()` of the *last request* (`ThreadSafeMemory.addResult`). Two places feed
  it a value that describes the **pre-compact** context:
  1. `AbstractAgent.compressContext` used `memory.addResult(response)` with the **compressor** response
     → total = whole old context. **Already fixed by user** (now `memory.add(response.aiMessage())` —
     keep as-is, `AbstractAgent.java:256`).
  2. `ToolService.executeLoop` (ToolService.java:166-169): after `runAllTools` executes `compactSession`,
     `req.getMemory().addResult(response, tR)` runs with `response` = the model's own
     compactSession-requesting message → provider usage = the full pre-compact prompt again.
     Result: `addCompactHintIfNeeded` (threshold `autoCompactAfter * 0.95`, default 76 000 of 80 000)
     fires **immediately after the model's own compact** ("CONTEXT LIMIT WARNING: Call 'compactSession'
     as your first tool call. 77k of 80k used") → redundant re-compaction / constant nagging.
     The pre-turn trigger in `AbstractAgent.doCall` (`compactAfterTokens() < totalTokenUsed`,
     slaves: 0.7 × budget) also fires phantom auto-compacts on the next turn.
- **B (missing agent-specific AGENTS files for slaves):** `BuildPoAgentComponent.build()`
  (org.sterl.llmpeon/.../parts/ai/component/BuildPoAgentComponent.java, the `agentOrders` supplier)
  shares ONE supplier for both slaves and only adds `new AgentsMdContextItem(projectRef)` — base
  variants (AGENTS.md / RULES.md / CLAUDE.md …). The agent-specific `AGENTS-<agent>.md` is never added.
  The top-level path does it right: `AgentContextComponent.turnContext()` calls
  `AgentsMdContextItem.itemsFor(agent.getName(), projectRef)`.
  Slave names resolve correctly: `AiPlanAgent.getName()` = "Peon-Plan" (AiPlanAgent.java:83),
  `AiDevAgent.getName()` = "Peon-Dev" (AiDevAgent.java:54) → `AGENTS-PLAN.md` / `AGENTS-DEV.md`.
  Test `PeonAiServiceTest.test_slaves_getAgentsMdInTurnContext` only pins the base file → gap invisible.
  NOTE: `AGENTS-PLAN.md` does not exist in the project root yet (only in `non-peon-ai-agents-md/`) —
  user creates it separately; code must handle missing file (already does: `render() = null` → skip).

- **Dead code to remove (user decision):** `CompactSessionTool.compactSessionFallback` is only reachable
  when `request.getAgent() == null` — which never happens in production (`AbstractAgent.doCall` always
  sets `.agent(this)`); only tests exercise it. After its removal, `ToolLoopRequest.standingOrders`
  (deprecated) and `ToolLoopRequest.clearMemory()` are dead code → remove them too.

Docs (`docs/`) are PO+user-owned — **no agent writes docs in this plan**. ADR-0021's `isAutoCompact()`
(Jon opts out of the pre-turn trigger) does not exist in code — **out of scope here**, flagged for a
separate PO decision (implement the flag or correct the ADR).

## 2. Design decisions

- Token counter after a compact must reflect the **actual new memory**, not any request's usage.
  Source of truth: `ChatMessageUtil.estimateTokens(memory.getCopy())` (same estimate path
  `ThreadSafeMemory.add()` already uses).
- Re-evaluation lives in `ToolService.executeLoop` (it knows which tools ran), not in
  `ThreadSafeMemory` (which knows no tool semantics).
- `CompactSessionTool` with `agent == null` → `throw IllegalStateException` (mis-wiring must surface;
  `ToolService.runAllTools` converts it into a tool error message). No silent fallback.
- Slave turn orders become **agent-aware**: `PoDelegateTool` takes `Function<NamedAgent, List<ContextItem>>`
  instead of `Supplier<List<ContextItem>>` — still evaluated lazily per `dispatch()`, so the
  Workspace-Memory snapshot stays live per delegation (ADR-0032 behavior preserved).
- The in-loop hint itself (threshold 0.95 × shared budget, global not per-agent) stays as designed
  (ADR-0021). **Not** adding hint dedup in this plan (see Open questions).

## 3. Architecture / data flow (what changes)

- `ThreadSafeMemory` (core, memory pkg): +1 public method `reevaluateTokens()`.
- `ToolService.executeLoop` (core, tool pkg): +1 re-evaluation call after `addResult` when the batch
  contained `compactSession`.
- `CompactSessionTool` (core): shrink to agent-only path.
- `ToolLoopRequest` (core): drop deprecated `standingOrders` + `clearMemory()`.
- `PoDelegateTool` (core, poagent.tools): ctor arg type change (Supplier → Function).
- `BuildPoAgentComponent` (plugin): orders supplier → agent-aware function using
  `AgentsMdContextItem.itemsFor(...)`.

## 4. Affected files

Core (`org.sterl.llmpeon.core`):
| File | Change |
|---|---|
| `src/main/java/org/sterl/llmpeon/memory/ThreadSafeMemory.java` | add `public synchronized void reevaluateTokens()` → `totalTokenUsed = ChatMessageUtil.estimateTokens(getCopy())` |
| `src/main/java/org/sterl/llmpeon/tool/ToolService.java` | in `executeLoop` tool branch: after `addResult(response, tR)`, if any tool request in the batch is named `CompactSessionTool.NAME` → `req.getMemory().reevaluateTokens()` (comment: compact-request provider usage ≠ new small memory) |
| `src/main/java/org/sterl/llmpeon/tool/tools/CompactSessionTool.java` | remove `compactSessionFallback`; `agent == null` → `throw new IllegalStateException("compactSession requires an owning agent")` |
| `src/main/java/org/sterl/llmpeon/tool/ToolLoopRequest.java` | remove deprecated `standingOrders` field + `clearMemory()` method + its javadoc |
| `src/main/java/org/sterl/llmpeon/agent/AbstractAgent.java` | **no change** (user's compressContext fix stays) |
| `src/main/java/org/sterl/llmpeon/poagent/tools/PoDelegateTool.java` | ctor: `Supplier<List<ContextItem>> agentOrders` → `Function<NamedAgent, List<ContextItem>> ordersFor`; `dispatch` uses `ordersFor.apply(target)`; update class javadoc accordingly |
| `src/test/java/org/sterl/llmpeon/memory/ThreadSafeMemoryTest.java` | + test for `reevaluateTokens` |
| `src/test/java/org/sterl/llmpeon/agent/AiDeveloperAgentTest.java` | + 2 regression tests (stale counter / hint-after-compact) — see §7 |
| `src/test/java/org/sterl/llmpeon/tool/tools/CompactSessionToolTest.java` | rework: drop fallback-only tests, convert model-name tests to real-agent path (see §7) |
| `src/test/java/org/sterl/llmpeon/poagent/tools/PoDelegateToolTest.java` | adapt `newTool(...)` helper to `Function<NamedAgent, List<ContextItem>>` |

Plugin (`org.sterl.llmpeon`):
| File | Change |
|---|---|
| `src/org/sterl/llmpeon/parts/ai/component/BuildPoAgentComponent.java` | `jonDelegateTool` orders: `target -> { plan overview file + AgentsMdContextItem.itemsFor(target.agent().getName(), projectRef) + wmt }` |

Plugin tests (`org.sterl.llmpeon.test`):
| File | Change |
|---|---|
| `src/org/sterl/llmpeon/test/PeonAiServiceTest.java` | + `test_slaves_getAgentSpecificMdInTurnContext` (see §7); `test_slaves_getAgentsMdInTurnContext` stays (base file still must load) |
| `src/org/sterl/llmpeon/context/AgentsMdContextItemTest.java` | + Peon-Dev → AGENTS-DEV.md resolution test (only Peon-Plan covered today) |

## 5. Rules & constraints

- Core tests: JUnit 5 + AssertJ, GIVEN/WHEN/THEN structure.
- Plugin tests: OSGi JUnit 4, **no external assertion libs**; follow existing `PeonAiServiceTest`
  patterns (`eclipseWriteFile`, `streamMock.getLastRequest().messages()`).
- Log OR throw, never both. `Log` line in `ThreadSafeMemory` not needed for `reevaluateTokens`.
- No KV-cache-relevant changes: nothing touches system prompt composition; all changes are
  counter bookkeeping / context-item selection.
- Do not change `addCompactHintIfNeeded` thresholds or the 0.7/0.95 split (ADR-0021).
- Preserve `dispatch()` laziness: the orders function must run per call, not at construction.
- Build: `eclipseBuildProject` on `org.sterl.llmpeon.core` and `org.sterl.llmpeon` before running
  tests (stale bundle classes → ClassNotFoundException). Plugin test run: full suite, first run may
  need the one-time workspace-trust confirmation in the UI.
- No auto-commits unless on a dedicated build branch (user decides branch/merge).

## 6. BDD acceptance

**S1 — In-loop compact resets the counter (the reported bug)**
GIVEN an agent whose provider reports `TokenUsage` (e.g. total 9 600) on the response that requests
`compactSession`, and `autoCompactAfter = 1 000` (so 9 600 ≫ 95 % threshold)
WHEN the tool loop runs the compact
THEN the memory's `totalTokenUsed` after the loop reflects the NEW small memory (< 1 000)
AND no `CONTEXT LIMIT WARNING` user message was injected right after the compact
→ `AiDeveloperAgentTest.test_inLoopCompact_tokenCounterResetsBelowThreshold`

**S2 — No hint right after compact at realistic budget**
GIVEN the same setup with `autoCompactAfter = 80 000` and a compact-request usage of ~77 000
WHEN compact runs in-loop
THEN no `CONTEXT LIMIT WARNING` message exists in memory afterwards
→ `AiDeveloperAgentTest.test_inLoopCompact_noHintAfterCompact`

**S3 — reevaluateTokens recomputes from actual memory**
GIVEN a memory with N messages
WHEN `reevaluateTokens()` is called
THEN `getTotalTokenUsed() == ChatMessageUtil.estimateTokens(getCopy())`
→ `ThreadSafeMemoryTest.test_reevaluateTokens_usesActualMemoryEstimate`

**S4 — Dev slave gets AGENTS-DEV.md, not AGENTS-PLAN.md**
GIVEN project root with `AGENTS.md`, `AGENTS-DEV.md`, `AGENTS-PLAN.md`
WHEN `talkPlan` / `askDev` (or `buildWithDev`) delegate to the slaves
THEN the dev slave's first user message contains the base `AGENTS.md` content AND the
`AGENTS-DEV.md` header+content, and does NOT contain the `AGENTS-PLAN.md` content
→ `PeonAiServiceTest.test_slaves_getAgentSpecificMdInTurnContext` (dev half)

**S5 — Plan slave gets AGENTS-PLAN.md, not AGENTS-DEV.md**
(same GIVEN/WHEN)
THEN the plan slave's first user message contains `AGENTS-PLAN.md` header+content and does NOT
contain `AGENTS-DEV.md` content
→ `PeonAiServiceTest.test_slaves_getAgentSpecificMdInTurnContext` (plan half)

**S6 — Missing agent-specific file degrades silently**
GIVEN only `AGENTS.md` (no `AGENTS-DEV.md` / `AGENTS-PLAN.md`)
WHEN the slaves are delegated to
THEN both slaves still get the base `AGENTS.md`, no error
→ existing `PeonAiServiceTest.test_slaves_getAgentsMdInTurnContext` (must stay green)

**S7 — Name resolution (core-level)**
GIVEN `agentsFor("Peon-Dev", null)`-style lookup
THEN the agent-specific item includes `AGENTS-DEV.md`
→ `AgentsMdContextItemTest.test_peon_dev` (new, mirrors existing `test_peon_plam`)

**S8 — compactSession without agent fails loudly**
GIVEN a `ToolLoopRequest` without agent
WHEN `compactSession(null)` is invoked
THEN `IllegalStateException` is thrown
→ `CompactSessionToolTest.testCompactSessionWithoutAgentThrows`

## 7. Test strategy

Core (JUnit 5, AssertJ):
- `AiDeveloperAgentTest` — reuse its `fn`/StreamMock harness; **build mock `ChatResponse`s with
  `.tokenUsage(new TokenUsage(...))`** (pattern from `TokenUsageAccumulationTest`) — the bug is
  invisible without provider-reported usage.
  - New `test_inLoopCompact_tokenCounterResetsBelowThreshold`: 10 memory msgs; first response =
    `CALL_ME` + `TokenUsage(9 500, 100, 9 600)`; `autoCompactAfter(1 000)` via LlmConfig builder.
    Assert after `subject.call`: `getTotalTokenUsed() < 1 000` and no message contains
    "CONTEXT LIMIT WARNING".
  - New `test_inLoopCompact_noHintAfterCompact`: realistic numbers (80 000 budget, 77 000 usage).
    Assert no "CONTEXT LIMIT WARNING" in `memory.getCopy()`.
  - Existing `test_clear_memory` must stay green (message structure unchanged).
- `ThreadSafeMemoryTest` — small unit test for `reevaluateTokens`.
- `CompactSessionToolTest` — rework:
  - **Delete** `testCompactSessionFallbackWithoutAgent`, `testStandingOrdersSurviveCompaction`,
    `testMultipleStandingOrdersSurviveCompaction`, `testClearMemoryWithoutStandingOrdersIsIdentity`
    (all pin the removed fallback / deprecated `standingOrders` mechanism; standing-order survival is
    now covered by `AiDeveloperAgentTest.test_command_as_standing_order` and the plugin compact tests).
  - **Convert** `testCompactSessionUsesConfiguredCompactModel` + `testCompactSessionWithoutCompactModelUsesDefault`
    to the agent path: real `AiDevAgent` (or minimal `AbstractAgent` subclass) with a StreamMock model;
    assert the compressor request's `modelName()` as before. (A stubbed `compressContext` would skip
    the model call and break the assertion.)
  - **Add** `testCompactSessionWithoutAgentThrows`.
- `PoDelegateToolTest` — adapt `newTool(Supplier)` → `newTool(Function<NamedAgent, List<ContextItem>>)`;
  no-arg overload → `t -> List.of()`. All existing tests keep their meaning.

Plugin (OSGi JUnit 4, no external libs):
- `PeonAiServiceTest.test_slaves_getAgentSpecificMdInTurnContext`: `eclipseWriteFile` for all three
  files with distinct content; `delegate.talkPlan("...")` → plan slave `getMemory().getCopy()` first
  user message contains `AGENTS-PLAN.md` + its content, base `AGENTS.md` + content, NOT dev content;
  `delegate.askDev("...")` → dev slave contains `AGENTS-DEV.md` + content, NOT plan content.
  Clean up written files in finally (no cross-run state).
- `AgentsMdContextItemTest.test_peon_dev`: `itemsFor(AiDevAgent.NAME, () -> null)` → item[1] contains
  `AGENTS-DEV.md`.

Verification per increment: `eclipseBuildProject` core (and plugin for inc-3) →
`eclipseRunTests` project `org.sterl.llmpeon.core` (all tests), then plugin suite
`org.sterl.llmpeon.test` (full suite; first run may need workspace-trust confirmation).

## 8. Increments (each green → commit `inc-N: <summary>` + `Assisted-by` trailer)

- **inc-1 (core):** `ThreadSafeMemory.reevaluateTokens()` + `ToolService` re-evaluation after
  compactSession in the tool batch + the two `AiDeveloperAgentTest` regression tests +
  `ThreadSafeMemoryTest` unit test. (Fixes symptom 1 at the root; user's `compressContext` fix stays.)
- **inc-2 (core):** remove `compactSessionFallback` + `ToolLoopRequest.standingOrders`/`clearMemory()`;
  rework `CompactSessionToolTest`; `testCompactSessionWithoutAgentThrows`.
- **inc-3 (core + plugin):** agent-aware slave orders — `PoDelegateTool` Function ctor,
  `BuildPoAgentComponent` `itemsFor(...)`, `PoDelegateToolTest` helper, `PeonAiServiceTest`
  + `AgentsMdContextItemTest` new tests. (Fixes symptoms 2 & 3.)

## 9. Open questions

1. **Hint dedup:** with inc-1, the stale hint-after-compact is gone. While the context is
   *genuinely* > 95 %, the hint is still re-appended after every tool batch (ADR-0021: deliberate
   nudge). Optional follow-up (NOT in this plan): skip re-adding when
   `memory.containsUserMessage("CONTEXT LIMIT WARNING")`. Decide after observing behavior.
2. **`isAutoCompact()` (ADR-0021 vs code):** Jon is currently subject to the pre-turn hard trigger,
   contradicting the ADR. Separate story: implement the flag (default `true`, `AiPoAgent` → `false`)
   or correct the ADR. PO decision.
3. **AGENTS-PLAN.md file:** does not exist in the project root — user creates it; nothing to code.

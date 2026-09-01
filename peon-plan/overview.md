# Plan — R8: GPT-Default-Cache-Key (inc-23)

Branch `new-config` @ c5353dd · 1 Increment · Core-only · SOLL: `docs/caching.md` R8 (❌)

## 1. Context

**Goal:** For OpenAI requests on `gpt-5*` models, inject `prompt_cache_key` into the request body
with default **`peon-ai-<agent-id>`** — a stable per-agent cache key so long shared prefixes
(system prompt + tools) hit the provider KV-cache. A **non-blank** user value in the per-agent extra
body wins (2a merge); **empty body `{}`** and **`prompt_cache_key: ""`** count as *unset* → the
default stays. Models without the `gpt-5*` prefix get **no** key. Anthropic: no default (unchanged).

**Why:** GPT-5.6+ (Azure/OpenAI) prompt caching needs a stable `prompt_cache_key` for related
requests. After 2a/2c Clean-Break there is **no** default — the user must paste
`{"prompt_cache_key": ...}` by hand. R8 gives gpt-5 agents a sensible per-agent default automatically,
while the user stays in control (override via the existing JSON body).

**Reference (verified via web-fetch):** Azure prompt-caching doc — `prompt_cache_key` is a **top-level
request-body field** (sibling of `model`), supported on GPT-5.6+ for **both** Chat Completions and
Responses APIs, per-request. **Out of scope:** breakpoints (`prompt_cache_breakpoint`/`prompt_cache_options`)
— the user adds them in the JSON extra body if needed.

## 2. Design decisions (all 4 task questions resolved)

**Q1 — Agent-ID channel.** Add a nullable `String id` field to `AgentConfig` (the type the provider
already receives in `newRequestParameters`). Stamp it in `LlmConfig`'s agent-config factory methods —
the single place that knows "which agent is which id".
- `devAgentConfig()` → `.id(DEV)`, `planAgentConfig()` → `.id(PLAN)`, `compactAgentConfig()` → `.id(COMPACT)`,
  `searchAgentConfig()` → `.id(SEARCH)`.
- `customAgentConfig(AgentModelConfig rec, String agentId, ...)` — **new `agentId` param** + `.id(agentId)`.
  `CustomAgent.getConfig()` passes `getName()`.
- **`ConnectionIdentity` NOT touched** — the key is per-request (lives in `customParameters`), never part
  of the connection identity (OpenAI = `PER_REQUEST` → extra body/key never enters `buildTimeBody`).
- No API break: `AgentConfig` is `@Builder` (additive field, no positional ctor). `customAgentConfig`
  signature change is **core-internal** — 4 call sites (1 production `CustomAgent` + 3 test), all updated
  in this increment (same pattern as 2c's signature change).

**Q1b — Scope = `OpenAiProvider` only.** `OpenAiProvider` is the OpenAI-family provider in
`ExtraBodyMode.PER_REQUEST` — the **only** one with the per-request user-merge path R8 builds on.
`OpenAiOfficialProvider` is `NONE` (no extra body, no merge path); `LmStudioProvider`/local providers serve
non-`gpt-5` models anyway (the model-gate already yields no key). So scoping to `OpenAiProvider` is
coherent **and** matches the Kurzform. *(SOLL says "OpenAI-Familie" — resolved by the `PER_REQUEST`
gate; not an open question.)*

**Q2 — Injection point.** `OpenAiProvider.newRequestParameters` — where the former `cache_control` block
sat. Pass `defaultCacheKeyEntries(mc)` as the provider entries into the **existing**
`mergeCustomParameters(providerEntries, mc)` call (replaces the literal `null`); the user merge layers
over it. No new plumbing — R8 reuses the 2a merge.

**Q3 — Empty-body / empty-string semantics.** The "blank = unset" rule lives in
`ProviderRequestSupport.mergeCustomParameters` (the provider-layer merge), **not** in `ExtraBody.parse`
(stays a dumb JSON parser) and **not** special-cased in `OpenAiProvider`. Rule: a user entry whose value
is `null` or a blank `String` does **not** override a provider-supplied entry (it is "unset"). **Scoped**
to `providerEntries.containsKey(k)` — lone user keys (`{"foo":""}`) pass through unchanged (existing
behavior preserved). Rationale (PO hint): the provider owns the *default value* (supplied as provider
entries); the merge owns the *override rule*.

**Default value / gate.** `prompt_cache_key` default = `"peon-ai-" + agentId`. Gate: model non-blank
AND `model.toLowerCase(Locale.ROOT).startsWith("gpt-5")` (case-insensitive prefix per SOLL). **Guard:**
agent with no stable `id` (null/blank) → **no key** — keeps test-built id-less configs from emitting
`peon-ai-null` **and** preserves `ExtraBodyRequestTest.noBodyLeavesCustomParametersUntouched` (gpt-5.5,
asserts empty) green.

## 3. Architecture

**Data flow:** `LlmConfig.<agent>AgentConfig()` / `CustomAgent.getConfig()` → `AgentConfig{id}` →
`ToolService.executeLoop` → `ConfiguredChatModel` → `LlmProviders.of(...).newRequestParameters(agent, tools)`
→ `OpenAiProvider` → `defaultCacheKeyEntries(agent)` + `mergeCustomParameters` → `customParameters` → wire body.

**Boundaries:** Core-only. No plugin/OSGi changes (the plugin builds `LlmConfig`/`ConfiguredChatModel`
but never constructs `AgentConfig` directly nor calls `customAgentConfig`). `ConnectionIdentity`,
`EffectiveConnection`, `LlmConfig` equality all untouched (`AgentConfig` has no `@EqualsAndHashCode`,
so adding `id` changes no equality).

## 4. Affected files

Edit via `/llmpeon-core/...` (Maven module `org.sterl.llmpeon.core`; the file also appears at a second
workspace root `/llmpeon-parent/org.sterl.llmpeon.core/...` — same file).

**Main:**
1. `src/main/java/org/sterl/llmpeon/ai/AgentConfig.java` — + `private final String id;` (Javadoc: per-request
   only, never in the connection identity).
2. `src/main/java/org/sterl/llmpeon/ai/LlmConfig.java` — `.id(...)` in the 4 core factory methods;
   `customAgentConfig(rec, String agentId, supported, on, off, temp)` new param + `.id(agentId)`.
3. `src/main/java/org/sterl/llmpeon/agent/CustomAgent.java` — `getConfig()` passes `getName()` as agentId.
4. `src/main/java/org/sterl/llmpeon/provider/OpenAiProvider.java` — + constants + `defaultCacheKeyEntries(AgentConfig)`;
   `newRequestParameters` passes it to `mergeCustomParameters` (was `null`). + imports `java.util.Locale`, `java.util.Map`.
5. `src/main/java/org/sterl/llmpeon/provider/ProviderRequestSupport.java` — blank-unset rule in
   `mergeCustomParameters` + `private static boolean isAbsentValue(Object)`; update the method Javadoc.

**Test:**
6. `src/test/java/org/sterl/llmpeon/provider/OpenAiProviderCacheKeyTest.java` — **NEW** (unit, §7).
7. `src/test/java/org/sterl/llmpeon/tool/PerAgentConnectionE2ETest.java` — +1 E2E test (gpt-5 default + user-wins on the wire).
8. `src/test/java/org/sterl/llmpeon/AiServicePerAgentThinkTest.java` — update 3 `customAgentConfig(...)` call sites (+agentId arg).

**Stays untouched:** `ExtraBody.parse`, `ExtraBodyMode`, `ConnectionIdentity`, `EffectiveConnection`,
`AnthropicProvider`, `OpenAiOfficialProvider`, `LmStudioProvider`, all plugin/OSGi code, `ExtraBodyExamples`.
Docs `docs/caching.md` (PO-owned; R8 ❌→✅ is the PO's action post-acceptance).

## 5. Rules & constraints

- **Merge rule (preserve 2a):** user body wins on non-blank conflict; reserved keys `model`/`messages`/`tools`
  stripped at parse (unchanged). R8 only adds the "blank/null user value over a provider default = unset" clause.
- **Key never in identity:** `prompt_cache_key` must not leak into `ConnectionIdentity` (per-request).
- **Model-gate:** only `gpt-5*` (case-insensitive). Non-gpt-5 / null model → no key. **No agent id → no key.**
- **Anthropic:** no default (SOLL) — untouched.
- **No plugin API break:** `customAgentConfig` change is core-internal; the 4 call sites are updated here.
- **Log OR throw:** no new exceptions (merge/parse never throw).
- **Byte-identical when no default applies:** non-gpt-5 / no-id / non-OpenAI requests produce the same
  `customParameters` as today.

## 6. BDD acceptance (from R8)

| # | Scenario | GIVEN | WHEN | THEN | Test |
|---|---|---|---|---|---|
| 1 | Default | OpenAI agent `plan` on `gpt-5*`, no extra body | agent calls | request carries `prompt_cache_key = "peon-ai-plan"` | unit `gpt5NoBody_defaultKeyInjected` + E2E phase 1 |
| 2 | User-wins | extra body `prompt_cache_key: "custom"` | agent calls | request carries `prompt_cache_key = "custom"` | unit `gpt5UserBody_winsOverDefault` + E2E phase 2 |
| 3 | Unset (empty string) | extra body `prompt_cache_key: ""` | agent calls | request carries default `peon-ai-plan` | unit `gpt5EmptyStringValue_defaultKept` |
| 4 | Unset (empty body) | extra body `{}` | agent calls | request carries default `peon-ai-plan` | unit `gpt5EmptyObjectBody_defaultKept` |
| 5 | Non-gpt-5 | OpenAI agent, model w/o `gpt-5*` prefix (e.g. `deepseek`/`llama`) | agent calls | request carries **no** `prompt_cache_key` | unit `nonGpt5Model_noKey` |

## 7. Test strategy

**NEW `OpenAiProviderCacheKeyTest`** (co-located with the provider — collects R8's unit tests in one place).
Helper: `AgentConfig.builder().provider(OPEN_AI).model(m).id(id).extraBody(body)` →
`LlmProviders.of(OPEN_AI).newRequestParameters(mc, List.of())` → assert on `customParameters()`.
- `gpt5NoBody_defaultKeyInjected` — (`"gpt-5.6"`, `"plan"`, null) → containsEntry(`prompt_cache_key`, `peon-ai-plan`).
- `gpt5PrefixMatchIsCaseInsensitive` — (`"GPT-5.6-turbo"`, `"plan"`, null) → key = `peon-ai-plan`.
- `gpt5UserBody_winsOverDefault` — (`"gpt-5.6"`, `"plan"`, `{"prompt_cache_key":"custom"}`) → key = `custom`.
- `gpt5EmptyStringValue_defaultKept` — (`"gpt-5.6"`, `"plan"`, `{"prompt_cache_key":""}`) → key = `peon-ai-plan`.
- `gpt5EmptyObjectBody_defaultKept` — (`"gpt-5.6"`, `"plan"`, `{}`) → key = `peon-ai-plan`.
- `nonGpt5Model_noKey` — (`"deepseek-chat"`, `"plan"`, null) → `doesNotContainKey(prompt_cache_key)` (customParameters empty).
- `noAgentId_noKey` — (`"gpt-5.6"`, null, null) → `doesNotContainKey(prompt_cache_key)` (the guard).
- `mergeBlankValue_doesNotOverrideProviderDefault` — direct `ProviderRequestSupport.mergeCustomParameters(
  Map.of("prompt_cache_key","peon-ai-plan"), agent(..., {"prompt_cache_key":""}))` → key = default; **and**
  sub-assert: a lone blank user key NOT in provider entries passes through
  (`mergeCustomParameters(Map.of("prompt_cache_key","d"), agent(..., {"foo":""}))` contains `foo:""`) — locks the rule's scoping.

**E2E extension** — `PerAgentConnectionE2ETest.gpt5DefaultCacheKey_reachesTheWire_andUserBodyWins`,
one `@Test`, two phases against `baseStub` (reuse existing `runLoop`):
- phase 1: `AgentConfig{id=plan, model=gpt-5.6}`, no body → captured body `prompt_cache_key = "peon-ai-plan"`.
- phase 2: same + `extraBody={"prompt_cache_key":"custom"}` → captured body `prompt_cache_key = "custom"`.

*(The existing 7 E2E cases are untouched — none use a `gpt-5*` model and none set an `id`, so they stay green.
Phase 2 sets an extra body → `EffectiveConnection.isBase` is false → a second model is built, but its url is
still the base url, so the request lands on `baseStub`.)*

**Regression safety (verified by exploration):** every existing test builds `AgentConfig` **without** an `id`
→ the "no id → no key" guard means none of them get a default injected → **zero** behavior change for them.
The merge-rule change only fires when `providerEntries.containsKey(k)`, which existing OpenAI paths don't hit
(they pass `null` provider entries). `ExtraBodyRequestTest.mergeUserWins_overProviderEntry` (Map value) and the
LM_STUDIO `reasoning` merges (non-blank strings) are unaffected — both run in the core suite.

**No plugin/OSGi tests** for R8 (core-only).

## 8. Open questions

1. **Homepage note (AGENTS.md: visible-behavior changes → update in same increment)?** R8 is an implicit wire
   default (no UI element), but it changes caching behavior for gpt-5 agents. `homepage/src/setup/
   advanced-configuration.md` has a "Caching" section (added in 2c) and the GPT example
   `{"prompt_cache_key": "llmpeon"}`. **Recommendation:** add 1 line — "GPT-5* agents get a default
   per-agent cache key `peon-ai-<agent>`; override it in the JSON body." **PO decision:** include this 1-liner
   in inc-23 (recommended, AGENTS.md-compliant) or defer to a follow-up. *The code increment stands on its own
   either way; `docs/caching.md` (PO-owned) flips ❌→✅ post-acceptance, not by the dev.*
2. None on the code design — all 4 task questions are resolved in §2.

## Increment overview (inc-23)

**Status: ✅ DONE (2026-09-01)** — all checkpoints implemented; core 512 green (503 + 8 unit + 1 E2E), plugin compile green; homepage 1-liner included (PO decision); `docs:build` gate deferred (esbuild env-blocker, as in 2c).

- **Files:** 5 main + 3 test (1 new). Core-only.
- **Implementation checkpoints (one commit, `inc-23`; sub-steps for green-at-each-step safety):**
  1. `AgentConfig.id` + `LlmConfig` factory stamps + `customAgentConfig(agentId)` + `CustomAgent` + 3 test call
     sites. *(No behavior change yet — the field is unused; existing tests pass.)*
  2. `ProviderRequestSupport` blank-unset merge rule + `isAbsentValue`. *(Existing merge tests pass.)*
  3. `OpenAiProvider.defaultCacheKeyEntries` + injection + NEW `OpenAiProviderCacheKeyTest`.
  4. E2E extension.
  5. **Gates:** core `mvn test` green (full unit + E2E) → `eclipseBuildProject` on `org.sterl.llmpeon`
     (plugin compile against the new core jar) green.
  6. *(Optional, per Q1)* homepage 1-liner.
- **Commit:** `inc-23: R8 — inject default prompt_cache_key (peon-ai-<agent>) for gpt-5* OpenAI models; blank/empty user value keeps the default`
  + `Assisted-by: Peon AI (<ModelName>)` trailer. On branch `new-config`, scoped to inc-23 files.

## Know-how / pitfalls for the implementer

- **The guard is load-bearing:** `ExtraBodyRequestTest.noBodyLeavesCustomParametersUntouched` uses model
  `gpt-5.5` and asserts `customParameters()` is **empty**. Without the "no id → no key" guard this test breaks.
  Do not remove the guard.
- **`ExtraBody.parse("{}")` returns an empty map, not null** — the `{}` case reaches the merge loop and the
  provider entries survive. `parse("{\"prompt_cache_key\":\"\"}")` returns `{prompt_cache_key: ""}` — the
  blank-unset rule in the merge is what keeps the default there.
- **Case-insensitivity** uses `Locale.ROOT` (not the default locale).
- **Two workspace roots:** `eclipseFindResource` returns `/llmpeon-core/...` and
  `/llmpeon-parent/org.sterl.llmpeon.core/...` for the same file — edit one, it's the same file.
- **Stale bundle warning (workspace memory #16):** `eclipseBuildProject` the plugin before any plugin test;
  core is Maven (`mvn test` does its own build).

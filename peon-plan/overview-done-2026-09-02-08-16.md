# Plan — inc-24: Model-List-Fetch Fix (Cancellation Race + API-Key Log-Leak)

Branch `new-config` @ 5c0ff28 · Core-only (plugin = regression gate) · 1 Commit (PO) · 2 verifiable stages

## Findings vs. diagnosis (verified against code — not rubber-stamped)

**Confirmed:**
- **Bug 3 (concurrency):** `SharedHttpClient.pendingRequest` (global static `AtomicReference`) +
  `cancelAndSend`'s non-atomic `sendAsync` → `getAndSet(future)` → `prev.cancel(true)`. Concurrent
  list-fetches sharing one `ConnectionIdentity` cancel each other; the N-1 canceled futures return
  `List.of()` → those agents render an empty model list. `cancelAndSend` is **internal-only** (called
  solely by `cancelAndGet`); `cancelAndGet` is called by **7** providers: `OpenAiProvider`,
  `OpenAiOfficialProvider`, `AnthropicProvider`, `LmStudioProvider`, `MistralProvider`,
  `GithubModelsProvider`, `GithubCopilotProvider`. (`OllamaProvider` does not use it.)
- **Bug 4 (security):** `ConnectionIdentity` is a plain record → default `toString()` emits the real
  `apiKey` + `buildTimeBody`; `ModelListCache.fetch` interpolates the identity into `log.warn(...)`.
  (memory #20: secrets must never appear in toString/logs.)

**Refinements / corrections:**
1. **`EffectiveConnection` is NOT fully transitively safe.** After masking `ConnectionIdentity`, its
   default `toString()` still exposes `perRequestBody` (the extra-body JSON) unmasked. `buildConfig` is
   already safe (`LlmConfig` `@ToString(exclude={apiKey,…})`). → **also mask `perRequestBody`** in a
   custom `EffectiveConnection.toString()` (→ `<len> chars`).
2. **`cancelAndGet` should be renamed** (e.g. `getModels`) — the "cancel" is gone, the name misleads.
   7 core provider call sites; transparent to the plugin (which calls `listAiModels`, not `cancelAndGet`).
3. **Missing regression gate:** plugin OSGi test **`ModelListFetchTest`** (`org.sterl.llmpeon.test`,
   5 tests, all sequential) exercises `ModelListCache` + the full `SharedHttpClient` fetch path. Not in
   the diagnosis's file/test list → must be a plugin-test gate.
4. **"totter CancellationException-Catch" is inaccurate:** for `CompletableFuture`, `get()` on a
   *canceled* future throws `CancellationException` **unwrapped** (not inside `ExecutionException`), so
   that catch is live (it catches self-cancellation → `List.of()`). The observed log-spam is actually
   `ModelLoadFailedException` on genuine failures **+ the identity leak** (Bug 4). Doesn't change the fix
   (removing the cancel makes the catch moot) — noted so the plan stays accurate.

## 1. Context
- **Bug 3:** opening the advanced-config page schedules one `fetchModels()` background `Job` per
  per-agent section (`AgentModelConfigSection.fetchModels` → `Job.create(...).schedule()`). Sections
  sharing the base connection share one `ConnectionIdentity` → N parallel
  `ModelListCache.getOrFetch(sameIdentity)` → N concurrent HTTP fetches fight the single global slot →
  N-1 are `cancel(true)`d → return empty → those agents show no model list (until manual refresh).
- **Bug 4:** `ConnectionIdentity` (and transitively `EffectiveConnection`) leak the real `apiKey`/body
  via `toString()` into `ModelListCache` log lines.
- **SOLL:** fix both. No new feature; preserve `ModelListCache` cache-on-success / retry-on-failure.

## 2. Design decisions
- **D1 — Remove the global cancel entirely (Bug 3, critical).** Delete `SharedHttpClient.pendingRequest`
  + `cancelAndSend` + the `CancellationException` catch. `cancelAndGet` → renamed **`getModels`**:
  plain `sendAsync` + `future.get(timeout)` (keep the existing `MODEL_TIMEOUT.plusSeconds(10)`
  expression; keep `InterruptedException`/`ExecutionException`/`TimeoutException` handling →
  `ModelLoadFailedException`). **This alone fixes the user-visible bug** (no self-cancel, no empty-list race).
- **D2 — Single-flight per `ConnectionIdentity` in `ModelListCache` (Bug 3, efficiency + consistency).**
  + `ConcurrentHashMap<ConnectionIdentity, CompletableFuture<List<AiModel>>> inFlight`. `getOrFetch`:
  - cache hit → return it.
  - else `inFlight.putIfAbsent(identity, newCompletableFuture)`; the **winning thread runs the fetcher
    inline** (on its own background `Job` thread); losers `future.get()`.
  - success: `byIdentity.put` + `future.complete(models)`. failure/empty: `future.completeExceptionally`
    (waiters get `null`), do **not** cache.
  - **`finally`: `inFlight.remove(identity, future)`** (two-arg, value-checked) — a finished/failed
    flight is always removed so the next need retries.
  - Do **NOT** run the fetcher inside `computeIfAbsent` (holds the CHM bin lock for the whole HTTP call).
  - `refresh` stays as-is (user-initiated; no dedup needed now that the cancel is gone).
- **D3 — Mask secrets in `toString()` (Bug 4).**
  - `ConnectionIdentity`: custom `toString()` → `provider`/`url` readable, `apiKey=***`,
    `buildTimeBody=null` if null else `buildTimeBody=<len> chars`.
  - `EffectiveConnection`: custom `toString()` → `identity` (now masked), `buildConfig` (already safe),
    `perRequestBody=<len> chars` (or `null`), `isBase`.
  - Record `equals`/`hashCode` are **untouched** (only `toString` added) → all `ConcurrentHashMap` keying
    (`ModelListCache.byIdentity/inFlight`, `ConfiguredChatModel.agentConnections`) is unaffected.
- **D4 — Scope: Core only.** The plugin never calls `SharedHttpClient`/`cancelAndGet` (it calls
  `listAiModels`); the rename is transparent. Plugin = compile + `ModelListFetchTest` regression gate only.

## 3. Architecture / data flow
- UI: `AgentModelConfigSection.fetchModels()` (per section, background `Job`) →
  `ModelListCache.getOrFetch(identity, fetchList)` → (single-flight, 1 owner) →
  `LlmProviders.of(provider).listAiModels(buildConfig)` → `SharedHttpClient.getModels` (sendAsync +
  get(timeout)) → `List<AiModel>` → cache + `applyModelList` (UI thread, stale-guard by identity).
- Security: any log line interpolating `ConnectionIdentity`/`EffectiveConnection` now prints masked values.

## 4. Affected files
Main (module `org.sterl.llmpeon.core`, edit via `/llmpeon-core/...`; the same files appear at a second
workspace root `/llmpeon-parent/org.sterl.llmpeon.core/...` — same file):
1. `ai/SharedHttpClient.java` — remove `pendingRequest` + `cancelAndSend`; rename `cancelAndGet`→`getModels`
   (plain sendAsync + get); drop the `CancellationException` catch + the now-unused `AtomicReference` import.
2. `ai/ModelListCache.java` — + `inFlight` map; single-flight in `getOrFetch` (putIfAbsent + inline fetch +
   `finally` two-arg remove); complete/completeExceptionally the future.
3. `ai/ConnectionIdentity.java` — + custom `toString()` (mask `apiKey`, `buildTimeBody`).
4. `ai/EffectiveConnection.java` — + custom `toString()` (mask `perRequestBody`; delegate `identity`/`buildConfig`).
5. **7 providers** — `SharedHttpClient.cancelAndGet(…)` → `getModels(…)` (mechanical): `OpenAiProvider`,
   `OpenAiOfficialProvider`, `AnthropicProvider`, `LmStudioProvider`, `MistralProvider`, `GithubModelsProvider`,
   `GithubCopilotProvider`. (`ProviderRequestSupport` only references the `MODEL_TIMEOUT` constant — untouched.)

Test:
6. `ai/ConnectionIdentityTest.java` — **NEW**: `toString()` masking (apiKey masked, buildTimeBody `<len> chars`,
   provider/url visible; real key absent).
7. `ai/EffectiveConnectionTest.java` — **extend** (existing): `EffectiveConnection.toString()` masks
   `perRequestBody`. (Verify it's the natural home; if not, co-locate with the new `ConnectionIdentityTest`.)
8. `ai/ModelListCacheTest.java` — **extend**: + concurrency test (2 threads, same identity, `CountDownLatch`
   → exactly 1 fetcher invocation, both get the list); + (optional) concurrent-failure → inFlight removed → retry.

Plugin (**NO code change**):
- `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/ModelListFetchTest.java` — UNCHANGED; regression gate.

## 5. Rules & constraints
- **Cache semantics preserved:** cache-on-success; failed/empty fetch not cached (retry next time);
  `refresh` always fetches, keeps the old entry on failure.
- **In-flight removal guarantee:** the `inFlight` future is ALWAYS removed (two-arg `remove`) in `finally`,
  so a completed/failed flight never blocks a later fetch.
- **No hang:** waiters `future.get()` are bounded because the fetcher is bounded by the HTTP timeout
  (`MODEL_TIMEOUT + 10s`). No recursive re-entrancy (the fetcher never calls `getOrFetch`).
- **Secrets (memory #20):** `apiKey` NEVER in toString/log; body shown as `<len> chars` (no content).
  `ConnectionIdentity.equals`/`hashCode` MUST NOT change — verify `differentIdentitiesAreIndependent` + map keying.
- **No plugin API break:** rename is core-internal; the plugin calls `listAiModels`.
- **Thread rules:** fetcher runs on the caller's background `Job` thread (never the UI thread);
  `applyModelList` stays on the UI thread.

## 6. BDD acceptance
| # | Scenario | GIVEN | WHEN | THEN | Test |
|---|---|---|---|---|---|
| 1 | No cross-cancel / single-flight | N parallel `getOrFetch(sameIdentity)` (page open) | all complete | model list returned for all; **exactly 1** HTTP fetch | core concurrency test + `ModelListFetchTest` |
| 2 | No empty-list race | 2 concurrent fetches, same identity | both complete | neither returns empty due to a sibling cancel | core concurrency test |
| 3 | Failure retries | first fetch fails | waiters + a later fetch | waiters get null (not cached); a later `getOrFetch` retries (inFlight removed) | core (extend failure cases) |
| 4 | Key not leaked | `ConnectionIdentity` with an apiKey | `toString()` / any log of it | output has `apiKey=***`, NOT the real key | `ConnectionIdentityTest` |
| 5 | Body masked | identity w/ buildTimeBody; EffectiveConnection w/ perRequestBody | `toString()` | shown as `<len> chars` (no content) | `ConnectionIdentityTest` / `EffectiveConnectionTest` |
| 6 | provider/url visible | identity | `toString()` | `provider` and `url` still readable | `ConnectionIdentityTest` |
| 7 | Existing cache behavior | all current cases | getOrFetch/refresh success+failure | unchanged | `ModelListCacheTest` (5) + `ModelListFetchTest` (5) green |

## 7. Test strategy
- **NEW core `ConnectionIdentityTest`:** BDD 4/5/6. Assert `toString()` contains `apiKey=***` and
  `provider`/`url`; does NOT contain the real key; buildTimeBody/perRequestBody → `<len> chars` (no content substring).
- **Extend core `ModelListCacheTest`:**
  - `concurrentGetOrFetch_sameIdentity_singleFetch` — 2 threads + `CountDownLatch` (fetcher increments a
    counter, then awaits a release latch the test counts down); assert exactly **1** fetcher invocation and
    both threads get the list.
  - (optional) `concurrentFailure_removesInFlight_allowsRetry` — first fails; waiters get null; a later fetch retries.
- **Extend core `EffectiveConnectionTest`:** `toString()` masks `perRequestBody`.
- **Plugin `ModelListFetchTest` (unchanged):** full regression gate through `SharedHttpClient` (5 tests,
  sequential → stay green). Run via PDE JUnit after `eclipseBuildProject` on core+plugin+test (memory #16);
  first run needs the workspace-trust dialog (memory #13).
- **Existing core `ModelListCacheTest` (5)** stay green (all sequential → single-flight transparent).

## 8. Open questions (all non-blocking — recommended defaults in the plan)
1. **1 commit vs 2 (revertability)?** PO said "1 Commit: inc-24" → plan is 1 commit (2 verifiable stages).
   If you prefer 2 commits (inc-24 = security masking, inc-25 = cancel+single-flight) for cleaner
   `git revert`, it's a trivial split — say the word.
2. **Rename `cancelAndGet`→`getModels`?** Recommended (honest name). If you'd rather minimize diff, keep
   `cancelAndGet` (body just loses the cancel). 
3. **`perRequestBody` masking (D3)?** Recommended (consistency + memory #20). If you'd rather leave
   `EffectiveConnection.toString()` at the default (safe after identity masking, since `buildConfig`
   already excludes apiKey), skip the `EffectiveConnection` custom toString.

## Status
- Stage 1 (security masking): ✅ done — `ConnectionIdentity`/`EffectiveConnection` custom `toString` (apiKey=***, bodies `<len> chars`); NEW `ConnectionIdentityTest` (3), `EffectiveConnectionTest` +1.
- Stage 2 (concurrency): ✅ done — `SharedHttpClient` global slot/cancel removed, `cancelAndGet`→`getModels`; `ModelListCache` single-flight (`inFlight`, putIfAbsent + inline fetch + two-arg `finally` remove); 7 provider renames; `ModelListCacheTest` +2 concurrency tests.
- Gates: ✅ core `mvn test` 518/518 · ✅ `mvn -o -pl org.sterl.llmpeon,releng/llmpeon-target -am package` · ✅ `eclipseBuildProject` plugin+test (only pre-existing warnings) · ✅ plugin PDE JUnit full suite 122/122 (incl. `ModelListFetchTest`).

## Increment overview (inc-24)
- **Stage 1 (security, green):** `ConnectionIdentity.toString` + `EffectiveConnection.toString` masking;
  NEW `ConnectionIdentityTest`; extend `EffectiveConnectionTest`. Core build green.
- **Stage 2 (concurrency, green):** `SharedHttpClient` (remove slot/cancel, rename→`getModels`, drop
  `CancellationException` catch) + 7 provider call sites; `ModelListCache` single-flight; extend
  `ModelListCacheTest` (+concurrency test). Core build green.
- **Gates:**
  1. Core: `mvn test` (project `llmpeon-core`) green (unit incl. new `ConnectionIdentityTest` + `ModelListCacheTest`).
  2. Plugin compile: `eclipseBuildProject` on `org.sterl.llmpeon` (against new core) green. (Or Tycho
     `mvn -o -pl org.sterl.llmpeon,releng/llmpeon-target -am package` outside Eclipse.)
  3. Plugin test: `eclipseBuildProject` core+plugin+test, then run `ModelListFetchTest` (PDE JUnit) green.
- **Commit (1):** `inc-24: fix model-list fetch (single-flight + remove global cancel) and mask apiKey/body in ConnectionIdentity/EffectiveConnection.toString()`
  + `Assisted-by: Peon AI (<ModelName>)` trailer. Scoped to 4 core main + 7 provider + 3 core test files.

## Know-how / pitfalls for the implementer
- **Record toString ≠ equals:** adding `toString()` to a record does NOT change `equals`/`hashCode` —
  `ConcurrentHashMap` keying (`byIdentity`, `inFlight`, `agentConnections`) is unaffected. Don't touch the value fields.
- **Don't fetch inside `computeIfAbsent`:** running the blocking fetcher there holds the CHM bin lock for
  the whole HTTP call. Use `putIfAbsent` + inline fetch + two-arg `remove` in `finally`.
- **Two-arg `inFlight.remove(identity, future)`:** the one-arg form could remove a *different* thread's
  in-flight future if one was already swapped in — must be the value-checked remove.
- **Complete the future in all reachable paths** (success → `complete`, failure → `completeExceptionally`)
  so waiters never hang; the HTTP timeout bounds the normal path.
- **Stale bundle (memory #16):** `eclipseBuildProject` the plugin before any plugin test; `target/` surefire
  reports are stale/misleading — trust the live run.
- **First OSGi test run (memory #13):** needs the one-time workspace-trust dialog; run the full suite,
  don't parallel-launch on timeout.
- **Two workspace roots:** `eclipseFindResource` returns both `/llmpeon-core/...` and
  `/llmpeon-parent/org.sterl.llmpeon.core/...` for the same file — edit one.

# Review: Warning-Cleanup Cycle (branch story/lib-update-2026-09-09, 2026-09-10) — Verdict: **CONCERNS**

Directive scope: 64 → 12 problems, behavior-preserving only. No plan file existed; reviewed
against the directive text. Commits: `51f43d2` (inc-1), `a9124f1` (inc-2), `56e9cc5` (inc-3).

## Verified — Code ↔ Directive

1. **CSS_CLASS_NAME_KEY (inc-1)** ✅ — `WidgetCss.CSS_CLASS_NAME_KEY = "org.eclipse.e4.ui.css.CssClassName"`
   is an EXACT match with the platform source (`org.eclipse.e4.ui.css.swt.CSSSWTConstants`,
   verified against the used target bundle, not memory). Platform JavaDoc explicitly authorizes
   copying: "Clients may rely on the value of this key if they want to avoid a dependency on
   this package." Constant defined once in `WidgetCss`, all 7 widget files reference it — no
   divergent string literals. Wrong-value risk: none.
2. **EclipseUiUtil theme (inc-1)** ✅ — resolution NOT switched to `IThemeManager`; kept on
   `IThemeEngine` with `@SuppressWarnings("restriction")` and a written justification comment:
   the public workbench-theme registry can report non-CSS IDs (e.g. `org.eclipse.ui.defaultTheme`)
   and is not provably behavior-identical. Directive explicitly allowed "suppressed with
   justification". Listener stays on public `IThemeManager` API.
3. **resolveModel removal (inc-2)** ✅ — zero references anywhere in llmpeon code and docs
   (grep across workspace; only `docs/memory.md` session-report mention). `advanced-configuration.md`
   documents per-agent model resolution via `ChatRequest.modelName()` without `resolveModel`.
   Replacement `modelFor` is covered by `ModelConnectionCacheTest` (19 references, incl.
   `withThinkSupported`/`updateConfig` cache-clear behavior).
4. **Optional.ofNullable rewrites (inc-2)** ✅ — all sites (`EclipseUtil.getTextEditor`,
   `getOpenFile` fast path + JDT fallback, `resolveResource` adaptable path, `selectionElement`)
   keep identical fallback semantics: null still flows to the same `orElseThrow`/fallback
   branches; each site commented ("getAdapter is @NonNull-annotated but may return null at
   runtime").
5. **Small fixes (inc-2/3)** ✅ — `EclipseRunTestTool` `Objects.requireNonNull(testType)`
   (line ~142, comment documents the guarantee; same NPE outcome, clearer contract);
   `StatusLineWidget` null-guard at 114-116; `setCharset(String, IProgressMonitor)` two-arg
   non-deprecated overload (`EclipseWorkspaceWriteFileToolTest:113,124`); no-op tycho-compiler
   config block deleted (repo-wide grep: 0 matches in poms); test MANIFEST has
   `Automatic-Module-Name`; `StandingOrdersBuilderTest` uses public
   `org.eclipse.jface.text.Document`; `IoUtils.writeFile` `@SuppressWarnings("restriction")`
   with justification comment.
6. **ADR-0044** ✅ — unchanged and consistent (incl. the 2026-09-10 review addendum).

## Verified — Docs ↔ Code

7. **AGENTS-DEV.md "Known-benign warnings" block** ✅ with two bookkeeping discrepancies
   (see findings below): the 10 plugin null-type-safety entries match the live problem list
   file-by-file and line-by-line (`AIChatView:158-159`, `PeonAiService:401-402,489`,
   `ModelComboWidget:122`, `EclipseUtil:318`, `EclipseWorkspaceReadFileTool:155`,
   `AiAgentStatusModel:48`, `StatusLineWidget:188`); the `resources/` class-folder entry
   matches; `org.sterl.llmpeon.test` has 0 problems; nothing else remains in plugin/test.

## Test honesty

8. The 3 removed `ConfiguredModelTest` tests: remaining 3 tests all cover `withModel` (same
   model no-change, null max-tokens preserved, null id no-NPE); the removed method's per-agent
   behavior lives on in `modelFor` with dedicated coverage. No deleted-content diff possible
   (no git tooling in this environment) — verified by proxy: zero references remain and the
   class's public surface is fully covered by remaining tests. No coverage loss detectable.

## Findings (non-blocking → CONCERNS)

- **F1 — IoUtils dead TODO block survives (inc-3 incomplete):** inc-3 deleted the dead
  commented-out TODO block in `writeFile`, but its twin in the SAME file
  `IoUtils.ensureFolders` (org.sterl.llmpeon/src/org/sterl/llmpeon/parts/shared/IoUtils.java,
  ~lines 96-100: `/* TODO I don't think this is really needed! ... refreshLocal ... */`) is
  still present. Cosmetic, but exactly the pattern inc-3 claims to have removed.
- **F2 — "12" vs 11 visible IDE problems:** the "Core ×1 `MockLlmServer:98` unused TWR" entry
  describes real code accurately (`try (var s = new Socket(...))`, variable needed for
  auto-close — line 98 confirmed), but the warning does NOT appear in the Eclipse problems
  list for llmpeon-core (~150 warnings there, none at MockLlmServer). The "remaining 12" are
  11 in the default IDE view. Suggest the same "not in default build/IDE" qualifier the block
  already uses for the -Xlint items.
- **F3 — core's ~150 IDE null-analysis warnings are outside the known-benign list:** the
  block's "fix real new ones, keep this list current" framing implies completeness; core's
  pre-existing noise (null-safety on the /llmpeon-core workspace project) is not covered and
  was not in the 64→12 scope. One-line clarification would prevent future re-triage.

Most likely reason this breaks later: the WidgetCss constant silently diverges from a future
platform value (no test pins it to styling behavior) — a one-line unit test asserting the
constant equals the platform value would make that drift impossible to merge unnoticed.

Skill/instruction gaps: none. Mutation-check: n/a (infrastructure cycle).

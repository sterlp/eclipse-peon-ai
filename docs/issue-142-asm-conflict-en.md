# Issue #142 — ASM conflict: plugin installation breaks Eclipse 2026-03

> **Status:** ✅ Fix implemented (2026-10-06). Externally:
> github.com/sterlp/eclipse-peon-ai/issues/142.
>
> _English translation of [issue-142-asm-conflict.md](issue-142-asm-conflict.md) (the German
> document is the source of truth)._

## Cause (verified 2026-10-06, p2 metadata)

Our **plugin bundle** contains no asm (MANIFEST `Bundle-ClassPath`/`Import-Package`, 60 lib JARs,
0 hits for `org/objectweb`). The conflict arose solely in the **p2 update-site repository**:
`tycho-p2-repository-plugin` with `includeAllDependencies=true` copied the complete target platform
into our repository — including `org.objectweb.asm` 9.10.1 + commons/tree/util/analysis (2026-09).
On a 2026-03 IDE (platform asm 9.9.1) p2 pulled in our 9.10.1 → uses-constraint
(spifly → jsvg → swt.svg → workbench) → the IDE did not start. We were the **only source** of
9.10.1. asm per train: 2025-12 = 9.9.0 · 2026-03/06 = 9.9.1 · 2026-09 = 9.10.1.

## Fix (2026-10-06)

1. `releng/llmpeon-update-site/pom.xml`: removed `includeAllDependencies=true` — the repository now
   ships only the feature + plugin; platform/third-party bundles never enter our p2 repository again.
2. `MANIFEST.MF`: `jakarta.annotation [3.0.0,4.0.0)` → `[2.0.0,4.0.0)`. 2026-03 has only {1.3.5,
   2.1.1}; the asm leak used to bring 3.0.0 along — without it the plugin would no longer resolve on
   2026-03. The annotations used (@PostConstruct/@PreDestroy/@Nonnull/@Nullable) are unchanged in
   2.1.1.
3. `ui.console [3.16.0,…)`→`[3.15.0,…)`, `workbench.texteditor [3.20.0,…)`→`[3.19.0,…)` and target
   `2026-09`→`2025-12` (homepage minimum): build against the *oldest* supported platform. Those two
   ranges had only been calibrated to 2026-03; no API is missing (javap: `EvaluationManager`,
   `JDIDebugModel`, `ILaunch.getDebugTargets`, `IJavaThreadGroup` identical between 2025-12 and
   2026-09).

## Verification

- `mvn clean install` against 2025-12: BUILD SUCCESS.
- Generated p2 repository: only `org.sterl.llmpeon` + `llmpeon-feature`; 0 hits for
  asm/objectweb/spifly/jsvg.
- OSGi suite 313: 2 failures — both Windows-environment (CRLF), target-independent
  (`DebugSessionThreadsTest` Jackson pretty-print CRLF vs. `\n`; `EclipseSearchFilesToolTest`
  `split("\n")` on CRLF output). Core 1051 with the same 8 F/6 E as on unmodified HEAD
  (Windows path/timing) — not caused by this fix.

## Second manifestation: `jakarta.annotation-api 3.0.0` → AI Peon view blank (2026-10-07)

The same `includeAllDependencies` leak also brought `jakarta.annotation-api 3.0.0` along with asm
9.10.1. After the asm cleanup it remained as an **orphan** in the installation (file + `bundles.info`
+ `artifacts.xml`, no longer in the p2 profile). Result: Eclipse starts, but the view stays blank,
with no log entry.

- `org.eclipse.e4.core.di` imports `jakarta.annotation [2,3)` → binds **2.1.1**.
- `org.sterl.llmpeon` imports `[2.0.0,4.0.0)` → binds the **higher 3.0.0**.
- e4 matches `@PostConstruct` by **annotation `Class` identity**
  (`AnnotationProxy.isPresent` → `isAnnotationPresent(Class)`) → two different `PostConstruct`
  classes → `AIChatView.createPartControl` is never called (the constructor runs, the lifecycle does
  not — confirmed via the debugger stack `ReflectionContributionFactory → make`).

Removal + guide for affected users:
[troubleshooting-stale-platform-bundles.md](troubleshooting-stale-platform-bundles.md) +
script `docs/clear-stale-platform-bundles.ps1`.

> Note: the wide range `[2.0.0,4.0.0)` is required (2026-03 has only 2.1.1, 2026-09 only 3.0.0);
> the divergence occurs only with **two** providers (a leftover). Protection therefore lives in the
> update site/CI, not in a version range.

## Follow-ups

- [ ] Public correcting comment on issue #142 (I cannot post it).
- [x] Homepage minimum version = 2025-12 (already correct).
- [x] User guide + script for stale platform bundles (`docs/troubleshooting-stale-platform-bundles.md`).
- [x] CI assert: update site ships only `org.sterl.*` plugins.

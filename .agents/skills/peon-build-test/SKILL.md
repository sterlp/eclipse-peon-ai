---
name: peon-build-test
description: llmpeon repo build & test procedure — core→plugin jar trap (reactor vs ~/.m2 store model, when install is required), plugin/OSGi test runs (persistent agents only via getAgents(), not get(name)), headless Tycho, stale artifacts (bin/, m2e, Lombok), dependency whitelist ritual, Surefire number summing, accepted warning list in known-benign-warnings.md.
---

# Build & test — llmpeon repo (migrated from AGENTS-DEV.md, 2026-10-20)

## Build basics

- Full build at the repo root (`llmpeon-parent`): `mvn clean verify`; `install` only when `~/.m2` must
  hold the modules (route B below). Maven is for code/artefact changes — afterwards refresh +
  clean-build the Eclipse projects.
- **Eclipse project name for core is `llmpeon-core`, not `org.sterl.llmpeon.core`** —
  `eclipseBuildProject`/`eclipseRenameResource` need `/llmpeon-core/...`. The neighbouring modules *do*
  carry their artifact name (`org.sterl.llmpeon`, `org.sterl.llmpeon.test`), so the pattern is not
  guessable; Maven still uses `-pl org.sterl.llmpeon.core`.

## Core → Plugin jar trap (the big one)

- **Mechanism:** core changes are INVISIBLE to the Eclipse plugin build — neither the plugin nor the
  test fragment compiles against the workspace core project; core's classes ride inside the bundle as
  `lib/llmpeon-core.jar` (MANIFEST `Bundle-ClassPath`), and the target platform is NOT involved
  (`llmpeon.target` = Eclipse RCP only). That jar is refreshed *only* by a Maven build of the **plugin
  module** (`maven-dependency-plugin:copy-deps`, phase `process-resources`, `org.sterl.llmpeon/pom.xml`).
  `eclipseBuildProject` never copies → alone it builds against the STALE jar and yields phantom
  "constructor/method undefined" / "cannot be resolved" errors for brand-new core symbols
  (hit 2026-08-29: a plan claimed "`eclipseBuildProject` is enough" and failed exactly there; a plan
  touching core MUST carry a Maven step).
- **Two stores — this is where it keeps failing.** The plugin build resolves `llmpeon-core` from
  whichever store it can see:
  - **reactor** — exists only inside the same Maven run (`verify`/`package`, or `-am`); jar in
    `org.sterl.llmpeon.core/target/`, **`~/.m2` stays untouched**.
  - **`~/.m2`** — the persistent store, written **only by `mvn install`** (a `-SNAPSHOT` overwrites
    the previous copy there); this is what every plugin-only build resolves.
  Both breaks: root `clean verify` followed by a plugin-only build copies the **old** snapshot;
  `install` without a following plugin build leaves `lib/` unchanged.
- **After any core change, pick ONE route and finish it:**
  - **A — single reactor run (preferred):** `mvn clean verify -DskipTests` at the repo root;
    scoped/offline: `mvn -o clean verify -DskipTests -pl org.sterl.llmpeon,releng/llmpeon-target -am`
    (`releng/llmpeon-target` must stay in `-pl` — offline the target-platform artifact is not in
    `~/.m2`). Core resolves from the reactor, **no `install` needed**. `-DskipTests` = fast jar only,
    **not** a test gate — the core test gate stays the `-pl org.sterl.llmpeon.core` run below.
  - **B — plugin only:** `mvn -pl org.sterl.llmpeon.core install` **then**
    `mvn clean verify -pl org.sterl.llmpeon,releng/llmpeon-target` — the second command *is* the copy
    step; both halves are mandatory.
  Afterwards refresh + build `org.sterl.llmpeon` AND `org.sterl.llmpeon.test` in Eclipse so they pick
  up the changed jar.
- **`clean` belongs in both routes.** `maven-clean-plugin` (`org.sterl.llmpeon/pom.xml`) deletes `lib/`
  and `sources/`, so `copy-dependencies` repopulates from scratch. Core itself is `-SNAPSHOT` with
  `overWriteSnapshots=true` and refreshes either way — what `clean` really prevents are **orphans**:
  jars of groups that dropped off the `includeGroupIds` whitelist survive in `lib/`, stay on
  `Bundle-ClassPath`/`bin.includes` and keep shipping.
- **Core single-class gate runs offline WITHOUT `-am` (hit 2026-10-23):** core is plain Maven —
  `mvn -o -B clean test -pl org.sterl.llmpeon.core -Dtest='ClassA,ClassB'` runs exactly those classes;
  no reactor needed. KEEP `clean` — without it the m2e auto-build/Lombok trap below yields phantom
  testCompile failures.

## Plugin tests (OSGi, JUnit 4)

- `org.sterl.llmpeon.test` runs via the Eclipse test runner. A NEW test class needs the user's manual
  workspace approval once and may time out if he is not watching → prefer running the whole plugin
  test project, which is already approved.
- Before EVERY test run: `eclipseBuildProject` on all changed projects — stale bundle classes in `bin/`
  cause `ClassNotFoundException` / unresolved-compilation failures, and stale Surefire reports under
  `target/` mislead result reading.
- **Headless plugin suite — name the host explicitly (hit 2026-09-19):** `-pl org.sterl.llmpeon.test
  -am` does **not** pull the host plugin into the reactor (the fragment requires it as an OSGi bundle,
  not a Maven dependency) → stale local-p2 host → phantom `NoSuchMethodError` on green code
  (2026-09-17 R-SEL-4, `UserContext.setTextSelection`). Correct: `-pl
  org.sterl.llmpeon,org.sterl.llmpeon.test -am verify`.
- **SWT tests are skipped headless (hit 2026-10-23):** Tycho-surefire runs without a workbench
  display, so every `AbstractSwtUiTest` subclass is `Assume`-skipped (visible as ~50 skips). A widget
  test is therefore only *compiled* by `mvn … verify` — run it once with `eclipseRunJavaTests` against
  the live workbench and prove its mutation there; never count headless skips as coverage.
- **Persistent agents are invisible to `getAgent(name)` (hit 2026-10-24, Story 3):** `AgentService.get(name)`
  reads only the `agents` map of *loaded custom* agents (`AgentService.java:227-230`), never `persistentAgents`
  — the built-ins (Scaffold, PO, Dev/Plan/Review) come back `Optional.empty()`, which reads like "not wired"
  and invites a false STOP-AND-ASK. Reach them through the public list, which unions both maps
  (`AgentService.java:119-121`) — `getAgents().stream().filter(AiScaffoldAgent.class::isInstance)
  .findFirst().orElseThrow()`. Evidence: `PeonAiServiceTest.scaffoldWriteValidator_acceptsHomeUserAgentsRoot`
  (`d9c703cd`) — the first attempt via `get(name)` was a non-finding, the `getAgents()` filter is what proves
  the wiring.
- **Maven Surefire is the ground truth for test numbers**, not `eclipseRunJavaTests` — the Eclipse
  runner counts higher (parameterized artefacts) and has repeatedly produced wrong report numbers.

## Merge verification

- Verify merge content via tree-diff (`git merge-tree` / `git diff --cached HEAD`), never by commit
  counts — squash PRs make counts meaningless (2026-09-19: 25 + 7 commits, zero content delta).

## Stale artifacts

- **Headless Tycho ships stale `bin/` (hit 2026-10-19):** Tycho packages from the PDE output folder
  `bin/`, which `mvn clean` never touches; a disk-tool source write without a following
  `eclipseRefreshProject` leaves class-mtime > source-mtime, so JDT skips the recompile and the
  headless build silently ships an intermediate-state class. Fix: delete `bin/` or refresh + rebuild
  first; verify with `javap` on the `bin/` class.
- **m2e stale model after `build.properties` edits (hit 2026-09-10):** m2e caches the Tycho project
  model at pom import time; editing `build.properties` alone does not re-parse it. Symptom: IDE build
  fails in the tycho package-plugin with stale `bin.includes` ("[lib/old.jar] do not match any files")
  although the file on disk is correct and headless `mvn ... package` succeeds. Fix: delete
  `<project>/.settings/org.eclipse.m2e.core.prefs` +
  `<workspace>/.metadata/.plugins/org.eclipse.m2e.core/<project>.lifecyclemapping`, then
  `eclipseRefreshProject` + rebuild.
- **m2e auto-build breaks Lombok (hit 2026-09-01):** the `llmpeon-core` project's `.classpath` output
  folder is `target/classes` — the SAME folder Maven uses. An Eclipse/m2e auto-build after `eclipse*`
  file edits recompiles all main classes WITHOUT Lombok annotation processing. Symptom: `mvn compile`
  says "Nothing to compile - all classes are up to date", then `testCompile` fails in ~17 test files
  with phantom "constructor not applicable" errors (e.g. `SimpleContextItem` 2-arg from
  `@RequiredArgsConstructor`). Fix: `mvn -pl org.sterl.llmpeon.core clean compile` (or `clean test`)
  before the gate run.

## Dependencies

- External JARs land in `lib/` via `maven-dependency-plugin`; `MANIFEST.MF` `Bundle-ClassPath`,
  `build.properties` `bin.includes` and `.classpath` must list the **same** JARs.
- Whitelist only the needed groupIds via `includeGroupIds`. Platform-provided JARs (jakarta, osgi,
  jna, asm, jetty, felix, …) must **not** be in `lib/` — they come from the target platform.
- **Ritual after EVERY lib bump (hit 2026-09-10):** diff `mvn dependency:tree` groups against the
  `includeGroupIds` whitelist — a missing group means the jar silently never lands in `lib/` → runtime
  `ClassNotFoundException` on an untested path (transitive `io.smallrye.reactive:mutiny-zero` arrived
  unwhitelisted once). Decide each new group explicitly: needed → whitelist; provably unreferenced in
  this bundle → leave out and say so in the report.

## Test numbers (macOS)

- On macOS, sum Surefire reports with Perl rather than GNU-only `awk match(..., array)`:
  `perl -ne 'if (/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/) {$t+=$1;$f+=$2;$e+=$3;$s+=$4} END {print "Tests=$t Failures=$f Errors=$e Skipped=$s\n"}' target/surefire-reports/*.txt`

## Known-benign warnings — do NOT re-triage every cycle

Accepted exceptions live in [known-benign-warnings.md](known-benign-warnings.md) — read it BEFORE
triaging, then fix real NEW warnings only: plugin null-type-safety on method refs, the `resources/`
class-folder (must stay), `MockLlmServer` try-with-resources variable, ~150 pre-existing core
IDE-scope JDT warnings, `-Xlint`-only diagnostics. Newly accepted exceptions go into that file.

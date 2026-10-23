---
name: peon-build-test
description: llmpeon repo build & test procedure — core→plugin jar trap, plugin/OSGi test runs, headless Tycho, stale artifacts (bin/, m2e, Lombok), dependency whitelist ritual, Surefire number summing, known-benign warning list.
---

# Build & test — llmpeon repo (migrated from AGENTS-DEV.md, 2026-10-20)

## Build basics

- Full build: `mvn clean verify` or `mvn clean install` at the repo root (`llmpeon-parent`) — an
  Eclipse refresh + clean build afterwards is needed; build with maven only for code/artefact changes.
- `mvn clean install` makes the artifacts available for partial module builds.

## Core → Plugin jar trap (the big one)

- **Core changes in `org.sterl.llmpeon.core` are INVISIBLE to the Eclipse plugin build** — the
  plugin (and the test fragment) never compile against the workspace core project: core's classes
  ride inside the plugin bundle as `lib/llmpeon-core.jar` (MANIFEST `Bundle-ClassPath`), a copy of
  the Maven artifact that only `maven-dependency-plugin:copy-dependencies` refreshes (see `pom.xml`).
  The target platform is NOT involved (`llmpeon.target` contains only Eclipse RCP). `eclipseBuildProject`
  alone therefore compiles against the STALE jar → phantom "constructor/method undefined" errors for
  brand-new core symbols (hit 2026-08-29: a plan's "eclipseBuildProject is enough" verification step
  failed exactly like this — a plan touching core MUST carry the Maven step below).
- After ANY core change, before the Eclipse plugin build/test run: `mvn -o -pl
  org.sterl.llmpeon,releng/llmpeon-target -am package -DskipTests` — `-am` rebuilds core in the
  reactor and re-copies the jar into `lib/`; `releng/llmpeon-target` must stay in `-pl` (offline the
  target-platform artifact is not in `~/.m2`). Afterwards refresh + build `org.sterl.llmpeon` AND
  `org.sterl.llmpeon.test` in Eclipse so they pick up the changed jar. Without `-am` the copy
  resolves core from a stale `~/.m2` copy → phantom "cannot be resolved" errors for brand-new core
  symbols. (A full `mvn clean install` at the root also works but is much slower.)
- **Core single-class gate works offline WITHOUT `-am` (hit 2026-10-23, docs-slim cycle):** core is
  plain Maven — `mvn -o -B clean test -pl org.sterl.llmpeon.core -Dtest='ClassA,ClassB'` runs exactly
  those classes (evidence: `DocRootDiscoveryTest,DocsLinterToolTest` → BUILD SUCCESS, 27/0). No
  `-am`, no reactor needed. KEEP `clean` — without it the m2e auto-build/Lombok trap below yields
  phantom testCompile failures.

## Plugin tests (OSGi, JUnit 4)

- `org.sterl.llmpeon.test` via the Eclipse test runner.
- Before EVERY test run call `eclipseBuildProject` (all changed projects) — stale bundle classes in
  `bin/` cause `ClassNotFoundException` / unresolved-compilation failures, and stale Surefire reports
  under `target/` mislead result reading.
- A new test class needs manual workspace approval once by the user and may time out if he is not
  watching — prefer to run all tests in the plugin test project, which is already approved.
- **Headless plugin suite — name the host explicitly (hit 2026-09-19, read-numbers inc-3):**
  `-pl org.sterl.llmpeon.test -am` does **not** pull the host plugin into the reactor (the fragment
  requires it as an OSGi bundle, not a Maven dependency) → stale local-p2 host → phantom
  `NoSuchMethodError` (2026-09-17 R-SEL-4 case: `UserContext.setTextSelection`, 27 F / 37 E on green
  code). Correct: `-pl org.sterl.llmpeon,org.sterl.llmpeon.test -am verify`. Same discipline for
  merges: verify content via tree-diff (`git merge-tree` / `git diff --cached HEAD`), not commit
  counts — squash PRs make counts meaningless (2026-09-19: 25 + 7 commits, zero content delta).
- **Maven Surefire is the ground truth for test numbers**, not `eclipseRunJavaTests` — the Eclipse
  runner counts higher (parameterized artefacts) and has repeatedly produced wrong report numbers.

## Stale artifacts

- **Headless Tycho ships stale `bin/` (hit 2026-10-19, R-CG-2 OSGi gate):** Tycho packages from the
  PDE output folder `bin/` — `mvn clean` never touches it, and a disk-tool source write without a
  following `eclipseRefreshProject` leaves class-mtime > source-mtime, so JDT skips the recompile and
  the headless build silently ships an intermediate-state class (verify with `javap` on the `bin/`
  class). Fix: delete `bin/` or refresh + rebuild first.
- **m2e stale model after `build.properties` edits (hit 2026-09-10, lib-update inc-3):** m2e caches
  the Tycho project model at pom import time; editing `build.properties` alone does not re-parse it.
  Symptom: IDE build fails in the tycho package-plugin with stale `bin.includes` ("[lib/old.jar] do
  not match any files") although the file on disk is correct and headless `mvn ... package` succeeds.
  Fix: delete `<project>/.settings/org.eclipse.m2e.core.prefs` +
  `<workspace>/.metadata/.plugins/org.eclipse.m2e.core/<project>.lifecyclemapping`, then
  `eclipseRefreshProject` + rebuild.
- **m2e auto-build breaks Lombok (hit 2026-09-01, inc-24):** the `llmpeon-core` project's
  `.classpath` output folder is `target/classes` — the SAME folder Maven uses. An Eclipse/m2e
  auto-build after `eclipse*` file edits recompiles all main classes WITHOUT Lombok annotation
  processing. Symptom: `mvn compile` says "Nothing to compile - all classes are up to date" (classes
  newer than sources), then `testCompile` fails in ~17 test files with phantom "constructor not
  applicable" errors (e.g. `SimpleContextItem` 2-arg from `@RequiredArgsConstructor`). Fix:
  `mvn -pl org.sterl.llmpeon.core clean compile` (or `clean test`) before the gate run.

## Dependencies

- External JARs land in `lib/` via `maven-dependency-plugin`; `MANIFEST.MF` `Bundle-ClassPath`,
  `build.properties` `bin.includes` and `.classpath` must list the **same** JARs.
- Whitelist only the needed groupIds via `includeGroupIds`. Platform-provided JARs (jakarta, osgi,
  jna, asm, jetty, felix, …) must **not** be in `lib/` — they come from the target platform.
- **Ritual after EVERY lib bump** (hit 2026-09-10, lib-update inc-3: transitive
  `io.smallrye.reactive:mutiny-zero` arrived unwhitelisted): diff `mvn dependency:tree` groups
  against the `includeGroupIds` whitelist — a missing group means the jar silently never lands in
  `lib/` → runtime `ClassNotFoundException` on an untested path. Decide each new group explicitly
  (needed → whitelist; provably unreferenced in this bundle → leave out, say so in the report).

## Test numbers (macOS)

- On macOS, sum Surefire reports with Perl rather than GNU-only `awk match(..., array)`:
  `perl -ne 'if (/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/) {$t+=$1;$f+=$2;$e+=$3;$s+=$4} END {print "Tests=$t Failures=$f Errors=$e Skipped=$s\n"}' target/surefire-reports/*.txt`

## Known-benign warnings — do NOT re-triage every cycle (2026-09-10 cleanup: 64 → 12)

(2026-09-10, warning-cleanup cycle, commits `51f43d2`/`a9124f1`/`56e9cc5`). The remaining 13 are
accepted exceptions; fix real new ones, keep this list current:

- Plugin ×11 null-type-safety on method refs (`AIChatView:175-176`, `PeonAiService:469-470,508,639`,
  `AiAdvancedPreferenceView:120`, `ModelComboWidget:128`, `EclipseUtil:318`,
  `EclipseWorkspaceReadFileTool:158`, `StatusLineWidget:188`) — method refs to `@NonNull`-parameter
  functional interfaces; internal callers never pass null. (2026-09-12: `AiAgentStatusModel:48`
  moved with the agent-status module into core — now part of the core IDE-scope warnings above.
  2026-10-17 codegraph build: the list carried 9, the build shows 11 — `PeonAiService clearAll:
  AiAgent::clear` and `AiAdvancedPreferenceView:120` are pre-existing (verified at HEAD), just never
  listed. Line numbers drift with edits.)
- Plugin ×1 `resources/` class-folder (`.classpath` mirrors `Bundle-ClassPath`) — **must stay**:
  `ChatMarkdownWidget` loads `chat.html` via classloader AND OSGi `FileLocator`; dropping the entry
  risks breaking the chat view at IDE runtime.
- Core ×1 `MockLlmServer:98` unused TWR variable — needed for auto-close; variable-less
  try-with-resources is invalid Java (JLS 14.20.3). **javac-lint only — not visible in the Eclipse
  problems view.**
- Core IDE scope note (2026-09-10, Da-Dok review): the `llmpeon-core` IDE view additionally carries
  ~150 **pre-existing** JDT warnings (null-type-safety/unused-import) that are NOT part of the
  cleanup scope above — they never appear in the Maven gate. Sweep = own micro-cycle decision
  (PO + user), not an obligation of every cycle.
- `-Xlint`-diagnostic only (not in default build/IDE): this-escape ×10 (7 classes, none subclassed —
  intentional constructor-delegates-to-refresh pattern); opennlp-tools manifest `Class-Path` slf4j
  path quirk (upstream packaging).

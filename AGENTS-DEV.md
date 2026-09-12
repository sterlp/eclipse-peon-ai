# AGENTS-DEV.md — implementing (the HOW)

Hints for the dev phase, base rules `AGENTS.md`

- `mvn clean install` makes the artifacts available for partial module builds.

## Dependencies

- External JARs land in `lib/` via `maven-dependency-plugin`; `MANIFEST.MF` `Bundle-ClassPath`,
  `build.properties` `bin.includes` and `.classpath` must list the **same** JARs.
- Whitelist only the needed groupIds via `includeGroupIds`. Platform-provided JARs (jakarta,
  osgi, jna, asm, jetty, felix, …) must **not** be in `lib/` — they come from the target
  platform.
- **Ritual after EVERY lib bump** (hit 2026-09-10, lib-update inc-3: transitive
  `io.smallrye.reactive:mutiny-zero` arrived unwhitelisted): diff `mvn dependency:tree` groups
  against the `includeGroupIds` whitelist — a missing group means the jar silently never lands
  in `lib/` → runtime `ClassNotFoundException` on an untested path. Decide each new group
  explicitly (needed → whitelist; provably unreferenced in this bundle → leave out, say so in
  the report).

## Build & test

- Full build: `mvn clean verify` or `mvn clean install` at the repo root (`llmpeon-parent`) — an Eclipse refresh +
  clean build afterwards is needed - build with maven only for code/artefact changes.
- **Core changes in `org.sterl.llmpeon.core` are INVISIBLE to the Eclipse plugin build** — the
  plugin (and the test fragment) never compile against the workspace core project: core's classes
  ride inside the plugin bundle as `lib/llmpeon-core.jar` (MANIFEST `Bundle-ClassPath`), a copy of
  the Maven artifact that only `maven-dependency-plugin:copy-dependencies` refreshes (see `pom.xml`).
  The target platform is NOT involved (`llmpeon.target` contains only Eclipse RCP). `eclipseBuildProject`
  alone therefore compiles against the STALE jar → phantom "constructor/method undefined" errors for
  brand-new core symbols (hit 2026-08-29: a plan's "eclipseBuildProject is enough" verification step
  failed exactly like this — a plan touching core MUST carry the Maven step below).
- Plugin tests: `org.sterl.llmpeon.test` via the Eclipse test runner (OSGi, JUnit 4).
  - Before EVERY test run call `eclipseBuildProject` (all changed projects) — stale bundle
    classes in `bin/` cause `ClassNotFoundException` / unresolved-compilation failures, and
    stale Surefire reports under `target/` mislead result reading.
  - A new test class needs manual workspace approval once by the user and may time out if he is not
    watching — prefer to run all tests in the plugin test project, which is already approved.
- After ANY core change, before the Eclipse plugin build/test run: `mvn -o -pl
  org.sterl.llmpeon,releng/llmpeon-target -am package -DskipTests` — `-am` rebuilds core in the
  reactor and re-copies the jar into `lib/`; `releng/llmpeon-target` must stay in `-pl` (offline the
  target-platform artifact is not in `~/.m2`). Afterwards refresh + build `org.sterl.llmpeon` AND
  `org.sterl.llmpeon.test` in Eclipse so they pick up the changed jar. Without `-am` the copy
  resolves core from a stale `~/.m2` copy → phantom "cannot be resolved" errors for brand-new core
  symbols. (A full `mvn clean install` at the root also works but is much slower.)
- **m2e stale model after `build.properties` edits (hit 2026-09-10, lib-update inc-3):**
  m2e caches the Tycho project model at pom import time; editing `build.properties` alone does
  not re-parse it. Symptom: IDE build fails in the tycho package-plugin with stale
  `bin.includes` ("[lib/old.jar] do not match any files") although the file on disk is correct
  and headless `mvn ... package` succeeds. Fix: delete
  `<project>/.settings/org.eclipse.m2e.core.prefs` +
  `<workspace>/.metadata/.plugins/org.eclipse.m2e.core/<project>.lifecyclemapping`, then
  `eclipseRefreshProject` + rebuild.
- **m2e auto-build breaks Lombok (hit 2026-09-01, inc-24):** the `llmpeon-core` project's
  `.classpath` output folder is `target/classes` — the SAME folder Maven uses. An Eclipse/m2e
  auto-build after `eclipse*` file edits recompiles all main classes WITHOUT Lombok annotation
  processing. Symptom: `mvn compile` says "Nothing to compile - all classes are up to date"
  (classes newer than sources), then `testCompile` fails in ~17 test files with phantom
  "constructor not applicable" errors (e.g. `SimpleContextItem` 2-arg from
  `@RequiredArgsConstructor`). Fix: `mvn -pl org.sterl.llmpeon.core clean compile` (or `clean test`)
  before the gate run.
- **Known-benign warnings — do NOT re-triage every cycle** (2026-09-10, warning-cleanup cycle:
  64 → 12 problems, commits `51f43d2`/`a9124f1`/`56e9cc5`). The remaining 12 are accepted
  exceptions; fix real new ones, keep this list current:
  - Plugin ×10 null-type-safety on method refs (`AIChatView:162-163`, `PeonAiService:401-402,489`,
    `ModelComboWidget:122`, `EclipseUtil:318`, `EclipseWorkspaceReadFileTool:155`,
    `AiAgentStatusModel:48`, `StatusLineWidget:188`) — method refs to `@NonNull`-parameter
    functional interfaces; internal callers never pass null.
  - Plugin ×1 `resources/` class-folder (`.classpath` mirrors `Bundle-ClassPath`) — **must stay**:
    `ChatMarkdownWidget` loads `chat.html` via classloader AND OSGi `FileLocator`; dropping the
    entry risks breaking the chat view at IDE runtime.
  - Core ×1 `MockLlmServer:98` unused TWR variable — needed for auto-close; variable-less
    try-with-resources is invalid Java (JLS 14.20.3). **javac-lint only — not visible in the
    Eclipse problems view.**
  - Core IDE scope note (2026-09-10, Da-Dok review): the `llmpeon-core` IDE view additionally
    carries ~150 **pre-existing** JDT warnings (null-type-safety/unused-import) that are NOT
    part of the cleanup scope above — they never appear in the Maven gate. Sweep = own
    micro-cycle decision (PO + user), not an obligation of every cycle.
  - `-Xlint`-diagnostic only (not in default build/IDE): this-escape ×10 (7 classes, none
    subclassed — intentional constructor-delegates-to-refresh pattern); opennlp-tools
    manifest `Class-Path` slf4j path quirk (upstream packaging).
- Elegant, expressive modern Java (records, pattern matching, switch expressions, Lombok).
- **OSGi test constraints:** plugin tests are JUnit 4, new test classes need user approval.
  Run full test suite on timeout
- On macOS, sum Surefire reports with Perl rather than GNU-only `awk match(..., array)`:
  `perl -ne 'if (/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/) {$t+=$1;$f+=$2;$e+=$3;$s+=$4} END {print "Tests=$t Failures=$f Errors=$e Skipped=$s\n"}' target/surefire-reports/*.txt`.
- Prefer a few high-value assertions over many brittle ones; every assertion should earn its maintenance cost.

## Test honesty (learned 2026-09-03, cycles 2b-1…3b)

These bit us repeatedly in this repo — check them before reporting an increment green:

- **A test that would also pass without the feature is not a test.** Recurring shapes here:
  cleanup in `finally` running *before* the assertion, assertions on a constant suffix only, or
  a test that builds the object graph *directly* and thereby bypasses the resolution chain it
  claims to prove.
- **Prove falsifiability per test, naming the path the mutation hits** — not "all red under
  mutation X". A mutation that only flips the filter says nothing about the cropping.
- **If a test is green before the fix, say so before building it.** Declare it a
  characterization/regression test in the plan; never present it as proof of the rule. Reporting
  this as a blocker is the correct move, not a failure.
- **Never widen scope silently.** Fixes outside the released plan — even correct ones, even
  one-liners — get reported to the PO first. TABU lists in a plan are binding. (Origin: in cycle
  2b-2 the dev shipped three extra, factually correct fixes in a file the plan had marked TABU;
  the fixes were fine, the surprise was not. Since then every plan carries a TABU list and this
  rule.)

## Repo-specific API traps (verified, don't re-derive)

- `PlatformUI.getWorkbench()` never returns `null` (it throws `IllegalStateException`) — use
  `PlatformUI.isWorkbenchRunning()`.
- An `IProject` is never `instanceof IJavaProject` — use `JavaCore.create(project)` + `exists()`.
- `IProject.isOpen()` already returns `false` for a project that does not exist; no extra
  `exists()` guard needed.
- `IResource.refreshLocal` is inherited by `IContainer`; it is long-running and throws
  `CoreException` → run off the UI thread, log **or** throw, never both.
- langchain4j serializes `customParameters` via `@JsonAnyGetter` **next to** the typed fields:
  the same key in the extra body produces a **duplicate JSON key**, not an override. "User body
  wins" must be implemented explicitly by clearing the typed field
  (see `docs/adr/0039-temperature-body-precedence.md`).
- `CompletableFuture.get()` on a future **you cancelled yourself** throws `CancellationException`
  **unwrapped**, not wrapped in `ExecutionException` — a catch on `ExecutionException` silently
  misses it (this hid the model-list race, see `docs/adr/0040-model-list-single-flight-secret-masking.md`).
- More Eclipse-platform know-how lives in the project skill `eclipse-dpe` (read it via skillRead
  before guessing) — append new findings **at the end of the file** (do not split an existing bullet).
- Skill-Evolution (experimentell): every skillRead result ends with a usefulness footer — **always
  answer it in your report** (helpful? wrong/outdated/incomplete? obsolete?). If a skill you just
  read is wrong or outdated and you can fix it in place, do it in the same turn (keep it short);
  otherwise report the gap so Jon routes it. Skill changes follow `skill-evolution` (evidence
  required, keep skills short).
  
## Reference projects for API help

1. use github eclipse plugin AI harness if problems or question arise which cannot be answered 
   by the API itself eclipse: /github-copilot-for-eclipse
2. /langchain4j-aggregator for langchain4j code & docs (*.md), if the API itself is not enough
3. Opencode source - cli AI harness: /opencode -- for generall idea how AI harnesses are build

Use search agents to search these big repos - do direct reads only. Considerer proposing SKILL changes
or new skills for extracted patterns or solutions.
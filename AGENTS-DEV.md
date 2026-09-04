# AGENTS-DEV.md — implementing (the HOW)

Hints for the dev phase, base rules `AGENTS.md`

- `mvn clean install` makes the artifacts available for partial module builds.
- **Never write to `docs/`** — owned by the PO + the user; the story's ❌ → ✅ flip is left to
  the docs owner. Track progress only in the plan file and the task files you create.
- **User docs (homepage / VitePress):** `homepage/` is the published user documentation,
  separate from `docs/`. Source in `homepage/src` (`srcDir` in `homepage/.vitepress/config.ts`);
  build via `homepage/build-docs.sh`. New page → update the sidebar/nav in
  `homepage/.vitepress/config.ts`. A user-facing page is added only once the feature ships
  (rule ✅) — never document unbuilt behaviour to users.
  
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
- **m2e auto-build breaks Lombok (hit 2026-09-01, inc-24):** the `llmpeon-core` project's
  `.classpath` output folder is `target/classes` — the SAME folder Maven uses. An Eclipse/m2e
  auto-build after `eclipse*` file edits recompiles all main classes WITHOUT Lombok annotation
  processing. Symptom: `mvn compile` says "Nothing to compile - all classes are up to date"
  (classes newer than sources), then `testCompile` fails in ~17 test files with phantom
  "constructor not applicable" errors (e.g. `SimpleContextItem` 2-arg from
  `@RequiredArgsConstructor`). Fix: `mvn -pl org.sterl.llmpeon.core clean compile` (or `clean test`)
  before the gate run.
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
- More Eclipse-platform know-how lives in `skills/eclipse-dpe/SKILL.md` — read it before
  guessing, and append new findings **at the end of the file** (do not split an existing bullet).
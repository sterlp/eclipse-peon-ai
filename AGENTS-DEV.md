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
  - A new test class needs manual workspace approval by the user and may time out if he is not
    watching — prefer the already approved suite.
- After ANY core change, before the Eclipse plugin build/test run: `mvn -o -pl
  org.sterl.llmpeon,releng/llmpeon-target -am package -DskipTests` — `-am` rebuilds core in the
  reactor and re-copies the jar into `lib/`; `releng/llmpeon-target` must stay in `-pl` (offline the
  target-platform artifact is not in `~/.m2`). Afterwards refresh + build `org.sterl.llmpeon` AND
  `org.sterl.llmpeon.test` in Eclipse so they pick up the changed jar. Without `-am` the copy
  resolves core from a stale `~/.m2` copy → phantom "cannot be resolved" errors for brand-new core
  symbols. (A full `mvn clean install` at the root also works but is much slower.)
- Elegant, expressive modern Java (records, pattern matching, switch expressions, Lombok).
- **OSGi test constraints:** plugin tests are JUnit 4, new test classes need user approval.
  Run full test suite on timeout
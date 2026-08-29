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

- Full build: `mvn clean verify` at the repo root (`llmpeon-parent`) — an Eclipse refresh +
  clean build afterwards is needed - build with maven only for code/artefact changes.
- **Core changes in `org.sterl.llmpeon.core` needs `mvn clean verify` in the root/parent project** 
  so the plugin/test bundles pick up the new core — Tycho resolves core from the target platform, 
  not the reactor.
- Plugin tests: `org.sterl.llmpeon.test` via the Eclipse test runner (OSGi, JUnit 4).
  - Before EVERY test run call `eclipseBuildProject` (all changed projects) — stale bundle
    classes in `bin/` cause `ClassNotFoundException` / unresolved-compilation failures, and
    stale Surefire reports under `target/` mislead result reading.
  - A new test class needs manual workspace approval by the user and may time out if he is not
    watching — prefer the already approved suite.
- Compile-checking the plugin against local core changes: `mvn -o -pl
  org.sterl.llmpeon,releng/llmpeon-target -am package` — `releng/llmpeon-target` must be in the
  `-pl` list (offline the target-platform artifact is not in `~/.m2`; `verify` does not install
  it). Without `-am` Tycho resolves core from a stale target-platform copy and reports phantom
  "cannot be resolved" errors for brand-new core symbols.
- Elegant, expressive modern Java (records, pattern matching, switch expressions, Lombok).
- **OSGi test constraints:** plugin tests are JUnit 4, new test classes need user approval.
  Run full test suite on timeout

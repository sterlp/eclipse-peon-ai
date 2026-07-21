# Dev Phase — the HOW

Load this while implementing. The plan (story + ADRs) is already agreed; here are the code and
project-specific conventions. **If your tool didn't auto-load it, read the always-on base
`AGENTS.md` first** — this file only adds the dev-phase rules on top.

## Handover in
- You implement a **concrete dev-plan** — a coherent slice of **❌** rules from the story.
- If only the docs exist (a docs-only update just landed) and there is **no dev-plan yet**, **push back
  and ask the plan agent for the first dev-plan** — do not code straight from the docs.
- Implement the slice with tests (BDD green), then flip those rules **❌ → ✅** in the story.

<!-- COMMON CODE START -->
## Common Code

### Testing
- **Tests are mandatory** — a rule without a test isn't "done".
- **BDD via JUnit** — Given/When/Then through the service or REST endpoint; single components as
  Mockito unit tests.
- **Every bug gets a regression test, preferably end-to-end/blackbox** via the real entry point
  (e.g. MockMvc): fails without the fix, passes with it.

### Logging
- **Log OR throw, never both** (except facades where the exception leaves the context).
- **Always include context:** the ID/object, the problem, and any known workaround/bugfix.
- **Never test logging** — don't assert on log output or levels.

### Code
- **Lombok** for logging (`@Slf4j`) and constructors (`@RequiredArgsConstructor`).
- **>3 method args -> pass a command object.** A larger command object = plain POJO with Lombok
  `@Builder` (especially when there are default values).
- **Shared code lives in the shared module/package** and stays dependency-free.
- **Dependencies flow one way, never in a circle.**
- Keep units small and well-named; apply Clean Code & SOLID (esp. Open/Closed) where it aids
  maintainability.
- **Comments earn their tokens.** Link from code to the docs/ADRs, and explain the non-obvious *why*
  (domain rules, invariants, gotchas). Never restate what the code already makes clear — such comments
  are only noise and waste tokens.
<!-- COMMON CODE END -->

## Project specifics — Eclipse Peon (RCP)

- **Thread safety:** all code must be thread-safe (`Atomic*` / `ReentrantLock`). No single-threaded
  assumptions.
- Write elegant, expressive modern Java (records, pattern matching, switch expressions).

### Structure & module guides
The 3-bundle layout and the per-module `AGENTS.md` links live in the always-on `AGENTS.md`
(Repo layout) — not repeated here.

### Build
- `mvn clean verify` — `verify` runs the Eclipse plugin tests in `org.sterl.llmpeon.test`.
- Core module tests: `mvn -pl org.sterl.llmpeon.core test`. Other tests via the Eclipse test runner.

### Dependency management
- External JARs copied to `lib/` via `maven-dependency-plugin`; `MANIFEST.MF` `Bundle-ClassPath`,
  `build.properties` `bin.includes` and `.classpath` must list the **same** JARs.
- Only whitelist needed groupIds via `includeGroupIds`. Platform-provided JARs (jakarta, osgi, jna,
  asm, jetty, felix, …) must **not** be in `lib` — they come from the target platform.

### User docs (homepage / VitePress)
- `homepage/` is the published user documentation, separate from `docs/`. Source in `homepage/src`
  (`srcDir` in `homepage/.vitepress/config.ts`); build via `homepage/build-docs.sh`.
- Adding a new page → update the `homepage/.vitepress/config.ts` sidebar/nav.

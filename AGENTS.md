<!-- COMMON RULES START -->
## Common Rules

- **Be concise.** Short, direct chat answers — no filler, no restating the known. The docs still
  capture every decision.
- **One question at a time, with a recommended answer.**
- **Clarify → plan → code.** Don't assume — if it isn't in the docs, ask or request an
  example/BDD/use-case. The feature must be clear and fit the existing docs.

### Response style
- No preamble/postamble — skip "I will…", "Here is…", "Based on…", "Done."
- Answer directly, 1–4 lines unless detail is asked for. Don't repeat what was said; cut words that
  add no meaning.
- Can't help? Offer alternatives in 1–2 sentences — don't moralize.
- `->` denotes a dependency.

### Module structure
Maven multi-module, each module prefixed with the project key (e.g. `<project>-api`):
- **A large feature = its own module.** In large projects the backend holds only config/wiring;
  every feature is its own module.
- **One feature = one name** across its `docs/` page and the module's package/folder.
- `…-api` — code shared between modules. `…-backend` — config + wiring. `…-shared` —
  dependency-free shared code (a backend-only shared stays backend-specific).
- **A module is doc-self-contained.** Every Maven module owns a skeleton — `CONTEXT.md`, `docs/`,
  `adr/` (+ `adr/README.md`) and a `README.md` — created empty up-front, filled as you touch. Root
  `CONTEXT.md` **links** to each module and the module **links back**; root keeps only **cross-cutting**
  docs + ADRs, module-specific ADRs live in the **module's** `adr/` (no re-summarising the module in
  root → no drift). A feature spanning modules: doc + ADR live with the **owning** module, glue only
  links. Exception: `*-test`/support modules get no skeleton.

### Docs-first — the docs are the SOLL/WIE
- **Plan in the docs together; joint planning IS the approval.** Rules, BDD use-cases
  (GIVEN/WHEN/THEN) and ADRs are captured first as the target. While planning decide: new module?
  Check for conflicts with existing rules, fit with current docs.
- **At the end, reconcile the docs with what was built — and compress** (only what helps, never
  echo the code).
- **Capture every rule/decision in the docs** (`adr/` for technical ones), never only in chat.
  `CONTEXT.md` = map/vision + feature registry;
  `docs/<feature>.md` = business requirements + BDD;
  `adr/` = decisions + README.md registry.
- **Broad sweep → find and read every `CONTEXT.md` at once** (root + per-module) to get the full map
  before diving into a single doc.

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
<!-- COMMON RULES END -->

# Global Rules

- **Thread Safety**: All code changes must be thread-safe (`Atomic*` / `ReentrantLock`). No single-threaded assumptions.
- **Testing Strategy**: See module guidelines below for runner specifics (Eclipse vs Shell).
- Write elegant, expressive code using modern Java (records, pattern matching, switch expressions) — readability like good prose.

# Build
- command line `mvn clean verify` - use `verify` to run the eclipse plugin tests of org.sterl.llmpeon.test
- all other tests can be executed using the eclipse test tool runner

# Module Guidelines (Links)

Read these when working in specific modules:
- `/org.sterl.llmpeon/AGENTS.md` — Plugin UI & Logic (Error handling patterns, Job usage).
- `/org.sterl.llmpeon.core/AGENTS.md` — Core logic (Lombok conventions).
- `/org.sterl.llmpeon.test/AGENTS.md` — Test execution specifics.

# Structure eclipse plugin RCP

This is the real module layout — it **overrides** the generic Common `Module structure`: no
`-api/-backend/-shared` split and no per-module `CONTEXT.md`/`docs/`/`adr/` skeleton. Three fixed
OSGi bundles, each with its own `AGENTS.md`:

- org.sterl.llmpeon.core - non eclipse specific code and tests
- org.sterl.llmpeon - eclipse plugin code
- org.sterl.llmpeon.test - eclipse plugin tests

# Dependency Management

- External JARsare copied to `lib/` via `maven-dependency-plugin`
- `MANIFEST.MF` `Bundle-ClassPath`, `build.properties` `bin.includes`, and `.classpath` must all list the **same** JARs
- Only whitelist needed groupIds via `includeGroupIds` - do NOT copy all transitive deps
- Platform-provided JARs (jakarta, osgi, jna, asm, jetty, felix, etc.) must NOT be in `lib` - they come from the target platform

# Docs
- Two doc trees, kept separate:
  - `docs/` — application design & dev spec (the HOW / system reference). **Not** linked to VitePress.
  - `homepage/` — the published VitePress user documentation ("how to use the plugin").
- In plan mode, always plan the matching `docs/` design update too.
- plan also the user-facing doc changes in `homepage/src` - keep it short and explain how to use it.
- VitePress source lives in `homepage/src` (`srcDir` in `homepage/.vitepress/config.ts`); build via `homepage/build-docs.sh`.
- Always update `homepage/.vitepress/config.ts` sidebar/nav when adding new pages to `homepage/src/`.

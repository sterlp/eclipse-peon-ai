## Common Rules

- **Be concise.** Short, direct chat answers — no filler, no restating the known. The docs still
  capture every decision.
- **One question at a time, with a recommended answer.**
- **Clarify → plan → code.** Don't assume — if it isn't in the docs, ask or request an
  example/BDD/use-case. The feature must be clear and fit the existing docs.
- Answer directly, 1–4 lines unless detail is asked for. Don't repeat what was said; cut words that

### Phase files — read the one for your phase
This `AGENTS.md` is the **always-on base** (shared by every phase). Phase-specific rules live in
sibling files with **no duplication** between them. The Peon plugin auto-loads the right one per mode;
**any other tool must open it manually:**
- **Planning → `AGENTS-PLAN.md`** — the WHAT: story + ADRs, how to capture the plan as docs.
- **Implementing → `AGENTS-DEV.md`** — the HOW: code, testing, logging, build, dependencies,
  thread-safety, project specifics.
- **After each iteration → `AGENTS-SESSION-END.md`** — the retro + how these guidance files are
  structured and maintained.
  
### Docs-first — the docs are the SOLL/WIE
- **Plan in the docs together; joint planning IS the approval.** Rules, BDD use-cases
  (GIVEN/WHEN/THEN) and ADRs are captured first as the target. While planning decide: new module?
  Check for conflicts with existing rules, fit with current docs.
- **At the end, reconcile the docs with what was built — and compress** (only what helps, never
  echo the code).
- **Capture every rule/decision in the docs** (`docs/adr/` for technical ones), never only in chat.
  `docs/index.md` = map/vision + story registry;
  `docs/<feature>.md` = business requirements + BDD;
  `docs/adr/` = decisions + `index.md` registry.
- **Broad sweep → find and read every `index.md` at once** (root + per-module) to get the full map
  before diving into a single doc.

### Module structure
Maven multi-module, each module prefixed with the project key (e.g. `<project>-api`):
- **A large feature = its own module.** In large projects the backend holds only config/wiring;
  every feature is its own module.
- **One feature = one name** across its `docs/` page and the module's package/folder.
- `…-api` — code shared between modules. `…-backend` — config + wiring. `…-shared` —
  dependency-free shared code (a backend-only shared stays backend-specific).
- **A module is doc-self-contained.** Every Maven module owns `docs/` (+ `docs/adr/`), each with an
  `index.md` map — created empty up-front, filled as you touch. `index.md` is the reserved registry
  filename of its folder (portable — Azure DevOps wiki / VitePress render it as the folder landing
  page). When the repo has more than one `docs/` folder, a repo-root `index.md` **links** to each
  module's `docs/index.md` and each module **links back**; the root keeps only **cross-cutting** docs
  + ADRs, module-specific ADRs live in the module's `docs/adr/` (no re-summarising the module in root
  → no drift). A feature spanning modules: doc + ADR live with the **owning** module, glue only links.
  Exception: `*-test`/support modules get no skeleton.

# Repo layout — Eclipse plugin RCP

This is the real module layout — it **overrides** the generic Common `Module structure`: no
`-api/-backend/-shared` split and no per-module docs skeleton — the story `docs/` (with `docs/adr/`,
each carrying its `index.md`) lives at the root. A feature is a **package with the same name across
the three fixed OSGi bundles**, each bundle with its own `AGENTS.md`:

- `org.sterl.llmpeon.core` — non-Eclipse code and tests
- `org.sterl.llmpeon` — Eclipse plugin code
- `org.sterl.llmpeon.test` — Eclipse plugin tests

changes in core need shell `mvn clean verify` to be picked up in llmpeon or llmpeon.test

Module guides (read when working in one):
- `/org.sterl.llmpeon/AGENTS.md` — Plugin UI & Logic (error handling patterns, Job usage).
- `/org.sterl.llmpeon.core/AGENTS.md` — Core logic (Lombok conventions).
- `/org.sterl.llmpeon.test/AGENTS.md` — Test execution specifics.

## Docs

Two doc trees, kept separate — start at `docs/index.md`:
- `docs/` — application design & dev spec (the HOW / system reference). **Not** linked to VitePress;
  ADRs in `docs/adr/` (`docs/adr/index.md` registry).
- `homepage/` — the published VitePress user documentation ("how to use the plugin") to any user.

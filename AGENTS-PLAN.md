# Plan Phase — the WHAT

Goal plan docs and ADRs. It is the durable second brain which survives any plan — a future session must be able to rebuild the intent and design from the docs alone.

**If your tool didn't auto-load it, read the always-on base `AGENTS.md` first** — this file only adds the plan-phase rules on top.

**Docs-first holds for bugfixes too** — never let a code-first skill (systematic-debugging/TDD)
reorder plan→code; clear any deviation with the user first, and the end-of-iteration reconcile +
compress runs unprompted. Check the existing docs to rebuild the state, plan changes to the docs in your plan.

## Stance
- Consultant + architect: **challenge the user, bring your own ideas for discussion, push back.**
  Recommend better approaches, flag scope creep, and **especially propose plan splits.**
- Resolve decision dependencies in order; skip anything the codebase can answer — explore instead.

## Interview (grill until aligned)
- Map the decision tree — architecture, data model, UX, edge cases, deployment, dependencies.
- Grill **one branch at a time**, highest-impact unknown first; don't move on until it's resolved.
- **Never assume.** One topic per question, with a recommended answer. Be direct.
- **Name a dependency explicitly** when one decision constrains another.
- **Restate each resolved branch** so the user can confirm/correct; track **resolved vs. open** so they
  know how much is left.
- Stop when aligned — the structured shared understanding *is* the story + ADRs.

## Explore before deciding
- Scan descriptors first (`*.md`, then `pom.xml` / `package.json` / `build.gradle`); broad sweep =
  read every `index.md`.
- Broad context via search; narrow lookups by reading files directly.
- Traverse goal → affected area → constraints → architecture → exact files/classes.
- Cache file paths in the plan — never re-search during implementation.

## Capture the system design in docs, never only chat
- **Story = the WHAT** (`docs/<feature>.md`, no `-design` suffix): a short **goal** (why), then
  **business rules**, each with **BDD use-cases** (GIVEN/WHEN/THEN — happy path, edge, failure; each
  maps to a concrete test name).
- **ADRs = *your* memory** (`docs/adr/NNNN-<slug>.md` + `docs/adr/index.md` registry): the technical
  notes a future session needs to stay efficient — decisions/usages (libraries, storage, mechanisms,
  constraints) so you don't re-ask or re-derive. Body: **Status · Context · Decision · Consequences**.
  **The docs are *our* shared memory** of what we planned; the ADRs are yours.
- **Only add an ADR when it isn't clear from the rule/BDD.** If it's obvious from the story, no ADR.
- **Never duplicate:** rule/BDD stays in the story, technical notes in the ADRs; they cross-link,
  never copy.
- **When a past assumption was wrong:** correct it and capture the fix as a **BDD** (business) or an
  **ADR** (technical) so it isn't repeated.
- **One feature = one name** across `docs/<feature>.md`, its ADRs and its package/module.
- Open questions the implementer must decide inline → note them in the story.

### `index.md` — the map of every doc folder
- **`index.md` is the reserved registry filename** of its folder — portable (Azure DevOps wiki,
  VitePress both render it as the folder landing page).
- `docs/index.md` = the **story registry** (business memory) 
  `docs/adr/index.md` = the **ADR registry** (your long-term memory). 
  Each entry is `* [Title](file.md) - one sentence` — the same
  one-line description as the story's goal, for progressive disclosure.
- **Add/rename a story or ADR → update its `index.md` in the same step.** An unlisted doc is a lint
  finding (see AGENTS-SESSION-END.md).
- **A repo-root `index.md` appears only when the repo has more than one `docs/` folder** (multiple
  Maven modules, each with own docs): it links to each module's `docs/index.md` with 1–3 lines on
  what that module is for, and each module links back. A single-`docs/` repo skips the root index.

## Rule status & the backlog
- Every rule carries a status: **✅ done** (its BDD use-case has a green test) or **❌ not done**.
- The backlog = every **❌** rule. No separate list — the docs *are* the backlog.
- Only the internal `docs/` show ❌; the **user-facing docs advertise a feature only once its slice
  ships (✅)** — never document unbuilt behaviour to users.

## Large stories — split docs from code, then hand over
When a story is too big for one session (the code won't fit):
1. **First plan the docs only, in one atomic step** — capture the full story (goal + all rules + BDD,
   marked ❌) + ADRs, so the knowledge is never lost.
2. **Compact the session** once the docs land.
3. **Then plan each dev slice as its own plan** — a coherent subset of ❌ rules, foundation-first —
   implement + test it, flip ❌→✅. Propose the split yourself.
4. **Handover:** a finished plan hands over to the dev agent. Right after the docs-only update there is
   **no implementable slice yet**, so the dev agent must **push back and ask the plan agent for the
   first dev-plan** rather than coding from the docs directly. Each slice is one iteration
   (see AGENTS-SESSION-END.md).

## Components
- A **component** = a main feature = a **Java package** (same name across the project's modules).
- A story (`docs/<feature>.md`) is usually **one component**, or a **cross-component extension** that
  changes several components — which must **link** the features it extends.
- **No homeless implementation:** if a story doesn't cleanly extend linked features, make it its **own
  component** — own package + service. Always consider a **Java service** that bundles the feature's
  business logic and uses the lower-level components — the home for the rules' behaviour.
- A component that grows into a set of features becomes its own Maven **module** with its own `docs/`
  (+ `docs/adr/`, each with its `index.md`). Where a project's module layout is fixed (see its
  AGENTS-DEV.md), components stay packages and the story + ADRs live in the root `docs/`.

## Also plan
- The user-facing docs change (how to use the feature), kept short.
- At the end, reconcile the docs with what was built and compress — keep only what helps, never echo
  the code.

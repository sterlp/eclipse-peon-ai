# Plan Phase — the WHAT

Read-only mindset: produce the plan (story + ADRs) as the sole input for the dev phase. It is the
durable second brain — a future session must rebuild the intent from the docs alone.

**Docs-first holds for bugfixes too** — never let a code-first skill (systematic-debugging/TDD)
reorder plan→code; clear any deviation with the user first, and the end-of-iteration reconcile +
compress runs unprompted.

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
  read every `CONTEXT.md`.
- Broad context via search; narrow lookups by reading files directly.
- Traverse goal → affected area → constraints → architecture → exact files/classes.
- Cache file paths in the plan — never re-search during implementation.

## Capture the plan as docs, never only chat
- **Story = the WHAT** (`docs/<feature>.md`, no `-design` suffix): a short **goal** (why), then
  **business rules**, each with **BDD use-cases** (GIVEN/WHEN/THEN — happy path, edge, failure; each
  maps to a concrete test name).
- **ADRs = *your* memory** (`adr/NNNN-<slug>.md` + `adr/README.md` registry): the technical notes a
  future session needs to stay efficient — decisions/usages (libraries, storage, mechanisms,
  constraints) so you don't re-ask or re-derive. Body: **Status · Context · Decision · Consequences**.
  **The docs are *our* shared memory** of what we planned; the ADRs are yours.
- **Only add an ADR when it isn't clear from the rule/BDD.** If it's obvious from the story, no ADR.
- **Never duplicate:** rule/BDD stays in the story, technical notes in the ADRs; they cross-link,
  never copy.
- **When a past assumption was wrong:** correct it, then capture the fix as a **BDD** (business) or an
  **ADR** (technical) so it isn't repeated.
- **One feature = one name** across `docs/<feature>.md`, its ADRs and its package/module.
- Open questions the implementer must decide inline → note them in the story.

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
- A component that grows into a set of features becomes its own Maven **module** with its own `docs/` +
  `adr/`. Where a project's module layout is fixed (see its AGENTS-DEV.md), components stay packages and
  the story + ADRs live at the root.

## Also plan
- The user-facing docs change (how to use the feature), kept short.
- At the end, reconcile the docs with what was built and compress — keep only what helps, never echo
  the code.

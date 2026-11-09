# Skill knowledge

Compact catalog of reusable patterns learned from implementation iterations. Each entry links to evidence or a focused pattern page and states **problem · root cause · proven response**.

## esbuild native binary hangs on macOS 26.6.2 (VitePress `docs:build` unusable)

- **Problem:** `npm run docs:build` in `homepage/` (VitePress → Vite → esbuild) hangs at startup. The esbuild native binary (arm64, valid Mach-O, ad-hoc signed, no quarantine) hangs on *any* invocation (`--version`, transform). Fresh reinstall and both installed node versions hang; a `config.mjs` workaround does not help.
- **Root cause:** Environment-level (macOS 26.6.2), not a code or content problem.
- **Proven response:** Treat the homepage `docs:build` gate as **deferred** — commit the homepage content with the note `homepage docs:build gate deferred (esbuild/macOS-26.6.2 env-blocker)` and do **not** modify the VitePress/Vite/esbuild dependency chain without explicit approval. Re-run the gate once the user's environment is fixed.
- **Evidence:** Cycle 2c (inc-18…inc-21, 2026-08-30) — all four homepage increments committed with the deferred-gate note; core (`mvn test`) and plugin (Maven package + Eclipse suite) green throughout.

## Edit-tool root-cause triage: input validation first

- **Problem:** `eclipseEditFile`/`diskEditFile` "oldString not found" for a micro edit (`}}`); a 2h stress/race hunt (self-reference growth, thread interleaving) before the real cause surfaced.
- **Root cause:** The anchor was below the validity threshold — an input-validation gap, not a concurrency bug. Chasing the exotic cause first.
- **Proven response:** Triage edit-tool failures in this order: (1) oldString null/blank/too short (input validation), (2) self-reference growth (oldString contains its own replacement), (3) race/stress (concurrent modification). Input validation is the cheapest and most frequent cause — check it before any stress testing.
- **Evidence:** Edit-Guard cycle `7800a56` (oldString required, min 3 non-whitespace chars, up-front in `FileUtils.applyEdit`); 2h stress hunt vs. the input-validation look (sanity-pass 2026-09-19, Da-Mek lesson).
## Status flip: sweep the doc, not just the marker

- **Problem:** After a rule ❌→✅, the doc's present-tense IST sentences ("Heute feuert das Tool nur `if (existed)`", with `File.java:73-81` line anchors) described the old state and went silently stale the moment the code landed — and a new user-visible output string with a number (a truncation marker) shipped without being checked against the repo's hard disclosure rule ("showing N of M", N = actually shown).
- **Root cause:** The flip treated only the status markers as "the doc"; IST narrative, dead anchors and new output text were out of scope of the flip step, so the reviewer had to catch both (review REJECTED with one behavior bug and one docs↔code drift in the same cycle).
- **Proven response:** A status flip includes a sweep of the whole rule block. Parts (1) old-state sentences and (2) code/line anchors are now a built-in doc-method rule (`prompts/doc-method.md`, 2026-11-09) — they are dropped, not rewritten. Repo-specific part stays here: (3) every NEW output string with a number is tested against the disclosure rules of the repo (exact count, named limit, nothing implied). Same flip, same edit round — not a follow-up.
- **Evidence:** Cycle 2026-11-05 (R-PT-6…8 + R-SD-1…3): Da-Dok review REJECTED with B1 (marker named the cap value instead of the shown count — `SimpleDiff.java:152-156`) and B2 (present-tense IST + dead anchors in the freshly flipped `file-update-diff.md`); fixed in `34f999d6`/`8cc13196`.

## Rename cycles: inventory the CONSUMERS of the identifier, not its config sides

- **Problem:** A rename (built-in agent `Peon-PO` → `Jon`) was inventoried along the **config/registration sides** of the name. The **readers of the old value** — key mapping, derived file names, registry keys — were never enumerated. One consumer (`AgentsMdContextItem.resolveAgentKey`, which strips the `Peon-` prefix to build `AGENTS-<KEY>.md`) silently stopped resolving the agent's own standing-orders file, while the feature docs still asserted the opposite.
- **Root cause:** Inventory formed as "where is the name configured" instead of "who reads the name". Additionally, an open point of the form "measure X" did not block the increment — it was carried into the review as ⏳ and never measured.
- **Proven response:** For naming/ID cycles, build the inventory from every **consumer** of the identifier: VOLL-Grep over the accessor call sites (`getName()` and friends), key resolution, derived file names, display strings and BDD lines in docs — **not** over doc file names (display strings and BDD rows survive a file-name-driven inventory). An open point phrased as "X messen" blocks the increment until the measurement is written into the doc.
- **Evidence:** Night cycle 2026-10-30 (origin + display names, ADR-0078/0079) — Da-Dok review REJECTED with BUG-1 (MAJOR: `AgentsMdContextItem` untested and silently broken) plus BUG-3/4/5 (a file-name-driven doc inventory left display strings behind, e.g. `AGENTS.md` built-in list, `agenten-status-im-header.md`). Measured, then closed as Clean Break with pin `AgentsMdContextItemTest#test_jon_resolvesOnlyToCleanBreakNames` (commit `4260475b`).

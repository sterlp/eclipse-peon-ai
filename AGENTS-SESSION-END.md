# Session End — retrospective (run after each iteration)

An iteration = design → plan → update docs → write code → tests → clean docs → compact docs.

**If your tool didn't auto-load it, read the always-on base `AGENTS.md` first** — this file only adds
the retro step on top.

## How the guidance files are structured (keep it this way)
Four files, **each rule has exactly one home — no duplication**:
- **`AGENTS.md`** — the always-on base every tool loads: interaction rules, module/`docs`/`index.md`
  structure, docs-first, repo layout, and the phase-file pointers.
- **`AGENTS-PLAN.md`** — plan-phase only (the WHAT: story + ADRs).
- **`AGENTS-DEV.md`** — dev-phase only (the HOW: code, testing, logging, build, deps, thread-safety).
- **`AGENTS-SESSION-END.md`** — this retro.

The Peon plugin auto-loads the phase file per mode; other tools open it on the cue in `AGENTS.md`.
`<!-- COMMON RULES -->` (in `AGENTS.md`) and `<!-- COMMON CODE -->` (in `AGENTS-DEV.md`) are the
backport blocks synced across projects — keep them non-overlapping. When improving a rule, edit its
single home; if you find the same rule in two files, that's drift to fix.

## Close the loop (before the Q&A)
- Flip each implemented rule **❌ → ✅** — only if its BDD test is green.
- Delete/complete the finished dev-plan; remove any leftover scaffolding.
- The feature is **done when no ❌ rule remains**; only then do the user-facing docs advertise it.
- Reconcile + compact the docs — keep only what helps a future session, never echo the code.
- **Lint the docs** — quick health-check: every story/ADR is listed in its `index.md`; no orphan doc
  (no inbound link); no broken cross-link; no `✅` whose test is gone/red or `❌` that actually ships.
  Fix what you find, or note it in the story if it needs a decision.

## Improvement Q&A

Run a short retro with the user before closing:

- **What went well?** Keep it — one line.
- **What went badly?** The concrete friction: wrong assumption, missed rule, rework.
- **What must be remembered?** If it's a technical decision/constraint → capture an **ADR** (your
  memory); if it's business behaviour → a **BDD/rule** in the story (our memory). Correct any wrong
  assumption at its source.
- **Was AGENTS.md / AGENTS-PLAN.md / AGENTS-DEV.md helpful?** What was missing or misleading — improve
  the guidance file so the next session starts smarter.
- **Feedback to the user:** say plainly what wasn't good (unclear requirement, thrash, missing
  example) — not just self-critique.

Keep it short: the goal is a compounding second brain, not a report. Capture only what changes future
behaviour.

# Session End — retrospective (run after each iteration)

An iteration = design → plan → update docs → write code → tests → clean docs → compact docs.

## Close the loop (before the Q&A)
- Flip each implemented rule **❌ → ✅** — only if its BDD test is green.
- Delete/complete the finished dev-plan; remove any leftover scaffolding.
- The feature is **done when no ❌ rule remains**; only then do the user-facing docs advertise it.
- Reconcile + compact the docs — keep only what helps a future session, never echo the code.

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

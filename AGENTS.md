<!-- COMMON RULES START -->
## Common Rules

- **Be concise.** Short, direct chat answers — no filler, no restating the known. The docs still
  capture every decision.
- **One question at a time, with a recommended answer.**

### Response style
- No preamble/postamble — skip "I will…", "Here is…", "Based on…", "Done."
- Answer directly, 1–4 lines unless detail is asked for. Don't repeat what was said; cut words that
  add no meaning.
- Can't help? Offer alternatives in 1–2 sentences — don't moralize.
- `->` denotes a dependency.
<!-- COMMON RULES END -->

# Workflow — docs-first, then code

- **Docs-first, always.** Capture the plan (story + ADRs) in the docs *before* code — the docs are the
  durable second brain and the source of truth. Never keep a decision only in chat.
- **Clarify → plan → code.** Don't assume — if it isn't in the docs, ask or request an
  example/BDD/use-case. The feature must be clear and fit the existing docs.
- **Plan phase → read [AGENTS-PLAN.md](AGENTS-PLAN.md)** — capture the story (goal + business rules +
  BDD = the WHAT) and the technical ADRs (the HOW) *before* writing code.
- **Dev / code phase → read [AGENTS-DEV.md](AGENTS-DEV.md)** — build, test, code, logging and
  dependency conventions.
- **End of each iteration → read [AGENTS-SESSION-END.md](AGENTS-SESSION-END.md)** — run the improvement
  Q&A and capture what must be remembered (ADR or BDD).

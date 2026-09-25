Compress the conversation into a structured briefing. Output exactly:

WHAT: <Feature Name>
- Goal and requirements
- Decisions made and their rationale
- Preserve always: paths to plan files, spec files, task files
- Preserve always: the most recent user instruction on how the agent should behave, overriding any earlier conflicting behavioral instruction

HOW: <Design or Plan>
- Solution design
- Work done so far; files modified and why
- Component responsibilities
- Last implementation plan (if not yet executed)

NEXT: <What to do next>
- Pending work, open decisions, blockers

LESSONS-LEARNED: <Problems overcome>
- Problems found and how they have been resolved
- Discarded/failed approaches and why they were rejected
- Critical issues found outside the task's scope, with a suggested patch/fix
  (e.g. to the AGENTS*.md)
- Errors found in existing SKILLs, or new SKILLs worth proposing — include the
  actual suggested content and the problem it should solve, not just "a skill
  is needed"

Preserve: key decisions + rationale, pending work, file paths, exact code references (file:line), conclusions drawn from tool results — especially hard-won facts that are expensive to rediscover.
Remove: duplicates, raw tool output, superseded decisions, filler, content already fully captured in a referenced plan/spec/task file — keep the path, drop the content.

The input may end with a disclosure of what was cut before you saw it:

session truncated
thinking capped 9000 (front), tool results 6000, per-message cap N, duplicates collapsed M

- "thinking capped N (front)": the BEGINNING of an AI thinking block was cut — the end (the conclusion) is what you see.
- "tool results N" / "per-message cap N": long tool outputs or messages were cut to their first N characters.
- "duplicates collapsed: M": M identical messages were removed.

Treat the cut parts as unknown: never invent what was cut away, and if a decision, path or fact is missing, say so instead of guessing.

If a section has no content yet, write: <not yet established>
Output only the briefing — no preamble, no closing remarks.
Compress aggressively: a new agent must be able to continue work from this briefing alone.
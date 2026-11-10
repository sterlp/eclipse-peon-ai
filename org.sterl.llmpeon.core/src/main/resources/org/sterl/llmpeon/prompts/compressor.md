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
- State the next concrete command, file, or decision point.

LESSONS-LEARNED: <Problems overcome>
- Problems found and how they have been resolved
- Discarded/failed approaches and why they were rejected
- Recurring LLM/agent mistakes observed in this session: state the pattern,
  trigger, consequence, and smallest verified correction (instruction, checklist
  item, test, or tool guard). Do not create or modify SKILLs during compaction;
  only propose them.
- Observations with evidence — one line each, only what the input shows; never infer
  from absence, never judge code you did not see. Use exactly these tags, omit a tag
  without an observation:
  - [redundant-test] <test> file:line — duplicates <other test> file:line
  - [verbose-comment] file:line — restates the code, narrates history, or is too long
  - [worked-well] <practice> — where it paid off
  Copy observations from an earlier briefing in the input, merge equal ones and count
  them (×n). This count is the evidence for the SKILL rule below.
- Critical issues found outside the task's scope, with a suggested patch/fix
  (e.g. to the AGENTS*.md)
- Errors found in existing SKILLs, or new SKILLs worth proposing — include the
  actual suggested content and the problem it should solve, not just "a skill
  is needed". Propose a SKILL only for a recurring, generalizable problem with
  evidence from at least 2 independent occurrences (×n above, or a recurring
  mistake); state the evidence and the validation step.

Preserve: key decisions + rationale, pending work, file paths, exact code references (file:line), conclusions drawn from tool results — especially hard-won facts that are expensive to rediscover.
Preserve verbatim: the latest error message, failing command, and decisive test result; do not paraphrase them.
Remove: duplicates, raw tool output, superseded decisions, filler, content already fully captured in a referenced plan/spec/task file — keep the path, drop the content.

Use only information present in the input. Never infer, reconstruct, or invent missing decisions, paths, facts, or context. If information is missing or uncertain, state that it is missing; mark uncertain conclusions as [assumed] and verified facts as [verified].

If the input contains a truncation notice (e.g. "session truncated", "thinking capped", "tool results", "per-message cap", "duplicates collapsed", "context blocks dropped"), treat the omitted content as unknown. Do not reconstruct it or claim it was absent; context blocks marked as dropped may be re-injected after this compact.

If a section has no content yet, write: <not yet established>
Output only the briefing — no preamble, no closing remarks.
Compress aggressively: but a new agent must be able to continue work from this briefing alone.
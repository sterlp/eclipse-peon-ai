- Write and keep the plan current with the plan tools (plan* on ${plan}); that file is the single source of truth and the durable handover to the Dev slave.
- Find the SIMPLEST solution and slice it into small increments that each build green on their own.
- Plan continuously: as understanding grows, refine the same plan rather than starting over.
- If feature docs exist, they are the golden record of the WHAT: reference their rule/UC-IDs, never copy or reword them in the plan; a needed rule missing from the docs is a question, not a plan decision.
- If something is unclear or needs a decision you cannot take, ask one direct question and stop — never guess.
- AGENT-MODE: If you have questions, stop — write the plan as far as known, then respond directly:
  - `STATUS: AWAITING_ANSWERS` followed by all questions/concerns (each with
    your recommended answer and rationale).
  - If nothing is open, end with `STATUS: COMPLETE`. 

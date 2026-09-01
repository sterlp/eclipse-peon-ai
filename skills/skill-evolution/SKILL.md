---
name: skill-evolution
description: Turn validated implementation experience into concise project skills without mixing requirements, decisions, or raw traces into procedures.
---

# Skill evolution

Use after each implementation iteration, during the mandatory review and before approval.

## Loop

1. **Observe:** Compare plan, implementation, tests, review findings, and available evidence. Include successful strategies and failures.
2. **Consolidate:** Search `skills/wiki/index.md` for an existing pattern. Add or refine one only when the root cause and workaround are reusable.
3. **Propose once:** Decide `no change`, patch one existing skill, or create one new skill. Keep the proposal atomic and concise.
4. **Validate:** Show that the changed instruction would prevent the observed failure or preserve the successful behavior. Prefer an existing falsifiable test or focused break-the-fix evidence.
5. **Gate:** Keep the skill change only when evidence supports it and it introduces no conflicting guidance. Otherwise revert it, but record the rejection in `skills/wiki/skill-impact.md`.
6. **Hand over:** Pass the exact paths of relevant accepted skills to Plan and Dev in the next applicable cycle.

## Knowledge boundaries

- Skills contain reusable procedures, applicability conditions, and review checks.
- `skills/wiki/` retains compact patterns and accepted/rejected skill-change history; agents executing feature work do not need it.
- Plans and evidence are the raw execution record; do not duplicate full traces.
- Business requirements stay in feature docs; technical decisions stay in ADRs; session state stays in `docs/memory.md`.
- Prefer project-local skills. Promote a mature pattern to a built-in/global skill only after it proves useful across projects.

## Review output

Record exactly one outcome per iteration: `no change`, `patched <skill>`, or `created <skill>`, with the validating evidence. This check does not start another review loop.

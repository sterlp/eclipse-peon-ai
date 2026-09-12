# Skill impact

Record accepted and rejected skill proposals so ineffective guidance is not proposed repeatedly.

| Date | Iteration | Skill | Proposal | Outcome | Evidence |
|---|---|---|---|---|---|
| 2026-08-31 | Process setup | `skill-evolution` | Introduce a lightweight observe–consolidate–propose–validate–gate loop | Accepted | WikiSkill paper and FORgE review experience |
| 2026-08-30 | Cycle 2c (inc-18…21) | wiki pattern | Record esbuild-on-macOS-26.6.2 hang → defer homepage `docs:build` gate, don't touch the VitePress/Vite/esbuild dep chain | Accepted (wiki, no new skill) | Diagnosed env-level hang (binary/reinstall/node/config all fail); all 4 homepage increments committed green with the deferred-gate note |
| 2026-09-10 | Cycle 3 mcp-fixes | — | No change: MCP `-32022`/Era-Lock knowledge lives in ADR-0045 + docs/mcp.md (project-local, langchain4j-specific) — no reusable skill; no skill deficiency observed | no change (legitimate) | Da-Dok review verdict CONCERNS with zero skill/instruction gaps; findings C1–C4 were code-level, handled in-cycle |

| 2026-09-12 | Cycle ui-config (R-A1/R-A2) | — | No change: Da-Dok flagged one instruction gap (SWT GridLayout honors only `GridData.exclude`, not visibility) — recorded as project-local API trap in `AGENTS-DEV.md`, not a reusable skill; eclipse-dpe not consulted, no skill deficiency observed | no change (legitimate) | Da-Dok review ACCEPTED, gap implemented as AGENTS-DEV.md bullet same turn; mutation-check confirmed stale-guard test gap (plan §11, follow-up open) |
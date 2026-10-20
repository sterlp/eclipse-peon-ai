---
name: plan-exploration
description: Context exploration before planning a new story — descriptors (*.md) first, then build files; broad sweep / narrow reads; index.md per affected folder; traverse goal to exact files; cache found paths in the plan.
---

# Plan exploration — explore before deciding (migrated from AGENTS-PLAN.md, 2026-10-20)

**Trigger:** neue Story planen, Kontext unklar.

- **Scan descriptors first** (`*.md`, then `pom.xml` / `package.json` / `build.gradle`); broad sweep
  = read every `index.md`.
- **Broad context via search; narrow lookups by reading files directly** — breit suchen, eng lesen.
- **Traverse goal → affected area → constraints → architecture → exact files/classes.**
- **Cache file paths in the plan** — never re-search during implementation.

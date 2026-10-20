---
name: story-slicing
description: Story slicing when a story is too big for one session — docs-only increment first (full story ❌ + ADRs, code-free), compact the session, each dev slice its own foundation-first plan, dev stops after docs-only and builds only on released slices.
---

# Story slicing — split docs from code, then hand over (migrated from AGENTS-PLAN.md, 2026-10-20)

**Trigger:** eine Story ist zu groß für eine Session (der Code passt nicht).

1. **First a docs-only increment** — the plan captures the full story (goal + all rules + BDD,
   marked ❌) + ADRs; the docs are written first (PO-owned — in this repo only Jon writes
   `docs/`), code-free, so the knowledge is never lost.
2. **Compact the session** once the docs land.
3. **Then plan each dev slice as its own plan** — a coherent subset of ❌ rules, foundation-first —
   implement + test it, flip ❌→✅. Propose the split yourself.
4. **Handover:** a finished plan hands over to the dev agent, which then self-drives the slices.
   The docs-only increment ends at the docs — there is **no implementable slice yet**, so the dev
   agent stops there rather than coding from the docs. Each following slice is one iteration.

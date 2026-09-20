# Session-Stand — 2026-09-20 (Tool-Evolution Build-Zyklus)

## Wo wir stehen

**Branch `analysis/tool-evolution`** — Paul freut Zyklus ab, kurze "ok"-Antworten.

- ✅ **Story A** (Disclosure + Web-Tools) — komplett, Flips ✅, Archiv `overview-done-2026-09-20-12-43.md`.
- ✅ **Story B** (CR-3 Project-Problems-Filter) — **DONE 2026-09-20.** Feature `0eb746f` → Review-Fixes
  `a721313` (ungültiger severity = IAE, case-insensitive gemessen, try/finally-Cleanup, whitespace=unset)
  → `e98e646` (UC-PP-7-Kommentar) → `e84fcbc` (Flips + Archiv `overview-done-2026-09-20-14-02.md`).
  Doc R-PP-1…7 + UC-PP-1…7 ✅, OSGi 236/0/0, Lint PP = 0.
- **Story C (Debugger)** — Doc gehärtet: [java-debugger-tool.md](java-debugger-tool.md) R-JD-1…5 +
  UC-JD-1…6 (❌), JD-Präfix gezogen. **Nächster Schritt: Plan via planWithPlanAgent** (User-managte
  Session, alle Actions, KEINE Confirmations, für Da Mek, sync, keine Session = ehrlicher Fehler;
  Fixture-Strategie Debug-Session im OSGi-Test klären — Da Mek STOP-AND-ASK).
- Danach: DocsLinter `idPattern`-`@P`-Description als Mini-Zyklus (Da-Dok-Kandidat, siehe open-points).

## Nächste Schritte

1. Story C: planWithPlanAgent → meine Abnahme → buildWithDev → reviewPlanAgent (Da Dok; vorher ggf.
   compactReview, er ist bei ~77%) → Flips ✅ + lint vor Flip.
2. Mini-Zyklus DocsLinter idPattern-`@P`-Description + docs-linter.md-Zeile.
3. Management-Summary an Paul mit Skill-Evolution-Abschnitt (AGENTS-PO); Merge/Squash = Paul.

## Bekannt & bewusst out-of-scope

- Lint: 34× `UNBELEGT_ERLEDIGT UC-DL` (Docs-Linter-Doc hat UCs, Tests sind Tool-Tests) — seit 2026-09-15 bekannt.
- Neu beobachtet: `VERWAIST UC-DL-99` (DocsLinterToolTest.java:78) —UC-DL-99 fehlt im Doc; beim
  DocsLinter-Mini-Zyklus mit fixen.

## Was nicht neu aufgemacht wird

CR-1/2/7 abgelehnt (Revisit nur bei Nicht-Git-Workspaces) · CR-5/CR-6 geparkt, nicht verworfen ·
Copilot-Server nicht decompilieren · keine Confirmations für Debugger.

## Lektionen (Zyklus)

1. Eigene frühere Analysen sind IST-Verdacht (Issue #142: p2-Site-Content ≠ Bundle-ClassPath).
2. PO-Run-Verdicts sofort ablegen (resolved-points.md), keine Leichen.
3. LLM-Backend (llama.cpp) kann crashen — 3× Connect-Abbrüche am 2026-09-20; State/Commits überleben,
   einfach retry (Evidence in open-points).

# Session-Stand (2026-09-12 — UI-Cycle R-A1/R-A2 läuft)

## Zyklus ui-config — STATUS: Plan ✅ abgenommen (inkl. Delta), BUILD als Nächstes

Branch `core-cleanup-2026-09-11` @ `d408fc6`. **Vor Inc-1 committet Da Mek separat die User-Work**
(ExtraBodyExamples llama.cpp-Beispiel, AbstractAgent/PoDelegateTool uiName-Fallback, ToolService
Reformat — User 2026-09-12, bewusst): der User repariert die PO-Tool-Meldungen — der Name des
Sub-Agents fehlte in den Tool-Meldungen („Context: N token - X% used (…)" ohne Namensbezug).
Docs R-A (advanced-configuration.md R-A1/R-A2/R-A3
+ model-loading.md R-ML3) vom PO auf **reines SOLL** umgeschrieben (User-Direktive 2026-09-12:
keine implementierten Bug-/„war:"-Narrativen in Docs — der Plan trägt den Diff SOLL/IST).
Uncommitted, reiten im **Inc-1-Commit** (Da Mek committet sie mit, ohne sie zu editieren) —
zusammen mit memory.md + open-points.md (PO-Entscheid, Session-Docs).

- **Plan** (`peon-plan/overview.md`, nach Delta): Inc-1 = R-A1 Spacing (2× `marginBottom=0` +
  `examplesLabel` hidden bis Paste; User-Smoke, kein Test). Inc-2 = R-A2: `ModelComboWidget` wird
  **Controller statt Composite** — Combo col2 im Parent-Grid, Refresh-Button span2/LEFT (wie
  `buildCheckUrl`), „Model:"-Label je Caller (Advanced `addLabel` END / Basic raw Label SWT.LEFT,
  JFace-verifiziert, VOR Konstruktion), `EclipseUtil.runInUiThread(Composite→Widget)` signatur-
  verbreitert (22 Call-Sites source-kompatibel), Stale-Guard-Anker `this`→`modelCombo`, API +
  Verhalten unverändert (R-ML* + ADR-0040). Tests: 4 Szenarien unverändert, Helfer suchen im
  Parent-Grid, + READ_ONLY-Assertion. Kein Core-Change; Homepage-Wording verifiziert wahr.
- **Abnahme-Korrektur (PO):** alter Plan-Claim „Widget-Grid 3→2 Spalten = Label-Spalte der
  Parent-Page" war falsch (verschachtelte GridLayouts teilen keine Spaltenbreiten) → Delta über
  planWithPlanAgent, Abnahme erteilt.
- **Nach Build:** IST-Verifikation je Deliverable (Plan §9, memory #28) → `reviewPlanAgent`
  (Da Dok, 3 Seiten; Docs: advanced-configuration.md R-A1/R-A2 + model-loading.md R-ML3) →
  Status-Flips ❌→✅ (PO-only) → `planImplemented` (Da Mek).
- **Flags (Plan §10):** dropdown-ui-comparison.md wird stale (Update gehört zu R-A3);
  AGENTS-DEV.md zitiert `ModelComboWidget:122` → Zeile von PO nach Inc-2 aktualisieren.
- **Danach (User):** R-A3 Copilot-Studie separat; User-Smokes (R-A1 Abstand, R-A2 Look beider
  Pages).
- ⏳ in open-points.md: R-A2 SOLL-Präzisierung (Sibling-Ausrichtung statt literal „links")
  im Smoke bestätigen lassen; Docs-SOLL-Hygiene-Sweep (Rest der Feature-Docs) — Scope-Confirm.
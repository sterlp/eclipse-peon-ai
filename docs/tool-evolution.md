# Tool Evolution — Sammelstelle (temporär)

> **Status:** 🚧 in design — PO-Run offen (Paul entspricht accept/reject je Item). 
> **TEMPORÄR:** Dieses Doc wird nach dem PO-Run **aufgelöst**: akzeptierte Items werden ❌ in den jeweiligen
> Feature-Docs, dieses Doc und [feature-change-request-copilot.md](feature-change-request-copilot.md)
> (einzige Datei mit externen Bezügen) werden gelöscht. Bis dahin ist es die Karte.

## Was ist das?

Ergebnis des Tool-Vergleich-Runs 2026-09-19: unser Plugin gegen das externe Copilot-Eclipse-Plugin
(alle Tools verglichen, hoch-levelig). Ausgangs-Analyse-Plan: `github-copilot-for-eclipse/peon-plan/overview.md`
(Da Thinka, archiviert bei Auflösung). Die externe Mapping-Seite (Wer, Pfad, was besser/WARUM) steht **ausschließlich**
in [feature-change-request-copilot.md](feature-change-request-copilot.md) — alle übrigen Docs bleiben neutral.

## CR-Übersicht

| CR | Thema | Empfehlung (Aufwand) | Feature-Doc | Status |
|---|---|---|---|---|
| CR-1 | Datei anlegen | Nein (—) | — (UI-Teil in CR-7) | erledigt als nein |
| CR-2 | Datei bearbeiten / Whole-File-Regen | Teilweise: Mechanismus Nein, Review-UI Ja (M) | [change-review.md](change-review.md) | 🚧 offen |
| CR-3 | Fehler prüfen pro Datei | Ja (S) | [project-problems-tool.md](project-problems-tool.md) | 🚧 offen |
| CR-4 | Java-Debugger | Nein/Roadmap, ggf. Lese-Subset (L) | [java-debugger-tool.md](java-debugger-tool.md) | 🚧 offen |
| CR-5 | Persistente/Background-Terminal-Session | Ja, Foreground zuerst (M) | [terminal-session-tool.md](terminal-session-tool.md) | 🚧 offen |
| CR-6 | Tool-Confirmation-Modell | Ja, minimal „Session" zuerst (M-L) | [tool-confirmation.md](tool-confirmation.md) | 🚧 offen |
| CR-7 | Change-Review-UI (Keep/Undo/Diff) | Ja — höchster Vertrauensgewinn (L) | [change-review.md](change-review.md) | 🚧 offen |
| CR-8–16 | Unsere 15 Stärken-Familien (Read/Search/Nav/Docs/Orchestration/Memory/Skills/Build/MCP) | Nein — unser Vorsprung | — | erledigt als nein |
| CR-17 | `eclipseSearchFiles` Cap-Disclosure | Ja (S) | [tool-output-disclosure.md](tool-output-disclosure.md) | 🚧 offen |
| CR-18 | `diskSearchFiles` Cap-Disclosure | Ja (S) | [tool-output-disclosure.md](tool-output-disclosure.md) | 🚧 offen |
| CR-19 | `webFetchAsMarkdown` Output-Cap | Ja (S) | [tool-output-disclosure.md](tool-output-disclosure.md) | 🚧 offen |

Priorisierungsvorschlag (Da Thinka, pending Paul): **CR-7 > CR-6 > CR-5(FG) > CR-3**, Hygiene (CR-17/18/19) als
geballtes S-Inkrement parallel, **CR-4 defer**.

## Offene Fragen an Paul (PO-Run)

1. **CR-4 Debugger:** defer (Lean: ja) oder anpacken? Falls ja: nur Lese-Subset (state/vars/stacktrace)?
2. **CR-5 Terminal:** nur persistente Foreground-Session (Lean) oder inkl. Background + Output-ID?
3. **CR-6 Confirmation:** minimales „Session"-Scope-Cache zuerst (Lean) oder volles Category-Modell?
4. **CR-2/CR-7:** Whole-File-Regen fix ablehnen (Lean) und nur Review-UI? WorkingSetBar (L) vor CR-6 priorisierbar?
5. **CR-3:** neues Tool vs. Pfad-/Severity-Filter auf `eclipseReadProjectProblems` (Lean: Filter)?
6. **Hygiene CR-17/18/19:** als ein S-Inkrement bündeln (Lean)?
7. **Reihenfolge** gesamt: CR-7 > CR-6 > CR-5 > CR-3, CR-4 defer — bestätigen/ändern?

## Auflösungs-Checkliste (nach PO-Run)

- [ ] Je akzeptiertes CR: Feature-Doc von 🚧 → ❌ (Regeln + BDD ergänzt), dann normaler Plan/Build-Zyklus.
- [ ] Abgelehnte CRs: Zeile hier als „abgelehnt (Begründung)" belassen, Feature-Doc löschen falls leer.
- [ ] Glossar-Einträge (Change-Review, Tool-Confirmation …) nach tatsächlichen Begriffsentscheidungen.
- [ ] [feature-change-request-copilot.md](feature-change-request-copilot.md) löschen (externe Bezüge weg).
- [ ] Dieses Doc löschen, index.md bereinigen.

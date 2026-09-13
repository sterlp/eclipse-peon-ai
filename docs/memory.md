# Session-Stand (2026-09-12 — UI-Cycle R-A1/R-A2 ABGESCHLOSSEN, R-A3/R-A4 im Backlog)

## Zyklus ui-config — STATUS: ✅ done (Review ACCEPTED, Smokes ok, Flips ✅, Archiv läuft)

Branch `core-cleanup-2026-09-11` — Commits: `75ace08` (User-Work: uiName-Fallback PO-Tool-Meldungen
+ llama.cpp-Extra-Body-Beispiel, Core-Surefire 728/0), `66ce4fe` (Inc-1 R-A1), `6b5c9ca` (Inc-2 R-A2).
Docs-Flips nach Review+Smoke erledigt: advanced-configuration.md (R-A1 ✅, R-A2 ✅), model-loading.md
(R-ML3 ✅), index.md, open-points (R-A2-Präzisierung → resolved-points.md), AGENTS-DEV.md
(Plugin-Liste ×9 nach AiAgentStatusModel-Move nach core; neuer API-Trap GridLayout/exclude).
Docs-Commits: siehe nächsten Abschnitt.

- **Nach Da-Dok-Verdict:** Mutations-Nachweis Stale-Guard (Da-Dok-Fund): Mutation → alle 5 Tests
  grün = Lücke bestätigt, im Plan §11 dokumentiert; **Follow-up-Test** (Identity-Wechsel während
  Fetch) noch zu planen. Core-Baseline mit User-Work: Surefire **728/0**.
- **⏳ offen:** Docs-SOLL-Hygiene-Sweep über übrige Feature-Docs (Scope vom User bestätigen);
  Stale-Guard-Follow-up-Test; `dropdown-ui-comparison.md`-Update gehört zu R-A3.
- **Nicht committen (User-WIP):** `CompactSessionTool.java`, `AIChatView.java` — liegen uncommitted
  im Tree (User repariert PO-Tool-Meldungen), Da Mek committet sie NICHT.
- **Sidequests (User 2026-09-12):** R-A4 Think-Dropdown nativ (🚧 in design, advanced-configuration.md)
  — offen: READ_ONLY vs editierbar + unbekannte gespeicherte Werte anzeigen. R-A3 Copilot-Studie
  separat danach.
- **User-Wissen (Session):** die Context-Stats-Zeilen in Tool-Meldungen sind die des Sub-Agents —
  User repariert gerade, dass der Agenten-NAME in den Meldungen steht (uiName-Fallback, `75ace08`).

## Nächster Zyklus (Kandidaten, Reihenfolge User)

1. R-A4 Think-Dropdown (SOLL-Festlegung + Build, klein).
2. Stale-Guard-Follow-up-Test (R-ML/ADR-0040).
3. Docs-SOLL-Hygiene-Sweep über übrige Feature-Docs (Scope-Confirm offen).
4. ApiRetry-Evidence-Sammlung + Live-Status im Backoff-Fenster (open-points, hoch priorisiert).
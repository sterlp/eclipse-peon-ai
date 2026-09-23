# ADR-0050: Debugger-Basis JDT-Debug-Model statt DAP — User-managte Session, keine Confirmations

> **Status:** ✅ accepted (2026-09-19/21, Paul) · **Fachdoc:** [java-debugger-tool.md](../java-debugger-tool.md)

## Context

Da Meks Debugger-Tool (CR-4, Copilot-Vergleich) braucht Zugriff auf laufende Debug-Sessions. Zwei
Basis-Optionen: JDT-Debug-Model (Eclipse-Interna, wie die Debug-View) oder DAP (Debug Adapter
Protocol). Zusätzlich die Frage Confirmations für mutierende Actions.

## Decision (Paul, 2026-09-19, im PO-Run)

1. **Basis: Eclipse-Debug-Model (JDT), nicht DAP.** Das Tool soll exakt das adressieren, was die
   Debug-View zeigt — dieselbe Session, dasselbe Thread-Model, keine zweite Session-Quelle
   (DAP-Adapter bräuchte einen eigenen Launch-Layer und liefe an der Debug-View vorbei).
2. **Debug-Session = User-Property.** Paul startet, managt und beendet die Session selbst im
   Debug-UI; das Tool liest und mutiert nur *innerhalb* der laufenden Session (kein Auto-Start,
   kein Auto-Disconnect — R-JD-1/2).
3. **Keine Confirmations.** Weil die Session User-Property ist, sieht Paul jede Änderung unmittelbar
   im Debug-UI — eine Bestätigungsrunde wäre Zeremonie ohne Schutzgewinn. (Konsistent mit
   [tool-confirmation.md](../tool-confirmation.md): Bestätigungen bleiben generell aus.)

## Consequences

- `isEditTool() = true`, Registrierung in `SharedToolsComponent` → Plan-/Review-/Search-/ReadOnly-
  Custom-Agents filtern es automatisch raus (Da-Mek-Wiring, IST-verifiziert).
- Ohne laufende Session: ehrlicher Fehler mit Start-Hinweis („start debugging in the Debug view
  first") — im E2E-Smoke positiv aufgenommen („hilfreicher als das SOLL").
- Wer später automatisierte Debug-Tests will (F2-Backlog & Co.), muss gegen dasselbe JDT-Model bauen
  — ADR-0049 hält die API-Fakten.

# Java-Debugger-Tool — Agent-gesteuertes Debugging (User-managte Session)

> **Status:** ❌ specified (2026-09-19, Paul: angenommen — angepasstes SOLL). Debugger-Tool für den
> Dev-Agenten (Da Mek); **User startet und managt die Debug-Session selbst**, das LLM unterstützt.
> Ursprungszuordnung (temporär): [tool-evolution.md](tool-evolution.md) — CR-4.

## Ziel

Da Mek kann eine **vom User gestartete** Debug-Session lesen und ändern — Debugging als Dialog zwischen
User (besitzt/managt die Session) und LLM (Hilfe: Zustand interpretieren, Variablen ändern, Breakpoints
setzen, Steps). Weil die Session User-Property ist, brauchen wir **keine Confirmations**.

## Vorgeschlagene Actions (voll, in einer Stufe)

- **Lesend:** `get_state`, `get_variables` (nested, depth), `get_stack_trace`.
- **Ändernd:** `evaluate_expression` (Timeout), `set_variable` (nur Primitiven/String/null),
  Breakpoints (conditional + hitCount + exception-breakpoints), `step_over/in/out`, `continue`, `suspend`.

## Regeln

- **R-JD-1 ❌** Alle Actions setzen eine **aktive Debug-Session voraus** (vom User gestartet). Ohne
  Session: ehrlicher Fehler („no active debug session — start debugging first"), kein Auto-Start durch
  den Agenten, kein Auto-Disconnect.
- **R-JD-2 ❌** Session-Lifecycle gehört dem **User** (starten, fortsetzen, beenden im Debug-UI). Der
  Agent mutiert nur Zustand *innerhalb* der laufenden Session; der User bleibt jederzeit Herr der Session.
- **R-JD-3 ❌** JSON-Output, pretty; synchron, jedes Tool gibt sofort zurück, was es sieht (kein
  Async-Bedarf, kein Zustands-Gedächtnis im Tool — Session-State lebt im Eclipse-Debug-Model).
- **R-JD-4 ❌** Basis: Eclipse-Debug-Model (JDT), nicht DAP. Keine Confirmations (bewusst, Session =
  User-Property — Paul 2026-09-19).
- **R-JD-5 ❌** Tool für den Dev-Agenten; Auswirkung nur auf die debuggte App — der User sieht jede
  Änderung im Debug-UI (Variablen-View, Breakpoint-View) unmittelbar.

## BDD (Entwurf, hart beim Plan-Zyklus mit UC-IDs)

- GIVEN laufende Debug-Session WHEN get_variables depth=2 THEN nested Variablen bis Tiefe 2.
- GIVEN keine Debug-Session WHEN irgendeine Action THEN ehrlicher Fehler mit Hinweis, kein Auto-Start.
- GIVEN laufende Session WHEN set_variable THEN Wert geändert, Debug-UI zeigt ihn; nur Primitive/String/null.
- GIVEN laufende Session WHEN Breakpoint conditional + hitCount THEN Breakpoint greift wie konfiguriert.
- GIVEN User beendet Session WHILE Agent zwischen Calls THEN nächster Call = ehrlicher Fehler.

---
idPrefix: JD
---

# Java-Debugger-Tool — Agent-gesteuertes Debugging (User-managte Session)

> **Status:** ❌ specified (2026-09-19, Paul: angenommen — angepasstes SOLL). Debugger-Tool für den
> Dev-Agenten (Da Mek); **User startet und managt die Debug-Session selbst**, das LLM unterstützt.
> Ursprung: CR-4 des Copilot-Vergleichs — [resolved-points.md](resolved-points.md) („Tool-Evolution-Run").

## Ziel

Da Mek kann eine **vom User gestartete** Debug-Session lesen und ändern — Debugging als Dialog zwischen
User (besitzt/managt die Session) und LLM (Hilfe: Zustand interpretieren, Variablen ändern, Breakpoints
setzen, Steps). Weil die Session User-Property ist, brauchen wir **keine Confirmations**.

## Vorgeschlagene Actions (voll, in einer Stufe)

- **Lesend:** `get_state`, `get_variables` (nested, depth), `get_stack_trace`.
- **Ändernd:** `evaluate_expression` (Timeout), `set_variable` (nur Primitiven/String/null),
  Breakpoints (conditional + hitCount + exception-breakpoints), `step_over/in/out`, `continue`, `suspend`.

## Regeln

### R-JD-1 — Keine Session = ehrlicher Fehler ❌

Alle Actions setzen eine **aktive Debug-Session** voraus (vom User gestartet). Ohne Session: ehrlicher
Fehler („no active debug session — start debugging first"), kein Auto-Start durch den Agenten, kein
Auto-Disconnect.

#### UC-JD-1 — noSessionFailsHonest ❌
- GIVEN keine Debug-Session WHEN irgendeine Action THEN ehrlicher Fehler mit Hinweis, kein Auto-Start.

### R-JD-2 — Session-Lifecycle gehört dem User ❌

Der User startet, fortsetzt und beendet die Session im Debug-UI. Der Agent mutiert nur Zustand
*innerhalb* der laufenden Session; der User bleibt jederzeit Herr der Session.

#### UC-JD-2 — userTerminatesSessionBetweenCalls ❌
- GIVEN User beendet Session WHILE Agent zwischen Calls THEN nächster Call = ehrlicher Fehler
  (kein Auto-Start, kein Auto-Reconnect).

### R-JD-3 — JSON-Output, synchron, stateless ❌

JSON-Output, pretty; synchron — jedes Tool gibt sofort zurück, was es sieht. Kein Async-Bedarf, kein
Zustands-Gedächtnis im Tool — Session-State lebt im Eclipse-Debug-Model.

#### UC-JD-3 — readActionsReturnNestedJson ❌
- GIVEN laufende Debug-Session WHEN `get_state` / `get_stack_trace` / `get_variables depth=2` THEN
  JSON (pretty), Variablen nested bis Tiefe 2.

### R-JD-4 — Basis Eclipse-Debug-Model, keine Confirmations ❌

Basis: Eclipse-Debug-Model (JDT), nicht DAP. Keine Confirmations (bewusst, Session = User-Property —
Paul 2026-09-19): jede Action wirkt sofort; der User sieht jede Änderung unmittelbar im Debug-UI.

#### UC-JD-4 — setVariableImmediateNoConfirmation ❌
- GIVEN laufende Session WHEN `set_variable` auf Primitive/String/null THEN Wert sofort geändert, ohne
  Bestätigungsrunde; Debug-UI zeigt ihn. GIVEN Ziel ist kein Primitive/String/null THEN ehrlicher Fehler.

### R-JD-5 — Tool für den Dev-Agenten ❌

Das Tool gehört Da Meks Toolset (disk-Write-Familie analog `isEditTool`); Auswirkung nur auf die
debuggte App.

#### UC-JD-5 — conditionalBreakpointWithHitCount ❌
- GIVEN laufende Session WHEN Breakpoint conditional + hitCount (+ exception-breakpoints) THEN
  Breakpoint greift genau wie konfiguriert.

#### UC-JD-6 — stepAndControlActions ❌
- GIVEN suspended Thread WHEN `step_over`/`step_in`/`step_out`/`continue`/`suspend`/`evaluate_expression`
  THEN Ausführung bewegt sich wie verlangt; `evaluate_expression` mit Timeout respektiert.

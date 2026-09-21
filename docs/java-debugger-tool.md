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

### R-JD-6 — Session-Erkennung schließt tote Targets aus ❌

Session-Lookup filtert Targets, deren Process terminiert ist oder keine Threads mehr hat
(`IProcess.isTerminated()` / `!hasThreads()`) — eine **selbst beendete** VM (Programm lief durch)
zählt als „no active debug session", genau wie eine UI-Terminierung; `continue` pollt dann nicht
in die Deadline. Kollateral aus dem E2E-Smoke (F7, 2026-09-21, Fix `2efaaf6`).

#### UC-JD-7 — deadVMSelfExitFailsHonest ❌
- GIVEN VM endete von selbst (kein UI-Terminate) WHEN irgendeine Action THEN ehrlicher
  „no active debug session"-Fehler, kein 30-s-Poll, keine „running"-Meldung mit leeren Threads.

### R-JD-7 — set_breakpoint verlangt geladene Compilation Unit ❌

Breakpoint-Erstellung löst das File zuerst über JDT auf (`JavaCore.createCompilationUnitFrom` →
`findPrimaryType`) und legt den Marker nur bei geladener CU an; sonst ehrlicher Fehler mit Datei +
Projekt + Grund (E2E F1: rohes `IFile` → „not a Java compilation unit", Fix `2efaaf6`).

#### UC-JD-8 — breakpointRequiresCompilationUnit ❌
- GIVEN Datei im Projekt WHEN `set_breakpoint` THEN CU-Auflösung vor Marker-Factory; Nicht-Source-
  File / fehlender Primär-Typ → ehrlicher Fehler, keine Marker-Leiche.

### R-JD-8 — Response-Vertrag: State-Semantik, Session-Kennung, Wert-Typen ❌

- `vm.state` = „suspended", wenn Target suspended ODER ein non-system-Thread suspended ist
  (debugging-relevante Frage „wo steht es?"), sonst „running"; zusätzlich `vm.suspendedThreads`
  (Anzahl suspended non-system Threads) — mixed State ehrlich gezählt.
- `get_state` (+ `diagnose_sessions`/Diagnostik) trägt `session` = Launch-Name + pid
  (`target.getProcess().getAttribute(ATTR_PROCESS_ID)`, **String** — `JDIDebugTarget` implementiert
  `IProcess` nicht, `instanceof` wäre immer false).
- `set_variable`-Response liefert Primitive als JSON-Zahl (nicht String) — konsistent mit
  `get_variables` (E2E F3).

#### UC-JD-9 — stateAndValueContract ❌
- GIVEN main suspended, System-Threads running, Target selbst NICHT suspended THEN `vm.state =
  "suspended"`, `suspendedThreads = 1`, `session` nennt Launch + pid; `set_variable`-Response
  liefert den Wert als JSON-Zahl.

## Thread-Enumeration (technischer Befund, ADR-verlinkt)

Thread-Listing und -Auflösung laufen ausschließlich über **`target.getThreads()`** —
`getRootThreadGroups()` listet `main` schlicht nicht (diagnose.txt-Beweis: 5 vs. 7 Threads,
main suspended in nur einem der beiden Wege). WARUM + Fallstricke: [ADR-0049](adr/0049-jdt-debug-2026-09-api-drift.md).

# Java-Debugger-Tool — Agent-gesteuertes Debugging

> **Status:** 🚧 in design — **Empfehlung: defer/Roadmap** (Entwurf aus dem Tool-Evolutions-Run
> 2026-09-19, wartet auf PO-Freigabe). Ursprungszuordnung (temporär): [tool-evolution.md](tool-evolution.md) — CR-4.

## Ziel

Live-Debugging per Agent: Zustand, Variablen, Stacktraces, Ausdrücke evaluieren, Breakpoints und Steps —
heute in llmpeon gar nicht möglich; stärkt den Run+Diagnose-Loop (zusammen mit `eclipseRunTests`).

## Vorgeschlagene Actions (Entwurf, in Stufen)

- **Stufe 1 (lesend):** `get_state`, `get_variables` (nested, depth), `get_stack_trace`.
- **Stufe 2 (mutierend):** `evaluate_expression` (Timeout), `set_variable` (nur Primitiven),
  Breakpoints (conditional + hitCount + exception-breakpoints), `step_over/in/out`, `continue`, `suspend`.
- **R-JD-1 🚧** Jede Action läuft durch [tool-confirmation.md](tool-confirmation.md) (Kategorie `debug`) —
  `evaluate_expression`/`set_variable` sind Code-Ausführung/State-Mutation und immer nachfragepflichtig.
- **R-JD-2 🚧** JSON-Output, pretty, nur im Debugger-Kontext verfügbar (Debug-Session aktiv), sonst
  ehrliche Fehlermeldung.
- **R-JD-3 🚧** Basiert auf dem Eclipse-Debug-Model (JDT), nicht auf DAP.

## BDD (Entwurf, hart erst bei ❌)

- GIVEN laufende Debug-Session WHEN get_variables depth=2 THEN nested Variablen bis Tiefe 2.
- GIVEN evaluate_expression WHEN Timeout THEN Partial/Timeout-Fehler, keine hängende Session.
- GIVEN keine Debug-Session WHEN irgendeine Action THEN ehrlicher Fehler (nicht „0 results").

## Offen (PO-Run)

- Defer (Lean: ja — L, sicherheitskritisch, ohne Confirmation-Schicht nicht verantwortbar) oder anpacken?
- Falls ja: nur Lese-Subset als Start?
- Aufwand: **L** (15 Actions, Session-Lifecycle, OSGi/JDT).

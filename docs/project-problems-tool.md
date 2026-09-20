# Project-Problems-Tool — gezielte Fehlerprüfung

> **Status:** ❌ specified (2026-09-19, Paul: angenommen) — Datei-Filter + Severity-Filter als Option,
> **projektweite Lesung bleibt unverändert** (beide Modi nebeneinander).
> Ursprungszuordnung (temporär): [tool-evolution.md](tool-evolution.md) — CR-3.

## Ziel

Nach einer Datei-Änderung will der Agent **gezielt die Fehler genau dieser Datei** prüfen („validate after
edit"), nicht die projektweite Problemliste — token-schlanker und präziser. Heute liefert
`eclipseReadProjectProblems` nur projektweit.

## Vorgeschlagenes Verhalten (Entwurf)

- **R-PP-1 ❌** `eclipseReadProjectProblems` bekommt optionalen **Datei-Filter** (ein oder mehrere Pfade).
- **R-PP-2 ❌** Optionaler **Severity-Filter** (z. B. nur ERROR) — Warnings/Infos bei Bedarf unterdrückt.
- **R-PP-3 ❌** Nur eigene Marker der Datei (keine Sub-Resources, DEPTH_ZERO-Verhalten).
- **R-PP-4 ❌** Leeres Ergebnis nach Filter: ehrliche Leermeldung mit Scope (kein „nicht gefunden"-Fehldeutes).
- **R-PP-5 ❌** Ohne Filter bleibt das Verhalten exakt wie heute: alle Probleme des Projekts — die
  Filter sind additive Optionen, kein Ersatz des projektweiten Modus (Paul 2026-09-19).

## BDD (Entwurf, hart erst bei ❌)

- GIVEN Datei mit 1 Error + 3 Warnings WHEN Severity=ERROR THEN nur der Error.
- GIVEN Filterpfad gehört nicht zum Projekt WHEN Aufruf THEN ehrliche Fehlermeldung mit Scope.
- GIVEN Datei fehlerfrei WHEN gefilterter Aufruf THEN „no problems in <file>" (nicht projektweite Liste).

## Offen (PO-Run)

- Pfad-/Severity-Filter auf bestehendem Tool (Lean — weniger Tool-Fläche) vs. eigenes neues Tool?
- Aufwand: **S**.

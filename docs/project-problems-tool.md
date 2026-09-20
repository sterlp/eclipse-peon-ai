---
idPrefix: PP
---

# Project-Problems-Tool — gezielte Fehlerprüfung

> **Status:** ❌ specified (2026-09-19, Paul: angenommen) — Datei-Filter + Severity-Filter als Option,
> **projektweite Lesung bleibt unverändert** (beide Modi nebeneinander).

## Ziel

Nach einer Datei-Änderung will der Agent **gezielt die Fehler genau dieser Datei** prüfen („validate after
edit"), nicht die projektweite Problemliste — token-schlanker und präziser. Heute liefert
`eclipseReadProjectProblems` nur projektweit.

## Regeln

### R-PP-1 — Datei-Filter (optional) ❌

`eclipseReadProjectProblems` bekommt optionalen **Datei-Filter** (ein oder mehrere Pfade) — nur die
eigenen Marker der Datei (DEPTH_ZERO-Verhalten, keine Sub-Resources).

#### UC-PP-1 — problemsForSingleFile ❌
- GIVEN Projekt mit Problemen in Datei A und Datei B WHEN `eclipseReadProjectProblems(project, files=[A])`
  THEN nur die Probleme von A (alle Severities, wenn kein Severity-Filter).

#### UC-PP-2 — filteredPathOutsideProjectFailsHonest ❌
- GIVEN Filterpfad gehört nicht zum Projekt WHEN gefilterter Aufruf THEN ehrliche Fehlermeldung mit Scope.

### R-PP-2 — Severity-Filter (optional) ❌

Optionaler **Severity-Filter** (z. B. nur ERROR) — Warnings/Infos bei Bedarf unterdrückt.

#### UC-PP-3 — severityFilterReturnsOnlyErrors ❌
- GIVEN Datei mit 1 Error + 3 Warnings WHEN `severity=ERROR` THEN nur der Error.

### R-PP-3 — Ehrliche Leermeldung ❌

Leeres Ergebnis nach Filter: Leermeldung mit Scope („no problems in <file>") — kein „nicht gefunden"-
Fehldeutes, keine projektweite Liste als Fallback.

#### UC-PP-4 — cleanFileReportsNoProblems ❌
- GIVEN Datei fehlerfrei WHEN gefilterter Aufruf THEN „no problems in <file>" (nicht die projektweite Liste).

### R-PP-4 — Default bleibt unverändert ❌

Ohne Filter bleibt das Verhalten exakt wie heute: alle Probleme des Projekts — die Filter sind additive
Optionen, kein Ersatz des projektweiten Modus (Paul 2026-09-19). Beleg: bestehende projektweite Tests
bleiben unverändert grün.

### R-PP-5 — Ungültiger Severity-Wert = ehrlicher Fehler ❌

Ein nicht-leerer, unbekannter Severity-Wert ist ein Fehler mit Nennung des Werts und der erlaubten
Werte (IAE) — nie stiller No-Filter, nie stiller projektweiter Fallback („A tool must never lie";
Review-Consensus 2026-09-20, Fix `a721313`).

#### UC-PP-5 — invalidSeverityFailsHonest ❌
- GIVEN `severity="bogus"` WHEN gefilterter Aufruf THEN IAE mit Wert + erlaubten Werten (Mutation
  „severity ignorieren" → rot).

### R-PP-6 — Severity case-insensitive ❌

Der Severity-Wert wird case-insensitive akzeptiert (`toUpperCase(Locale.ROOT)`), der Header nennt den
kanonischen Namen. Gemessen, nicht nur angenommen (Fix `a721313`).

#### UC-PP-6 — severityValueIsCaseInsensitive ❌
- GIVEN `severity="error"` WHEN gefilterter Aufruf THEN filtert wie `ERROR`, Header zeigt `severity ERROR`.

### R-PP-7 — Empty means unset (auch Whitespace) ❌

Whitespace-only-Werte in Filter-Parametern (`files=" "`) zählen als unset → projektweiter Modus,
wie `severity`-Blank-Handling und AGENTS-Regel „Empty means unset" — konsistent, kein hartes
Abschlagen nur für `files` (Da-Mek-Entscheidung 2026-09-20, Paul-Freigabe im Review-Umlauf).

# ADR-0053 — CallStats: geteilter Stats-Suffix-Helper im core

**Status:** ❌ accepted (2026-09-22)   **Feature:** [tool-time-disclosure.md](../tool-time-disclosure.md)

## Context

`PoDelegateTool` baut das Zeit-Suffix („done. … (1m 5s, 14:32)") privat (`dispatchStats`):
`System.nanoTime()`-Delta + `StringUtil.humanElapsed` + `LocalTime.now()` (HH:mm). Mit dem
Time-Disclosure-SOLL (Shell, eclipseRunTests, eclipseBuildProject) braucht jetzt eine zweite
Tool-Familie dasselbe Muster.

## Decision

Ein kleiner Helper `CallStats` im core `org.sterl.llmpeon.shared`:

- `CallStats.start()` … `suffix()` → `(3s)` bzw. `(3s, 14:32)`
- Dauer via `StringUtil.humanElapsed`, Uhrzeit `LocalTime` HH:mm — **Clock injizierbar**
  (Vorbild `shared.Timer`), damit Unit-Tests deterministisch sind.
- `PoDelegateTool.dispatchStats` wird auf `CallStats` migriert; sein Output ändert sich nicht
  (Pin-Test schützt).

## Consequences

- **Pro:** „One behaviour, one implementation" (AGENTS) — das Muster lebt einmal im core, beide
  Tool-Familien erreichen ihn (Plugin importiert core-`shared.*` flächendeckend).
- **Pro:** Clock-Injektion macht die Suffix-Formate unit-testbar ohne Sleeps/Flakiness.
- **Fallstricke:** `LocalTime.now()` ist nicht injizierbar — deshalb Clock-Parameter, nicht
  statische Zeit; kein zweiter Format-Varianten-Wildwuchs (Suffix exakt `(N, HH:mm)` bzw.
  `(N)`); `StringUtil.humanElapsed` bleibt die einzige Dauer-Formatierung.
- **Nicht hier:** Log-Zeitstempel, Streaming-Timer (R18–R21, eigene `shared.Timer`) — die laufen
  weiter getrennt; `CallStats` ist nur für Tool-Output-Suffixe.
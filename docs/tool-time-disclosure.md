---
idPrefix: TD
---

# Tool-Time-Disclosure

> **Status:** ❌ specified (2026-09-22, Paul)
> **Fachdoc:** dieses Doc   **ADRs:** [ADR-0053](adr/0053-call-stats-shared-helper.md)

**Ziel:** Der LLM sieht am Ende von langlaufenden Tool-Aufrufen **Dauer + Uhrzeit** — er kann
Timeout/Retry skalieren, Hangs einordnen und Tageszeit-Kontext ableiten. Vorbild ist das bestehende
Muster in `PoDelegateTool` („done. Context: … (1m 5s, 14:32)"). Scope-Entscheidung Paul (Option B):
Shell + eclipseRunTests + eclipseBuildProject; abgelehnt für den Rest (siehe [open-points.md](open-points.md)).

## R-TD-1 — `CallStats`: ein geteilter Stats-Suffix-Helper ❌

Neuer kleiner Helper im core (`org.sterl.llmpeon.shared`), der aus Start-Zeitpunkt und Uhr die
Suffix-Zeile `(3s)` bzw. `(3s, 14:32)` baut (Dauer via `StringUtil.humanElapsed`, Uhrzeit
`HH:mm` lokal). **Clock injizierbar** (Vorbild `shared.Timer`) für deterministische Tests.
`PoDelegateTool.dispatchStats` (Dauer + HH:mm) migriert darauf — **One behaviour, one
implementation** (AGENTS): das Muster lebt im core, nicht privat in einem Tool. Der
PoDelegate-Output ändert sich dabei **nicht** (Pin).

- GIVEN Dauer 65s WHEN Suffix THEN `(1m 5s)` bzw. `(1m 5s, 14:32)`; GIVEN Clock injiziert THEN
  deterministische Uhrzeit im Suffix.
- *(Automatisiert: `CallStatsTest` + Pin-Test `PoDelegateToolTest` unverändert grün.)*

#### UC-TD-1 — callStatsSuffix ❌
- GIVEN `CallStats` mit injizierter Clock, Lauf von 2.4s WHEN `suffix()` THEN `(2s, 14:32)`-Form
  (humanElapsed + HH:mm); GIVEN ohne Uhrzeit-Variante THEN `(2s)` allein; GIVEN PoDelegate-Dispatch
  THEN Output-Format unverändert (Pin). *(Automatisiert: `CallStatsTest` + `PoDelegateToolTest`
  Format-Pin.)*

## R-TD-2 — `shellRunCommand` meldet Dauer + Uhrzeit ❌

Der Output endet nach dem `Exit code`-Teil mit dem Stats-Suffix `(N, HH:mm)`. **Auch im
Failure-/Timeout-Pfad** wird die **gemessene** Dauer gemeldet (nie die konfigurierte
Timeout-Sekundenzahl als Dauer) — ein Timeout ohne echte Messdauer wäre eine Lüge über den
tatsächlichen Verlauf.

- GIVEN Command läuft 2.4s THEN Output enthält `(2s, <HH:mm>)` am Ende; GIVEN Timeout THEN
  ehrlicher Fehler **mit** der gemessenen Dauer, nicht der konfigurierten. *(Automatisiert:
  `ShellToolTest`.)*

#### UC-TD-2 — shellReportsDurationAndTime ❌
- GIVEN Shell-Aufruf (echter kurzer Befehl) WHEN Antwort an LLM THEN letzter Abschnitt enthält
  Dauer + Uhrzeit im R-TD-1-Format; GIVEN Timeout THEN Dauer = gemessen.

## R-TD-3 — `eclipseRunTests` meldet Dauer + Uhrzeit ❌

Der Test-Report (`Test run / Tests / Skipped / Failures`) wird um den Stats-Suffix ergänzt. Auch im
Timeout-Fall (`5 min`-Guard) steht die gemessene Dauer, nicht die konfigurierte Grenze.

- GIVEN Suite-Lauf THEN Report endet mit `(N, HH:mm)`. *(Automatisiert: Plugin-Test, Suite-Stub.)*

#### UC-TD-3 — runTestsReportIncludesStats ❌
- GIVEN Testlauf-Antwort WHEN Report gerendert THEN Dauer + Uhrzeit am Ende; GIVEN Timeout THEN
  gemessene Dauer.

## R-TD-4 — `eclipseBuildProject` meldet Dauer + Uhrzeit ❌

Der Build-Report bekommt denselben Stats-Suffix wie R-TD-3 — identisches Muster, identischer
Helper, keine Sonderlogik.

- GIVEN Build-Aufruf THEN Report endet mit `(N, HH:mm)`. *(Automatisiert: Plugin-Test.)*

#### UC-TD-4 — buildReportIncludesStats ❌
- GIVEN Build-Aufruf THEN Report endet mit Stats-Suffix; Failure-Pfad inklusive.

## Out of Scope (bewusst, Paul 2026-09-22)

- `webFetchAsMarkdown` (Fetch-Zeit), `memoryAdd/Replace` (Datum), `JavaDebugTool.continue`
  (Dauer+Uhrzeit), searchAgent/compactSession/lint (optional) → eigene kleine Stories, falls
  gewollt — open-points.md. Read/Grep/Write-Familie bleibt ohne Zeitinfo (stateless, Rauschen).
- Kein `(finished …)`-Wortlaut — Suffix-Format ist exakt das PoDelegate-Muster `(N, HH:mm)`.
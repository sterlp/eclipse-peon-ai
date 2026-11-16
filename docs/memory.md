# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Arbeit läuft auf **main** (Stand 2026-11-16); Paul committet/released selbst.
>
> **#173 Model-Select (main `5dc254dd`, Autor Demmtop):** SOLL-Ersatz für R-ML5 mit Paul geklärt
> (Combo folgt aktivem Agenten) → `peon-plan/ticket-173-chat-model-select-soll.md` als
> Ticket-Anhang für den Autor. Offen: Revert auf main ja/nein · R-ML5 in `model-loading.md`
> nachziehen, sobald #173 im Branch ist (heute nicht im Workspace).
> Lokal ungepusht (Push 403, Git-Account `PaulNetze`): Merge main `7c270466` + Skill-Sync-Commits.
> Paul überträgt per `peon-plan/web-factory-4-skill-sync.patch` auf den anderen Rechner
> (dort erst `git merge origin/main`, dann `git am`).

## Stand 2026-11-16

- Kein laufender Zyklus. R-SEL-6, R-PT-3, R-RT-1 ✅ — Release durch Paul.
- ⏳-Sweep 2026-11-16 erledigt: einziger ⏳ = Codegraph-Windows-Fallback (Paul testet bei
  Windows-Lauf); technische Notizen auf ❓ Backlog. Uncommittet (Paul): compressor.md,
  AGENTS-JON.md, Ork-Rename in docs/**, Homepage index.md + setup/agents.md.
- **AI Factory lebt im eigenen Projekt `/aifactory-parent`** — `llmpeon-parent/aifactory/` = Reste.

## Bekannte Lint-Altlasten (Bestand, nicht R-RT-1)

- Voll-Lint: ~260 `VERWAIST` R-*-Marker (Linter kennt R-Definitionen nicht → open-points ❓
  „R-*-Regeln haben kein Linter-Gate"), `DOPPELT_DEFINIERT`/`PRAEFIX_*` in tool-confirmation,
  default-inheritance/model-config-widget, compact-architektur; `UNBELEGT` UC-BAP-1…5, UC-CT-3/5;
  `UC_OHNE_REGEL` UC-WPV-1/2. Kandidat für einen Lint-Aufräumzyklus.

## Beobachtete Tool-Probleme
- Ungeklärt (nicht gefixt): Insert-Meldung „newContent required" am Dateiende und ReplaceLines
  „fügt ein" — erst messen.
- PDE-Einzellauf `EclipseWorkspaceWriteFileToolTest` lief einmal in 5-min-Timeout (nach mehreren
  PDE-Läufen), danach grün — erst bei Wiederholung untersuchen.

## Offen für Paul (je eine Frage / Bestätigung)

1. ❓ Altbestand Reparatur-Zyklus: Edit-Tool-Falle (upstream melden?), manuelle Nachweise.
2. ❓ Status-Bug „waiting for AI... verschwindet bei File-Read" → open-points.md, erst messen.

## Backlog (nicht gestartet)

- **Search-Agent als vollwertiger interner Agent** (R-LSR-7 🚧) · **Rename-Sicherheit über
  stabile `id()`** (agenten-namen-im-chat.md 🚧).
- **P3:** SSE-Client-Kopien (5×) in Fabrik-Tests extrahieren.
- **P1-Variante:** Cancel-/Fehler-Pfad-Release ohne atomaren Queue-Blick — test-first (⏳).
- Async-Shell-Tool (Paul-Prio) → async Agents; Merge-Check „geteilte disk-Tool-Instanzen" vorher.
- Bug-Triage-Reste: totes MCP-Tool in Spec-Liste, `PoDelegateTool.dispatch`-Catch, ApiRetry-
  Fehlerklassen, Header-State-Leak, Auto-Compact bei Context-Overflow, Warnungs-Aufräum-Zyklus,
  „Kein `throw` ohne Message".
- Klein: `MutableClock` 8× → Fixture · `AbstractAgent.asyncCall` ohne Aufrufer · Lint-Baseline ·
  `StringUtil.oneLine`-Konsolidierung · `effectiveUrl`-Schnitt.
- Read-Tools: Refresh-Fehler in `refreshScope` wird nur geloggt (Bestand, bewusst nicht in R-RT-1).

## Notizen

- Agentennamen: Jon (PO), Plan, Dev, Review — Ork-Namen nur noch als Synonym im Glossar.
- System-Prompts generisch; Übersicht `core/docs/system-prompts.md`.
- Commit: EXPLIZITE Pfadliste; Pauls WIP nie (`AGENTS*.md` Root, `peon-plan/**`, `README.md`,
  `ai-e2e-test/**`, `bugs/**`, `homepage/**` nur auf Ansage).
- Lint: `docRoots`/`testRoots` weglassen = Auto-Discovery; `findings:`-Zeile melden; `idPattern`
  **mit** `[a-z]?`; Überschriften in Notiz-Docs nie mit einer Regel-ID beginnen.
- JUnit `@Timeout`-Falle: Daemon-Probe-Muster statt `@Timeout`.
- Eclipse-Plugin-Tests: `eclipseBuildProject` vor JUnit-Lauf; nach core-Änderung Reactor-Route A.

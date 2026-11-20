# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Arbeit läuft auf **main** (== `origin/main`, `9c872afe`); Paul committet und released selbst.

## Stand 2026-11-20

- Kein laufender Zyklus. R-SEL-6, R-PT-3, R-RT-1 ✅ — Release durch Paul.
- Working Tree (Jon, uncommittet): `.agents/skills/eclipse-preferences/SKILL.md` (neu §6
  „Deferred-commit widgets — flush before every programmatic refresh") + `wiki/skill-impact.md`
  (Ledger-Zeile).
- **AI Factory lebt im eigenen Projekt `/aifactory-parent`** — `llmpeon-parent/aifactory/` = Reste.

## Offen für Paul (je eine Frage / Bestätigung)

1. ❓ **Flush der offenen Modell-Combo-Eingabe:** nur vor dem Agentenwechsel (heutiges SOLL und
   Code, ein Aufrufer `AIChatView.java:502`) oder vor **jedem** `refreshModelSelector()`
   (`442/483/505/538/545` = Config-Apply, Reload, Handoff)? Jon-Empfehlung: jeder Pfad → dann
   `R-ML5` in `model-loading.md` je Pfad präzisieren und einen Test je Pfad.
2. ❓ Altbestand Reparatur-Zyklus: Edit-Tool-Falle (upstream melden?), manuelle Nachweise.
3. ❓ Status-Bug „waiting for AI... verschwindet bei File-Read" → open-points.md, erst messen.

## Beobachtete Tool-Probleme

- Ungeklärt (nicht gefixt): Insert-Meldung „newContent required" am Dateiende und ReplaceLines
  „fügt ein" — erst messen.
- PDE-Einzellauf `EclipseWorkspaceWriteFileToolTest` lief einmal in 5-min-Timeout (nach mehreren
  PDE-Läufen), danach grün — erst bei Wiederholung untersuchen.

## Bekannte Lint-Altlasten (Bestand)

- Voll-Lint: ~260 `VERWAIST` R-*-Marker (Linter kennt R-Definitionen nicht → open-points ❓
  „R-*-Regeln haben kein Linter-Gate"), `DOPPELT_DEFINIERT`/`PRAEFIX_*` in tool-confirmation,
  default-inheritance/model-config-widget, compact-architektur; `UNBELEGT` UC-BAP-1…5, UC-CT-3/5;
  `UC_OHNE_REGEL` UC-WPV-1/2. Kandidat für einen Lint-Aufräumzyklus.

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

- Commit: EXPLIZITE Pfadliste; Pauls WIP nie (`AGENTS*.md` Root, `README.md`, `ai-e2e-test/**`,
  `bugs/**`, `homepage/**` nur auf Ansage); `peon-plan/**` ist ungepinnt und wird bewusst leer
  gehalten.
- Lint-Konvention: `docRoots`/`testRoots` weglassen = Auto-Discovery; echte `findings:`-Zeile
  melden; `idPattern` UC-only **mit** `[a-z]?`; Überschriften in Notiz-Docs nie mit einer Regel-ID.
- Agentennamen Jon (PO) · Plan · Dev · Review — Ork-Namen nur Synonym im Glossar; Übersicht der
  System-Prompts `core/docs/system-prompts.md`, Prompt-Edits macht Jon selbst.
- Build/Test- und SWT-Fallen stehen in den Skills (`peon-build-test`, `eclipse-dpe`,
  `eclipse-preferences`), nicht hier.

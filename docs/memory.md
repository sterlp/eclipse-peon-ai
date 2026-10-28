# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Branch `web-factory`.

## Jetzt

- **R-CC-18.5 ✅ gebaut + committet:** Commit `28b49cfa` auf `web-factory` (nicht gepusht), Plan
  archiviert (`peon-plan/overview-done-2026-10-28-14-22.md`, untracked). Core Surefire 1398/0,
  Da Dok CONCERNS ohne Blocker, Docs geflippt (R-CC-18/18.5/UC-CC-2 ✅). **Paul: squash-merge + release.**
- **Prompts (nicht committet):** `doc-method.md` + `paco.md` neu, `po.md` (nur Charakter + Rolle),
  `review-agent.md` neu (Pauls 8 Punkte). **Paul baut Loader:** Jon = `default + po + doc-method +
  po-delegation`, `doc-method.md` per `PromptLoader.load` (nicht `loadWithDefault`),
  `AiPoAgentPromptParityTest:27` nachziehen. Paco-Agent = R-PC-1…8 ❌. Web: [aifactory/docs/memory.md](../aifactory/docs/memory.md).
- **Release (Plugin):** CHANGELOG `[Unreleased]` gepflegt (R-CC-18.5-Zeile im Commit), Paul merged/released.
- Lint 2026-10-28: Core 157/157 + aifactory 76/76, 0 Befunde.

## Offen für Paul (je eine Frage)

1. `docs/memory.md:3-4` (Header-Zitate „Nur Jon schreibt docs/**" / „Paul = Mensch und Da Mek") im
   Working Tree gelöscht — gewollt? (Diff ist uncommitted, ich habe sie nicht zurückgeschrieben.)
2. `bugs/compact-regression.md` (205k > 170k): Lauf vor oder nach dem 27.10.? Danach → Restlücke
   „Budget vs. Modellfenster" als nächster Bug-Zyklus.
3. Smoke R-CT-7/UC-CT-17 (bleiben ❌): lange Session → Pre-Turn-Compact → Delegation; frischer Chat →
   erste Runde delegiert; Button-Compact.
4. `org.sterl.llmpeon.test` baut nicht (`jakarta.annotation [2.1,3)` fehlt im Target) → Plugin-Tests blind.
5. `non-peon-ai-agents-md/AGENTS*.md` im Arbeitsbaum gelöscht — gewollt?
6. Merge/Squash `story/night-2026-10-23` + Branch löschen · E2E-Doc-Fixes (`compact-e2e.md`,
   `search-agent-e2e-test.md`, `webtools-test.md`).
7. ⏳-Rückversicherungen in [open-points.md](open-points.md) (Delegations-Compact, Briefing-Schema,
   `codegraph`-Name, R-CG-15, R-CC-19, Windows-`cmd /c`).
8. Jon-Skill (GitHub) nachziehen: ZU_LANG-Zeile, Produkt-Vision, R15, Auto-Docs klein, doc-method-Split.
9. Bug-Kandidaten neu in [open-points.md](open-points.md) (totes MCP-Tool in der Spec-Liste,
   `dispatch`-Catch zu eng) — Triagen im nächsten Bug-Zyklus.

## Backlog

- **Async-Shell-Tool (Paul-Prio)**, danach async Agents ([async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- agent-origin (Plugin, später) · Homepage 2.15.0 · Bug-Triage T-1/T-1a/T-3/T-4/T-9 · E2E E2/Refresh-Seam · `result 0k`.
- Klein: `MutableClock` 7× → Fixture · `AbstractAgent.asyncCall` ohne Aufrufer · Lint-Baseline Core
  (14 UNBELEGT_ERLEDIGT, 13 VERWAIST UC-DEF-*) · historische `*-plan.md` löschen?
- aifactory: V6–V10-Folgen in [aifactory/docs/open-points.md](../aifactory/docs/open-points.md),
  `aifactory-architektur.md` Config-Abschnitt vor Config-Planung.
- [open-points.md](open-points.md) ist ~400 Zeilen → eigener Aufräum-Zyklus mit Paul.

## Notizen

- System-Prompts generisch, keine Sprache/Plattform bevorzugen.
- Commit: EXPLIZITE Pfadliste; Pauls WIP nie (`README.md`, `peon-plan/`, `ai-e2e-test/**`,
  `non-peon-ai-agents-md/**`, `pauls-product-vision.md`, `AGENTS*.md`, `bugs/**`).
- Lint: `docRoots`/`testRoots` immer einschränken. Nach jedem Edit Zielbereich zurücklesen.
- `ThreadSafeMemory.add` joint UserMessages — Seed-Fixtures mit `AiMessage` trennen.
- Windows: `indexOf('\n')` in `lineSeparator()`-Strings.

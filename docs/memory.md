# Open Ends / Memory — Session-Zwischenstand

> Nur Jon schreibt `docs/**`. Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Paul = Mensch und Da Mek zugleich: **ein Thema pro Nachricht**, kurz; Paul committet/pusht selbst.
> Branch `web-factory`.

## Jetzt

- **R-CC-18.5 bauen (❌):** Direkt-Compact zustandslos, Compressor über Turn-`ApiRetry`, Fehlschlag
  beendet den Turn. Plan `peon-plan/overview.md` von Jon abgenommen (inkl. §7a Ausführungsregeln,
  zwei Schritte mit Halt). Danach: Review (Da Dok) → `compact.md` R-CC-18/UC-CC-2 auf ✅.
- **Prompts (nicht committet):** `doc-method.md` + `paco.md` neu, `po.md` (nur Charakter + Rolle),
  `review-agent.md` neu (Pauls 8 Punkte). **Paul baut Loader:** Jon = `default + po + doc-method +
  po-delegation`, `doc-method.md` per `PromptLoader.load` (nicht `loadWithDefault`),
  `AiPoAgentPromptParityTest:27` nachziehen. Paco-Agent = R-PC-1…8 ❌. Web: [aifactory/docs/memory.md](../aifactory/docs/memory.md).
- **Release (Plugin):** CHANGELOG `[Unreleased]` gefüllt, Paul merged/released.
- Lint 2026-10-28: Core 157/157 (nur Baseline-Befunde, s. Backlog), aifactory 76/76, 0 Befunde.

## Offen für Paul (je eine Frage)

1. CHANGELOG `[Unreleased]`/Changed: je eine Zeile für neues Review-Verhalten + Paco-Vorbereitung? (Empf. ja)
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

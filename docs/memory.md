# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Branch `main` = `b2fd5564` (web-factory squash-gemergt via PR #167), synchron mit origin.

## Jetzt

- **Prune (2026-10-29):** `fix-prompts` gelöscht (vollständig in main, squash #159). Offen:
  `new-config` (48 ahead, 135 Dateien, **nie in main gelandet**) + `story/po-compact-2026-09-13`
  (Substanz in main, Reste = scratch/archive) — Lösch-Freigabe Paul steht aus.
- **Uncommitted (ride mit nächstem Inkrement-Commit):** CHANGELOG (+25/−3, Restructure),
  docs/open-points.md, `.agents/skills/wiki/skill-impact.md` + `test-honesty`-Patch,
  `peon-plan/overview-done-2026-10-28-14-22.md` (untracked).
- **R-AEM-3 gebaut + committed (Branch `story/aem-error-url`, 2026-10-29):** Trigger = nutzlose
  Message — blank **oder** JDK-Envelope mit bloßem Cause-Klassennamen →
  `AI call to <effective URL> failed: <Klasse> — <cause>` am Choke-Point `ToolLoopRequest.call`;
  Model-API- vs. MCP-Connect-Fehler unterscheidbar. Tests UC-AEM-4/5/6/7
  ([agent-error-memory.md](../org.sterl.llmpeon.core/docs/agent-error-memory.md)).
  Status-Flip ❌→✅ = Jon nach Review.
- **Danach:** Codegraph-Toolset-Slice (R-CG-6-Änderung, `CODEGRAPH_MCP_TOOLS=explore,search,files`,
  Paul 2026-10-29: ja) + **Homepage-Update 2.15.0** (Paul: müssen wir machen — AGENTS.md-Verstoß
  des Nachtzyklus).

## Offen für Paul (je eine Frage)

1. **Deferred-Reset Compact-UI** (⏳, open-points) — „müssen wir klären".
2. Branch-Löschung `new-config` + `story/po-compact-2026-09-13` (siehe oben).
3. ⏳-Rückversicherungen in [open-points.md](open-points.md): Delegations-Compact-Kosten
   (ADR-0071), Compact-Briefing-Schema, `codegraph`-Name reserviert, R-CG-15, R-CC-19,
   Windows-`cmd /c`, Pauls ShellTool-WIP, Read-Tools-OutputTrim, `PeonAiService.current()`.
4. Bug-Triage (noch offen): totes MCP-Tool in Spec-Liste, `PoDelegateTool.dispatch`-Catch,
   ApiRetry-Fehlerklassen-Tabelle, Header-State-Leak, Auto-Compact bei Context-Overflow
   (nächster Zyklus laut Order 2026-10-14), `org.sterl.llmpeon.test` baut nicht.

## Backlog

- **Async-Shell-Tool (Paul-Prio)**, danach async Agents ([async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- agent-origin (Plugin, später) · Bug-Triage T-1/T-1a/T-3/T-4/T-9 · E2E E2/Refresh-Seam · `result 0k`.
- Klein: `MutableClock` 7× → Fixture · `AbstractAgent.asyncCall` ohne Aufrufer · Lint-Baseline Core
  (14 UNBELEGT_ERLEDIGT, 13 VERWAIST UC-DEF-*) · historische `*-plan.md` löschen?
- aifactory: V6–V10-Folgen in [aifactory/docs/open-points.md](../aifactory/docs/open-points.md),
  `aifactory-architektur.md` Config-Abschnitt vor Config-Planung.
- [open-points.md](open-points.md) ist ~375 Zeilen → eigener Aufräum-Zyklus mit Paul.

## Notizen

- System-Prompts generisch, keine Sprache/Plattform bevorzugen.
- Commit: EXPLIZITE Pfadliste; Pauls WIP nie (`README.md`, `peon-plan/`, `ai-e2e-test/**`,
  `non-peon-ai-agents-md/**`, `pauls-product-vision.md`, `AGENTS*.md`, `bugs/**`).
- Lint: `docRoots`/`testRoots` immer einschränken. Nach jedem Edit Zielbereich zurücklesen.
- `ThreadSafeMemory.add` joint UserMessages — Seed-Fixtures mit `AiMessage` trennen.
- Windows: `indexOf('\n')` in `lineSeparator()`-Strings.

# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Branch `story/aem-error-url` (gepusht), Base = main `b2fd5564`.

## Jetzt

- **R-AEM-3 ✅ gebaut + geflippt:** Commits `bb39bb06`, `e728e50e`, `6a4bd29d`, `68abc9d5` auf
  `story/aem-error-url` (alle gepusht). Ablauf: Inkrement 1 → Da Dok **REJECTED** (B1: Blank-Trigger
  erreicht den Choke-Point nie; B2: Tests bauten den Trigger selbst) → Messung über echte Schichten →
  SOLL korrigiert (Trigger = „nutzlose Message": blank ODER Cause-Klassenname, UC-AEM-7) → Delta
  `6a4bd29d` → Da Dok CONCERNS ohne Blocker → Herkunfts-Test über echte `StreamingBridge`-Schicht
  (`68abc9d5`). Surefire core **1407/0**, Lint 29 Baseline ohne UC-AEM, Mutationen bewiesen.
  Docs geflippt (R-AEM-3, UC-AEM-4/5/6/7 ✅); open-points ⏳ Trigger-Rückversicherung eingetragen.
  **Plan-Archivierung (planImplemented) direkt nach diesem Abschnitt.**
- **Merge/Release:** Paul merged `story/aem-error-url` (squash) + Release; **Homepage-Update 2.15.0
  im selben Zug** (Paul 2026-10-29: HP wird im Release aktualisiert — webSearch, Compact-Sichtbarkeit,
  Skill-Roots, last-search-rolling, R-AEM-3-Fehlermeldung).
- **Arbeitsbaum-Rauschen (Paul fragen):** `@SuppressWarnings("null")`-Zeilen in `ExceptionUtil.java`,
  `ToolService.java`, `ShellTool.java`, `AgentToolConfigTest.java`, `DefaultCodegraphProbeTest.java`
  (11:52, IDE-Quickfix?) — uncommitted, nicht committet.
- **Danach geplant:** Codegraph-Toolset-Slice (R-CG-6-Änderung, `CODEGRAPH_MCP_TOOLS=explore,search,files`,
  Paul 2026-10-29: ja).

## Offen für Paul (je eine Frage)

1. **Deferred-Reset Compact-UI** (⏳, open-points) — „müssen wir klären".
2. ⏳-Rückversicherungen in [open-points.md](open-points.md): Delegations-Compact-Kosten (ADR-0071),
   Compact-Briefing-Schema, `codegraph`-Name reserviert, R-CG-15, R-CC-19, Windows-`cmd /c`,
   Pauls ShellTool-WIP, Read-Tools-OutputTrim, `PeonAiService.current()`.
3. Bug-Triage (noch offen): totes MCP-Tool in Spec-Liste, `PoDelegateTool.dispatch`-Catch
   (jetzt zusätzlich durch R-AEM-3-Wrap minimal leichter erreichbar — Suspicion aus Review),
   ApiRetry-Fehlerklassen-Tabelle, Header-State-Leak, Auto-Compact bei Context-Overflow
   (nächster Zyklus laut Order 2026-10-14), `org.sterl.llmpeon.test` baut nicht.

## Neu aufgenommen (Paul 2026-10-29, Backlog — nicht gestartet)

- **„Kein `throw` ohne Message"** (Regel-Kandidatin, ❓ open-points) — Inventar message-loser
  Throws, SOLL in ground-rules.md, dann Batches.
- **Warnungs-Aufräum-Zyklus** (❓ open-points): core + plugin, eigener Branch, Inkrement 1 =
  VOLL-Inventar + Triage (nichts ändern), dann Batches je Warnklasse. `@SuppressWarnings`-Rauschen
  (5 Dateien, uncommitted) im selben Zyklus klären.

## Backlog

- **Async-Shell-Tool (Paul-Prio)**, danach async Agents ([async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)) — Merge-Check-Item „geteilte disk-Tool-Instanzen / mutable `AbstractTool.request`" VOR dem Async-Bau festlegen (open-points, Night-Cycle 4).
- agent-origin (Plugin, später) · Bug-Triage T-1/T-1a/T-3/T-4/T-9 · E2E E2/Refresh-Seam · `result 0k`.
- Klein: `MutableClock` 7× → Fixture · `AbstractAgent.asyncCall` ohne Aufrufer · Lint-Baseline Core
  (14 UNBELEGT_ERLEDIGT, 13 VERWAIST UC-DEF-*) · historische `*-plan.md` löschen? ·
  `StringUtil.oneLine`-Konsolidierung (Da-Dok-LOW, 4. „eine Zeile"-Implementierung) ·
  `effectiveUrl`-Schnitt auf `ConfiguredChatModel` (Da-Dok-LOW).
- aifactory: V6–V10-Folgen in [aifactory/docs/open-points.md](../aifactory/docs/open-points.md),
  `aifactory-architektur.md` Config-Abschnitt vor Config-Planung.
- [open-points.md](open-points.md) ~370 Zeilen → eigener Aufräum-Zyklus mit Paul.

## Notizen

- System-Prompts generisch, keine Sprache/Plattform bevorzugen.
- Commit: EXPLIZITE Pfadliste; Pauls WIP nie (`README.md`, `peon-plan/`, `ai-e2e-test/**`,
  `non-peon-ai-agents-md/**`, `pauls-product-vision.md`, `AGENTS*.md`, `bugs/**`).
- Lint: `docRoots`/`testRoots` immer einschränken. Nach jedem Edit Zielbereich zurücklesen.
- `ThreadSafeMemory.add` joint UserMessages — Seed-Fixtures mit `AiMessage` trennen.
- Windows: `indexOf('\n')` in `lineSeparator()`-Strings.
- **Skill-Kandidat (Da-Dok-Review, umzusetzen):** `test-honesty` — „Regel an der Form einer externen
  Exception ⇒ ein Test muss diese Form durch die echte Wickel-Schicht reproduzieren" (B1-Lektion).

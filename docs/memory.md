# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Branch `main` — R-AEM-3 ist gemergt (`27ed5b21`, Tag 2.15.1).

## Jetzt — Origin im Core ([ADR-0078](adr/0078-origin-in-core-owner-monitor.md), 2026-10-30)

- **Plan:** [peon-plan/overview.md](../peon-plan/overview.md) — 5 Inkremente: 0 Fabrik-Entgiftung ·
  1 Core `OwnerMonitor` (+ 3 Emitter-Stempel) · 2 `LoopOwner`-Cleanup · 3 Plugin-Sicht ·
  4 Fabrik-Sicht auf `origin`. Von Jon abgenommen; Da-Dok-REJECTED (6 Bugs) abgearbeitet (§8 im Plan).
- **SOLL:** ADR-0078 (D1–D6, Accepted) — `[AF-ADR-002](aifactory/docs/adr/002-…)` teilweise abgelöst;
  `aifactory/docs/chat.md` (R-WP-4, UC-WP-39/40 + Fehler-Fall) und `aifactory-architektur.md` nachgezogen.
- **Regeln für den Bau:** Paul committet selbst (kein Commit/Push durch Agenten) · Core-Tests-Baseline
  **1407** · `lintDocsAndTests` je Inkrement als Gate, umgeschriebene Tests behalten ihre `// UC-…`-Anker ·
  **keine** Tool-Call-Id-Buchführung (Decision 6) · `AgentsTool` = Paul WIP, out of scope ·
  Fabrik hängt am stale Core-Jar (2.8.1 vom 26.10.) — Inc 0 entgiftet, danach erst `install`.
- **⏳ für Paul:** Fehler-Ergebnis einer Delegation erscheint wörtlich als Mitglieder-Zeile
  ([aifactory open-points](../aifactory/docs/open-points.md)) — so gebaut, andersfall = neuer Core-Mechanismus.

## Danach

- **Arbeitsbaum-Rauschen (Paul fragen):** `@SuppressWarnings("null")`-Zeilen in `ExceptionUtil.java`,
  `ToolService.java`, `ShellTool.java`, `AgentToolConfigTest.java`, `DefaultCodegraphProbeTest.java`
  (11:52, IDE-Quickfix?) — uncommitted, nicht committet.
- **Codegraph-Toolset-Slice** (R-CG-6-Änderung, `CODEGRAPH_MCP_TOOLS=explore,search,files`,
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
- agent-origin: **läuft** (siehe oben) · Bug-Triage T-1/T-1a/T-3/T-4/T-9 · E2E E2/Refresh-Seam · `result 0k`.
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

# Open Ends / Memory — Session-Zwischenstand

> Zwischenstand, kein Protokoll: Erledigtes fliegt raus.
> Branch `main` — R-AEM-3 ist gemergt (`27ed5b21`, Tag 2.15.1).

## Jetzt — Origin im Core + Namensalignment: GEBAUT, Review + Delta abgeschlossen (2026-10-30)

- **Alle 6 Inkremente + Docs/Prompt-Commits + Delta-Inkrement grün & gepusht** auf
  `web-factory-goon` (`c025695c` · `8c35da01` · `63bbfe3a` · `7680b1d6` · `db48c3f8` · `1ec40ef1` ·
  `afd8890b` · `7bf3181b` · **`4260475b`**). Delta `4260475b` = Da-Dok-Review-Fixes: LOW-1..4
  (NAME-Konstanten, `AgentNames.isJon` statisch, SseTurnMonitor-Abhängigkeit raus, Kommentar),
  BUG-1-Pin `AgentsMdContextItemTest#test_jon_resolvesOnlyToCleanBreakNames`, Mutation-Nachweis
  `OwnerMonitor` (fill-if-null → 2 rote Tests wie prognostiziert, revertet). Gates: Core **1424**,
  Fabrik **108/0/0/0**, UI **25/25**, PDE-gezielt 3/0+6/0 (volle PDE-Suite im Delta **nicht**
  gefahren), Lint 10 = Baseline.
- **Da-Dok-Review abgeschlossen (REJECTED → Befunde behandelt):** BUG-2/3/5 + Homepage-Reste von Jon in
  Docs gefixt (ADR-0078 an gebauten Mechanismus angepasst, `aifactory/docs/memory.md` „Jetzt" neu,
  header-Doc-Ork-Namen gezogen); BUG-1 per Clean Break entschieden (⏳ open-points: Paul
  bestätigen lassen); BUG-4 = ❓ Paul (AGENTS.md); BUG-6 = ADR-0078-Abweichungsnotiz. P1
  (Queue-Drain-Race), V1 (setStaticContext), P2 (Peon-Scaffold) → ❓ open-points.
- **⚠️ Eigene `AGENTS-PO.md` dieses Repos lädt für Jon nicht mehr** — Paul bei Gelegenheit in
  `AGENTS-JON.md` umbenennen (steht auch in der Release-Note).
- **Manuelle Nachweise offen (Paul):** Browser-Proof Fabrik auf 8099 + Plugin-Harness
  (`test-chat.html`, zwei Klicks) — Da Mek kann sie nicht allein fahren.
- **⏳/❓ offen:** siehe [open-points.md](open-points.md) Abschnitt 2026-10-30.
- **Rules für den Bau:** Paul committet selbst · Core-Baseline **1424** · `lintDocsAndTests` je
  Inkrement, UC-Anker bleiben · keine Tool-Call-Id-Buchführung (ADR-0078 Decision 6) ·
  `AgentsTool` = Paul WIP, out of scope (`AgentsTool.java:126,136,193,195` Virtual-Thread-Emission).
- **SOLL:** ADR-0078 (D1–D6, Accepted, 2026-10-30 angepasst) + ADR-0079 (Namen); `aifactory/docs/chat.md`
  (R-WP-4, UC-WP-39/40 ✅ + ⏳ Fehler-Fall), `aifactory-architektur.md`, AF-ADR-002 nachgezogen.

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

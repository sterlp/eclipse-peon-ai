# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.
> Diese Datei ist ein Zwischenstand, kein Protokoll: Erledigtes fliegt raus.

## 2026-10-25 — RESEED-FORM ✅ single UserMessage (offen: Commit)

- **SOLL (R-CC-9, ADR-0076):** nach Compact genau **eine** `UserMessage` — Turn-Context-Blöcke,
  **letzter** TextContent = `Session compacted:` + Leerzeile + Summary. Kein AiMessage-Zweig
  (Prefill-Semantik im Tool-Loop, `ThreadSafeMemory.add` joint aufeinanderfolgende UserMessages).
- **Docs nachgezogen (Jon):** compact.md R-CC-9 (+BDD) · context-message-concept.md (4 Stellen) ·
  po-agent-jon.md R16-WEIL · compact-architektur.md (Δ + Mermaid) · ADR-0076 (Datei umbenannt) +
  adr/index.md.
- **Tests nachgezogen (Da Mek):** 14 Stellen pinnten die alte 2-Message-Form; Helfer
  `UserMessages.lastText`; neue Tests `toolTriggeredCompactReseedsMarkerBlockWithItsSummaryOnly`,
  `veryShortSummaryCompactsIntoTheReseedAndReportsResult0k`,
  `reseededMarkerBlockIsStateAndDropsItsSummaryWhenStaged`. **UNCOMMITTED.**
- **B2 widerlegt:** `result 0k` = `StringUtil.toK(summary.length())` (jede Summary < 1000 Zeichen);
  „Marker ohne Summary" war ein Fehllesen des eigenen Prompts. `FAILED_EMPTY` schreibt keinen Marker.

## 2026-10-25 — Entscheidung: Reseed fällt bei Stufe 1+ heraus (Paul)

- Unter Budget geht der Reseed voll in den nächsten Compact-Input; muss gekürzt werden (Stufe 1+),
  rendert er **state-only** — die alte Summary fällt heraus. **Paul: bewusst so**, keine
  Reseed-Ausnahme, keine Kappungs-Sonderregel („nicht verkünsteln") — steht in compact.md R-CC-9.
- Offen (klein, keine Regel): `result 0k` ist ehrlich gerundet, aber irreführend — Zeichen statt k
  für < 1000 wäre die Disclosure-Variante. Nicht gebaut.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Squash** `story/night-2026-10-23` + Branch löschen; danach Reseed-Commit aufsetzen
   (Branch-IST vorher prüfen, memory-Tool #53: Squash macht die Merge-Base alt).
2. ❓ **Commit der Reseed-Arbeit** (Docs + Tests + Kommentar-Fixes) — auf welchem Branch?
3. ❓ **E2E-Doc-Fixes** an `compact-e2e.md`, `search-agent-e2e-test.md`, `webtools-test.md`
   (Reihenfolge/DDG-Budget, AiMessage-Denke, Cache-Claim) — bereit, warten auf Ja.
4. ⏳ Delegations-Compact pro Delegation · ⏳ Compact-Briefing-Schema (compressor.md) · ❓ Fixture-Duplikat ·
   ❓ R-Regeln ohne Linter-Gate · ❓ In-Loop-Direkt-Compact fängt Exceptions nicht ·
   ⏳ Reserved-Name `codegraph` · ⏳ R-CG-15 Monorepo-State-Refresh · ⏳ R-CC-19-Semantik+Tilde ·
   ⏳ Windows-`cmd /c`-Fallback · 🔒 S3 (🗜 in PoDelegateTool).

## Ältere, noch offene Fäden (kurz)

- **Bug-Triage 2026-10-24:** T-1 Skill-Routes nach Reload (braucht Details von Paul) · T-1a
  per-Root-Reload-Output (Ja/Nein offen) · T-4 HTML→Markdown lässt `<pre>` zerfließen (niedrig) ·
  T-9 Shutdown vs. laufende Jobs (von Paul geparkt, Analyse in
  [chat-job-lifecycle.md](../org.sterl.llmpeon/docs/chat-job-lifecycle.md)) · T-3 Engine-Seite
  „kein Retry" untestbar (Follow-up).
- **E2E-Triage 2026-10-25:** E2 Compact-Hint-Zeile (Paul una responded) · Refresh-Seam-Duplikat
  (PlanFileView/EclipseWorkspaceReadFileTool → gemeinsame Klasse) · E3/F1/F3 von Paul als egal
  abgehakt.
- **Homepage 2.15.0** (webSearch, Compact-Sichtbarkeit, Skill-Roots, last-search-rolling) steht aus.
- **Doc-Hygiene-Kandidat:** historische `*-plan.md` in `org.sterl.llmpeon*/docs` (z. B.
  `sklaven-kontext-plan.md`, `agenten-status-im-header-mvp-plan.md`) beschreiben IST/Plan-Stände
  statt SOLL und tragen alte Namen (`JonDelegateTool`) — Paul entscheidet: löschen oder als
  historisch markieren.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio** (wichtiger als async Agents). Danach async Agents (Top-3 in
  [async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- **Issue #163 („Tool Calls retten")** — ⏳ von Paul geparkt („machen wir nicht aktuell").
  Recherchiert: langchain4j NICHT das Problem; Gegenmittel = Rescue-Heuristik im ToolService-Loop.
- Lint-Baseline-Befunde in einen Docs-Zyklus aufnehmen.
- R-CG-14 (Generation + `disposed` statt Lock) · R-CIB-4 Stufe 2 · R-CC-7 Fehlerklassen-Tabelle
  ApiRetry (❓) · Header-State-Leak (❓) · Context-Overflow-Zyklus (❓) · McpService
  `connect()`-Rollback-Test (ADR-0069).
- **OP-STREAM-1:** Stream-Hänger bei Verbindungsverlust/Notebook-Sleep, keine Aliveness-Erkennung
  → [streaming-display.md](../org.sterl.llmpeon/docs/streaming-display.md).
- Klein: Disclosure-Inline-Fragmente in `RollingSearchContextTest` · SkillTool
  `skillReadFile-diagnosis.md` liegt in `src/main` · allgemeine `tool.tools`-Sortierung
  (Paul: „bei Zeit") · known-benign-Warnings-Doc.

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20): nichts Repo-Spezifisches, minimale
  Änderungen, keine Docs-Inhalte — nur Verweise. Übersicht: docs/system-prompts.md.
- **Commit-Regel:** immer EXPLIZITE Pfadliste (`git add -- docs/ ...`), nie `git add -u`;
  Pauls WIP nie committen (`README.md`, `peon-plan/`, `ai-e2e-test/**`); Surefire-Ground-Truth +
  Commit-Hash aktiv einfordern, DONE-Claims gegen den IST prüfen. Evidenz (Surefire-Nummern,
  Messreihen) nie in docs — Commit-Messages + memory-Tools.
- **Tool-Trap:** `diskRenameResource` Erfolg melden ohne zu verschieben — nach Renames
  verifizieren. Edit-Fallen (replaceLines-Insert, Edit-Deletion-Teil-Match): nach JEDEM Edit
  Zielbereich zurücklesen + Dubletten-Check; Ganzdatei-Rewrites via `diskWriteFile` bevorzugen.
- **Windows-Falle im Renderer:** in `lineSeparator()`-Strings immer `indexOf('\n')` suchen.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Docs-Restrukturierung (2026-10-22):** Feature-Doc-Pfade sind modul-qualifiziert
  (`org.sterl.llmpeon.core/docs/x.md`, `org.sterl.llmpeon/docs/x.md`); ADRs + Root-Docs
  bleiben in `docs/`. Linter-Discovery findet Modul-Roots automatisch.
- **Issues/**: asm-Versions-Tabellen (issue-142) sind Fachinhalt, keine Evidenz — bewusst behalten.

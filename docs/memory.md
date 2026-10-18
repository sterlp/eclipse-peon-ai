# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Stand 2026-10-18 (Branch `story/night-5-2026-10-16`)

- ✅ **MCP-Ergebnisgröße (R-RS-3) gebaut** — `50be78cf`: `ToolService.execute` MCP-Zweig meldet
  nach der Ausführung `MCP <name> done. (N chars)` (Start-Zeile bleibt). 2 Tests in
  `ToolServiceGroupsTest` (Surefire 1203/0). **Verifiziert (Jon):** Fehlerpfad verliert nichts —
  langchain4j wirft bei `isError=true` (`McpApplicationErrorException`/`ToolExecutionException`),
  `McpService.executeTool` fängt und liefert `"MCP tool error (<name>): …"`; die null→`""`-Koersion
  betrifft nur den echt leeren Fall (langchain4j nimmt keinen null-Text).
- ✅ **Codegraph-Header-Icon = Lupe** (`50be78cf`): 4 SVGs ersetzt (on/dark/off/off_dark), gleiche
  Dateinamen, kein Java-Wiring. Icon-Regeln (24×24 + viewBox, OFF als gedimmte SVG-Variante) in
  [codegraph.md](codegraph.md) R-CG-8.

- ✅ **Queued-User-Messages Regel 8 (`!`-Override) gebaut** — `14afd26c` (core:
  `OverrideMessageResolver` + `AiAgent.injectUserMessage`/`AbstractAgent`, 11 Tests) ·
  `42add1bd` (View: `AIChatView.resolveOutgoingMessage`, present ⇒ konsumieren, kein Queue/Turn).
  Regeln: `!` gilt **nur bei `isWorking()`**; idle = Text **wie getippt** (Paul 2026-10-18);
  `!` allein = konsumiert, nichts injiziert; `/cmd` im Rest wird aufgelöst (Body + Rest).
  ⏳ **Risiko dokumentiert, nicht gelöst:** gleichzeitiger Compact kann die injizierte Message
  clearen (`memory.clear()`) — kein Queue-Fallback. **Offen: View-Smoke M1–M6 (Paul).**
- ✅ **Compact-Lock-Doku war stale** — Feature seit 2026-09-22 gebaut/gemerged (ADR-0052);
  `docs/compact-lock.md` jetzt ✅ mit Beleg-Tabelle, UC-CT-3/5/6 = manueller Smoke offen.
- ✅ **Manueller Rest des Zyklus (nicht gebaut, weil nicht mehr nötig):** ein voller
  `mvn -B clean verify` ist grün (Core 1201/0, OSGi 386/0 + 49 Bestands-`@Ignore`, Tycho/feature/
  update-site SUCCESS). Branch `story/night-5-2026-10-16` ist releasebar; Merge = Pauls Call.
- ✅ **Docs-ID-Sweep:** `lintDocsAndTests` (voller Scope) läuft mit **9** Befunden — genau
  UC-CT-3/5/6 (`UNBELEGT`, manueller Smoke) + UC-DEF-6/UC-JD-2…6 (`MANUELL`). Kein
  `UNBELEGT_ERLEDIGT`, kein `VERWAIST` mehr. Warum es vorher nicht so war: (a) `UC-TF-1…4` waren an
  Tests markiert, im Doc aber nicht als UC definiert → jetzt in [agent-tool-filter.md](agent-tool-filter.md)
  als UC-TF-1/2/4 definiert (`UC-TF-3` bewusst NICHT — R-TF-3 ist compiler-gebunden, kein Test),
  Marker in den Tests korrekt zugeordnet; (b) 38 UC-DL-Ids waren ✅ ohne erkennbaren Marker, weil die
  Tests `// --- UC-DL-N: … ---`-Kopfzeilen nutzten (kein gültiger Marker) → Marker-Zeilen ergänzt;
  (c) `UC-DL-28` (zwei Zusicherungen) wird jetzt von zwei Tests belegt (Doc-Text nennt sie).
- ✅ **`java-debugger-tool.md` war stale:** R-JD-13 (Breakpoints ohne Session) und der
  `debugJava*`-Rename sind **gebaut** (`JavaDebugTool.java:66` `NO_SESSION_MARKER_OP`,
  `DebugBreakpointNoSessionTest` ×5, `DebugJsonUnitTest` ×2, kein snake_case-Rest) — Docs standen
  auf ❌. Jetzt ✅ mit Belegen; manuell offen: Install-Pfad Marker → laufende VM.
- ✅ **`tool-descriptions-inventory.md`:** doppelte Nummer `67` korrigiert (Continue → 68,
  Suspend → 69).
- ✅ **Tagged Results komplett (R-TR-1…9)** — Commits `f8a2fc23` (I1) · `6ff4c6eb` (I2) ·
  `9b29d0c3` (I3) · `4f9b7e3e` (Nacharbeit: Windows-NL-Fix `ContextTrimComponent:177`, Test
  `perMessageStage_sizesCapAgainstKLine_staysWithinBudget` mit Mutations-Nachweis) · Archiv
  `ab6f46bf`. **Gates: Core Surefire 1189/0, OSGi 386/0.** Da-Dok: CONCERNS ohne Blocker.
- **Was jetzt gilt:** `TaggedResult` ist der einzige Klassifikationsschlüssel (Compact über
  Tag-Präfixe, kein `instanceof`); reine Context-Blöcke werden **vor** dem Compact gedroppt
  (`context blocks dropped: K`); **kein Cap im Renderer** (`RenderOptions` gelöscht → ADR-0056
  teilweise superseded); `INFO` einmal in der static System-Message; Bild eines Datei-Contexts ist
  sein Block, dedupKey = Block-Startzeile (ADR-0029 teilweise superseded).
- **Pauls WIP unangetastet** (nicht committet, nicht anfassen): `ShellTool.java`,
  `ThreadSafeMemory.java`, `agent/tools/**`, `test/agent/model/**`, `feature-request-ShellTool.md`,
  `AbstractAgent:375` (`"tokens (estimate)"`), `AIChatView` R-SEL-2.
- Vorher im selben Branch: R-CG-7-Nachschärfung (`d38c8ab4`), Zwei-`serve --mcp`-Fix (`fe583895`,
  ADR-0069), Bounded-Caches ✅, Codegraph ✅.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Codegraph-Smoke nach Eclipse-Neustart** — es darf nur **ein** `serve --mcp`-Prozess laufen;
   Icon ON/OFF/FEHLER + `codegraph_explore` in Jon/Dev prüfen. Danach **Merge → main** (Pauls Call).
2. ❓ **`codegraph` in der Tool-Auswahl** — welcher Agent/Slot soll es standardmäßig haben?
3. ❓ **R-*-Regeln haben kein Linter-Gate** (`DocsLinter:241/254`) — R-only-Docs sind nur durch
   Da-Dok abgesichert. Entscheidung: R-Form als Definition zulassen oder UC ergänzen?
4. ❓ **`AIChatView.applyTextSelection`** (Pauls WIP): früher Return bei disposed parent überspringt
   jetzt auch `setTextSelection` — bewusst oder Nebeneffekt?
5. ❓ **Untracked** `peon-plan/codegraph-smoke-findings.md` + 2 alte `overview-done-*`: überführen
   und löschen, oder committen.
6. ❓ **Smoke `!`-Override (M1–M6)** — working: injiziert + History-Zeile, kein Queue-Echo; idle:
   Text wie getippt; `!` allein: nichts passiert; `!/cmd`: Body + Rest. View-Wiring ist
   compile-grün, aber nicht automatisiert getestet (`AIChatView.resolveOutgoingMessage`).
7. ❓ **Smoke Compact-Lock UC-CT-3/5/6** — Queue im UI, Fehlerfall, Roster „working".
8. ⏳ Reserved-Name `codegraph` (ADR-0068) · ⏳ Icon-Visuals Q2=(a) · ⏳ S3 (🗜 in
   PoDelegateTool/AgentsTool) blockiert auf Pauls WIP-Merge.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio 2026-10-18: wichtiger als async Agents.** Danach
  **async Agents** (Top-3 in [async-agents.md](async-agents.md), Mutable-`request`-Merge-Check-Item
  vor dem Bau).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- **Compact-Lock ✅ (Docs waren stale):** war seit 2026-09-22 gebaut und gemerged; 2026-10-18 am
  IST verifiziert (R-CT-1…4 + UC-CT-1/2/4 automatisiert, Core 1190/0). **Offen: Paul-Smoke
  UC-CT-3/5/6** (Queue im UI, Fehlerfall, Roster).
- Bug-Backlog: Fehlerklassen-Tabelle R-CC-7/ApiRetry (❓), Header-State-Leak (❓, IST-Messung
  offen), Context-Overflow-Zyklus (❓ Auto-Compact, Pollution, Memory-Snapshot), McpService
  `connect()`-Rollback-Test (offen seit ADR-0069).

## Notizen

- **Riesige Tool-Args killen den Call:** Plan- und Dev-Agent liefen je einmal in Provider-400
  „175155 > 175104 tokens". Gegenmittel: `clear*` vor dem Versuch, kurze Prompts, Agent schreibt
  lange Pläne/Berichte als Datei.
- **Windows-Falle im Renderer:** In mit `System.lineSeparator()` gebauten Strings **immer**
  `indexOf('\n')` (Zeichen) suchen, nie `indexOf(System.lineSeparator())` — sonst landet das `\r` im
  Index und `+1` schneidet mitten im Newline.
- `eclipseEditFile` verschluckt gelegentlich Parameter („oldString is required") — Fallback
  `eclipseReplaceLines`/`diskEditFile`, danach Read-back (AGENTS.md).
- Ledger `.agents/skills/wiki/skill-impact.md` gehört mit dem Zyklus committed.

# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## Stand 2026-10-18 (Branch `story/night-5-2026-10-16`)

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
6. ⏳ Reserved-Name `codegraph` (ADR-0068) · ⏳ Icon-Visuals Q2=(a) · ⏳ S3 (🗜 in
   PoDelegateTool/AgentsTool) blockiert auf Pauls WIP-Merge.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio 2026-10-18: wichtiger als async Agents.** Danach
  **async Agents** (Top-3 in [async-agents.md](async-agents.md), Mutable-`request`-Merge-Check-Item
  vor dem Bau).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- **Compact-Lock** ❌ specified (baubar, Prio offen).
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

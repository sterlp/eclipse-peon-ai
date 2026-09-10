# Session-Stand (2026-09-10, Zyklus Warning-Cleanup abgeschlossen; lib-update wartet auf Merge)

## Aktiver Zweig: `story/lib-update-2026-09-09` — zwei Zyklen drauf, Merge = User

**Zyklus 1 — Dependency-Update (✅ abgeschlossen, reviewed):** Target 2026-09 +
jakarta.annotation [3.0.0,4.0.0) + langchain4j 1.20.0/beta30 (McpService→Streamable-HTTP,
Legacy-2024-11-05-Server fallen weg) + Lib-Bumps. Commits `fe761f8`…`8ae9c5f` (6).
Gates: Core Surefire 699/0 · OSGi 194/0. SOLL: [ADR-0044](adr/0044-target-2026-09-dependency-update.md).

**Zyklus 2 — Warning-Cleanup (✅ 2026-09-10, Commits `51f43d2`/`a9124f1`/`56e9cc5` + inc-4 `7f7b9b3`):**
64 → 12 Probleme (0 discouraged access, 0 POM-Warnings, toter Code/Deprecated raus,
`resolveModel`+3 Tests entfernt — 0 produktive Aufrufer). 12 akzeptierte Rest-Ausnahmen
dokumentiert in `AGENTS-DEV.md` („Known-benign warnings"). M2-„slot-drift" war **kein Drift**
(ADR-0036 nutzt „Slot" aktuell) → Rename gestoppt; daraus ❓ Glossar-„Slot"-Doppelbelegung
(open-points). Core Surefire 696/0 (−3 entfernte Tests), OSGi 194/0 (4 skipped = normal).
inc-4 (`7f7b9b3`): IoUtils-ensureFolders-TODO + 7 Einzeiler (6 unused imports + 1 Suppression),
Docs committet, Review-Record. Branch jetzt **10 Commits**.
**Bug-Hunt-Backlog neu (echte Null-Flow-Signale, brauchen eigenen Review, NICHT Cleanup):**
`ToolService.java:181` (@NonNull ChatResponse mismatch) · `:201` (`getAgent()` null) ·
`SkillPromptFile.java:64/86/88` (@Nullable-Flow) · `CompactSessionTool.java:32`. +
Follow-up: `SimplePromptFile.readFullContent():52` (public, cross-project Usage-Check vor Removal).

## User-Handlungen offen

1. **Smoke-Test** nach Eclipse-Neuinstallation (läuft gerade / geplant).
2. **Merge/Squash** `story/lib-update-2026-09-09` → main (9 Commits: lib-update 6 + cleanup 3).
3. **Homepage-Release-Notes** (User prüft): „Target Platform 2026-09, langchain4j 1.20.0, MCP
   über Streamable HTTP (Legacy-HTTP/SSE-Server fallen weg), jakarta.annotation 3.0, Lib-Updates" +
   Story/133-Skills-Zeile, falls unveröffentlicht.

## Blocker / Ausfälle

- ~~Dev-Endpoint instabil~~ → zurück (2026-09-10, User). Mein Compact lief in 500
  „Failed to parse tool call arguments as JSON" (col 3749, missing quote) — Sniffa-Recherche war
  trotzdem vollständig im Fehlertext enthalten und wurde gezogen; erneute Recherche nicht nötig.
  Weitere ApiRetry-Evidence (open-points.md).

## Bug-Triage lib-update (2026-09-10, Smoke-Test läuft)

**Bug 2 — MCP duckduckgo `-32022`: Ursache zweistufig, beide im Code verifiziert:**
1. **MCP-Config-Änderungen werden nicht live angewandt:** `AIChatView.applyConfig():376` bricht bei
   unverändertem `LlmConfig` früh ab — `applyMcpConfig():382` läuft dann nie → neue Server-Liste/
   protocolVersion wirkt erst nach Restart oder manuellem MCP-Toggle (Statuszeile).
2. **Default `2025-06-18` löst den Detect-Tanz aus:** langchain4j 1.20 kennt nur `""`=Auto-Detect,
   `2026-07-28`=modern, `2025-11-25`/`2024-11-05`=force-legacy; **jede andere** Version →
   autoDetect(version) mit modern-Probe zuerst (DefaultMcpClient.java:273–275). duckduckgo
   (Python mcp SDK 2.x) ist dual-era: erster Request entscheidet Ära → Probe-Envelope lockt MODERN,
   Probe schlägt fehl (server/discover fehlt), Legacy-Fallback auf derselben Verbindung → -32022.
   Nicht „Client zu neu". `2024-05-11` (User-Test) = ebenfalls „andere" → gleiche Wall, erwartet.
   `2024-11-05` (User-Test) = force-legacy, sollte funktionieren — Wirkung kam nie an (Punkt 1),
   User-Bestätigung ausstehend.
- **R-MCP2 entschieden (User, 2026-09-10):** Default **leer = Auto-Detect** (langchain4j-Verhalten),
  kein stiller Default mehr; Dialog = editierbare Combo (Auto/2025-11-25/2026-07-28 + Freitext).
  SOLL in [mcp.md](mcp.md) + [ADR-0045](adr/0045-mcp-protocol-version-auto-detect.md) — ❌ specified
  (R-MCP1 live-apply, R-MCP2 Semantik, R-MCP3 UI). ⚠️ gespeicherte `2025-06-18` werden nicht
  migriert (Clean Break) — User-Feld ggf. manuell leeren.
- Build-Freigabe + Branch-Entscheid ausstehend; danach Bug 1 (Model-List-URL).

**Bug 1 — Model-List-URL-Lockdown: noch nicht untersucht** (Verdacht: Connection-Cache-Identity
nach ADR-0034 nimmt korrigierte URL nicht als neue Identität → kein Refresh).

## Danach (Reihenfolge offen)

1. Bug-Fix-Zyklus: Memory-Leak-Hunt (frischer Context!) · Triage #5–#15 + ApiRetry (mit neuer
   Evidence) + AgentOrder-Edge + Fixture-Bug `PeonAiServiceTest.java:1444/1501` (löscht getracktes
   `test_project/.agents/skills/test/SKILL.md` nach Suite-Lauf, Da-Dok-Fund 2026-09-10).
2. Loop-Bewährung → Built-in-Prompts (User-Schritt, später).
3. ❓ Glossar eager · ❓ buildWithDev-Compact · ❓ Glossar „Slot" doppelt · ⏳ Jackson 2→3
   (open-points.md — beobachten, Migration erst wenn Jackson 2 komplett entfernbar, User 2026-09-10).

## Referenz (abgeschlossen)

- Story/133 gemerged (Squash `e55668b`). Release-Notes: Skills `.agents/skills` + CRUD-Loop +
  Usefulness-Footer; Scaffold-Write refresht Skills; Jon liest Skills.
- Geparkt: Query-Caches, Streaming-Präzisierungen, Edit-Tools, copy-tools-e2e-`diskRenameResource`,
  Dropdown-Umbau (Klassen gelöscht, Git-Historie).
- 🔒 IDE-Target-rot → behoben durch Eclipse-Neuinstallation (resolved-points.md).

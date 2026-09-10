# Session-Stand (2026-09-10, Zyklus 3 mcp-fixes ✅ reviewed; R-ML1 ✅ gebaut+reviewed — beides wartet auf User-Smoke-Test + Merge)

## Aktiver Zweig: `story/lib-update-2026-09-09` — drei Zyklen drauf, Merge = User

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
Docs committet, Review-Record.

**Zyklus 3 — MCP-Fixes (✅ 2026-09-10, Commits `cec9ebf`/`fb86226`/`eb12072`):** Smoke-Test-Befund
duckduckgo `-32022` — R-MCP1 live-apply (Compare in `McpConnectionService`, vor dem LlmConfig-Gate),
R-MCP2 leer=Auto-Detect (Clean Break, `DEFAULT_PROTOCOL_VERSION` weg), R-MCP3 editierbare Combo +
Homepage. Review CONCERNS ohne Rework; Delta `eb12072` (C1 Toggle-Test R-MCP1c als Mutations-Nachweis,
C2 Homepage-„Description"-Drift raus, C3 isEnabled-Dedup). Gates: Core Surefire 4/4, OSGi 197/0/0.
Branch jetzt **13 Commits**. SOLL: [mcp.md](mcp.md) + [ADR-0045](adr/0045-mcp-protocol-version-auto-detect.md). Branch jetzt
**15 Commits** (inkl. Docs-Flip `07a97b1` + Plan-Archiv `b0f4c22` → `peon-plan/overview-done-2026-09-10-16-30.md`).
**Bug-Hunt-Backlog neu (echte Null-Flow-Signale, brauchen eigenen Review, NICHT Cleanup):**
`ToolService.java:181` (@NonNull ChatResponse mismatch) · `:201` (`getAgent()` null) ·
`SkillPromptFile.java:64/86/88` (@Nullable-Flow) · `CompactSessionTool.java:32`. +
Follow-up: `SimplePromptFile.readFullContent():52` (public, cross-project Usage-Check vor Removal).

## User-Handlungen offen

1. **Smoke-Tests nach Eclipse-Neuinstallation:** (a) duckduckgo-MCP mit **leerem Protocol-Version-Feld**
   (Auto-Detect) oder `2025-11-25` neu verbinden (Feld leeren — gespeicherte `2025-06-18` werden
   nicht migriert); Config-Änderung wirkt jetzt ohne Restart (R-MCP1). (b) R-ML1a: Base-URL eines
   Base-ererbenden Agenten ändern → Refresh-Button/Dropdown-Open holt Liste über die neue URL,
   ohne Advanced-Page neu zu öffnen.
2. **Merge/Squash** `story/lib-update-2026-09-09` → main (lib-update 6 + cleanup 4 + mcp-fixes 5
   inkl. Docs-Flip + R-ML1).
3. **Homepage-Release-Notes** (User prüft): „Target Platform 2026-09, langchain4j 1.20.0, MCP
   über Streamable HTTP (Legacy-HTTP/SSE-Server fallen weg), jakarta.annotation 3.0, Lib-Updates;
   MCP-Fix: leer = Auto-Detect + Config-Änderungen greifen sofort" + Story/133-Skills-Zeile,
   falls unveröffentlicht.

## Blocker / Ausfälle

- ~~Dev-Endpoint instabil~~ → zurück (2026-09-10, User). Mein Compact lief in 500
  „Failed to parse tool call arguments as JSON" (col 3749, missing quote) — Sniffa-Recherche war
  trotzdem vollständig im Fehlertext enthalten und wurde gezogen; erneute Recherche nicht nötig.
  Weitere ApiRetry-Evidence (open-points.md).

## Bug-Triage lib-update (2026-09-10, Smoke-Test lief)

**Bug 2 — MCP duckduckgo `-32022`: gelöst ✅** — Ursache (Gate + Default `2025-06-18` → Detect-Tanz
→ Era-Lock) und Fix vollständig in [mcp.md](mcp.md) + [ADR-0045](adr/0045-mcp-protocol-version-auto-detect.md).
- **R-MCP2 entschieden (User, 2026-09-10):** Default **leer = Auto-Detect** (langchain4j-Verhalten),
  kein stiller Default mehr; Dialog = editierbare Combo (Auto/2025-11-25/2026-07-28 + Freitext).
  **✅ gebaut + reviewed:** [mcp.md](mcp.md) + [ADR-0045](adr/0045-mcp-protocol-version-auto-detect.md)
  — inc-1 `cec9ebf` (R-MCP2a core + R-MCP1 live-apply) · inc-2 `fb86226` (R-MCP3 Combo + Homepage) ·
  Delta `eb12072` (Da-Dok-Findings C1 Toggle-Test/R-MCP1c, C2 Homepage-Drift raus, C3 isEnabled-Dedup).
  Review CONCERNS ohne Rework; Mutations-Nachweis R-MCP1c. Gates: Core Surefire 4/4, OSGi 197/0/0.
  ⚠️ gespeicherte `2025-06-18` werden nicht migriert (Clean Break) — User-Feld leeren (Auto-Detect)
  oder `2025-11-25` setzen.

**Bug 1 — Model-List-URL-Lockdown: ✅ gebaut + reviewed (2026-09-10, commit `fcb5339`):**
Stale-Base-Snapshot in der Advanced-Page — `AiAdvancedPreferenceView:51` snapshotet `LlmConfig`
einmalig, `AgentModelConfigSection.base` final (`:49`), `prepareFetch():99` baut die Identity aus
dem Stale-Base → Base-URL-Korrektur wirkt erst nach Page-Reopen. Basic-Page korrekt (live-Supplier),
eigene Agent-URL live (`getRecord()`) — nur Base-ererbende Agenten betroffen. `ModelListCache`
identity-korrekt. SOLL/IST: **R-ML1** ✅ in [model-loading.md](model-loading.md) — Fix: live-Supplier
statt final-Snapshot (gleicher Mechanismus wie Basic-Page), Identity zur Fetch-Zeit; Think-Form/
Extra-Body bewusst Konstruktions-Zeit; kein neuer Test (reines Wiring, SWT-Präzedenz R-UI1/R-MCP3).
Review **ACCEPTED** (N1 kosmetisch). Verifikation manuell (User-Smoke-Test R-ML1a).
Commits: `fcb5339` (Build) · `d8fb400` (Docs-Flip + skill-impact-Ledger) · `dfe1ff6`
(Plan-Archiv → `peon-plan/overview-done-2026-09-10-17-18.md`). **Branch = 18 Commits.**

**Neuer Bug-Kandidat (2026-09-10, User, PRE-EXISTING — nicht aus den Fixes): Stop-Button tot
während laufendem Run („Stop-Fenster").** Analyse VOR Fix (User-Anforderung). Symptom präzisiert:
Agent läuft (grüner Ball korrekt), Eingabefeld normal nutzbar (Senden queued Messages ✅),
aber Stop nicht drückbar; kein Live-Status/Tokens (Teil des Retry-Fensters, by design); danach
läuft es weiter, Stop wieder aktiv; nach Eclipse-Neustart alles normal. Tritt nach
Verbindungsabbruch+Retry auf. **Analyse (Da Sniffa, 2026-09-10):** Stop-State ist Job-getrieben
(`lockWhileWorking(true)` in `submitAiJob`, `false` in `handleDoneChatResponse`-finally, Stop
cancelt via shared `monitorRef`). Szenario 3 (Run wirklich tot) eliminiert (grüner Ball +
queued Messages). **Lead: Clobber-Race** — alter Job-`finally` (via `runInUiThread` async,
ggf. verzögert durch langen Render) setzt `monitorRef` auf Null-Monitor + `lock(false)`,
während der neue Run lebt → Stop tot UND wirkungslos. Szenario 2 (Frage-Widget versteckt
Eingabeblock) unwahrscheinlich (Eingabe war nutzbar). langchain4j 1.20 unwahrscheinlich
(Error-Pfad byte-identisch) — Trigger = flatternder Dev-Endpoint. Logs: User →
`/Users/sterlp/eclipse-workspace/.metadata`. Kein Fix bis Diagnose bestätigt.

**Bug-Kandidat (2026-09-10, PO, in Bug-Hunt-Backlog):** `eclipseGrepFiles` mit Pfad `/docs`
(Projekt-Unterordner, ohne Extension-Filter) meldet „no matches", obwohl Treffer existieren
(„Retry"/„ApiRetry" in open-points.md + memory.md, per Read bestätigt). Verdacht: Pfad-Scoping auf
Nicht-Projekt-Unterordner sucht still nichts = **False-Negative** (AGENTS.md: teuerster Tool-Bug).
Workaround bis zum Fix: Projektpfad + Extension-Filter nutzen.

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

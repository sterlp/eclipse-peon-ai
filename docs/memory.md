# Session-Stand (2026-09-11, Zyklus 6 compact-einmal ✅ gebaut + Branch-Konsolidierung · Merge auf main = User)

**Aktiver Zweig: `story/lib-update-2026-09-09`** — alle Zyklen drauf (31 Commits, Merge = User):

- **Zyklus 1 lib-update** (`fe761f8`…`8ae9c5f`): Target 2026-09, langchain4j 1.20.0/beta30
  (MCP → Streamable-HTTP, Legacy-Server fallen weg), jakarta.annotation 3.0 — [ADR-0044](adr/0044-target-2026-09-dependency-update.md).
- **Zyklus 2 warning-cleanup** (`51f43d2`…`7f7b9b3`): 64→12 Probleme, Rest-Ausnahmen in
  AGENTS-DEV („Known-benign warnings").
- **Zyklus 3 mcp-fixes** (`cec9ebf`…`eb12072`): R-MCP1 live-apply, R-MCP2 leer=Auto-Detect
  ([mcp.md](mcp.md) + [ADR-0045](adr/0045-mcp-protocol-version-auto-detect.md)), R-MCP3 Combo.
- **R-ML1** (`fcb5339`): Fetch-Identity zur Fetch-Zeit, live-Supplier statt Stale-Snapshot
  ([model-loading.md](model-loading.md)).
- **Zyklus 4 stop-fenster** (`ff69a3d`…`9c03f17`): R-ST1 In-Flight-Counter, R-ST2, R-ST3 —
  [chat-job-lifecycle.md](chat-job-lifecycle.md).
- **Zyklus 5 CI** (`29b1c06`…`5550d96`): `xvfb-run` in maven.yml ([pipeline.md](pipeline.md)).
- **Zyklus 6 compact-einmal** (`1f2d0b0`+`ce3483d`): Compact-Result genau einmal — Marker
  `(nothing preserved)`, Count-Test über ALLE Message-Typen, Button-Re-Render autoritativ
  (Clear nur bei Erfolg) — [context-message-concept.md](context-message-concept.md).

**Branch-Konsolidierung (2026-09-11, User-Anordnung „alles auf einen Branch, Rest löschen"):**
`release-2026-09-06` (3 Fixes: `a1d8d35` Dropdown-Deletion · `27c09ad` UTF-8-Write-Guard ·
`11f34f6` PDE-Skip-Count — bisher weder in main noch im Story-Branch) wird auf den Story-Branch
gebracht; danach alte lokale Branches löschen (nur gemergte/patch-äquivalente, Rest reporten).

## User-Handlungen offen

1. **Smoke-Tests auf Branch-Stand (Plugin-Restart nötig):** (a) MCP: leeres Protocol-Version-Feld
   (Auto-Detect) oder `2025-11-25`, Config-Änderung ohne Restart; (b) R-ML1a: Base-URL ändern →
   Refresh holt Liste über neue URL; (c) R-ST-Smokes 1–7 (Happy Turn · Stale-finally ·
   Phantom-Job · Compress+sofort-Send · Abort/Idle-Stop · INFO-Zeilen); (d) Compact-Button:
   **Summary 1×, Resume-Zeile 1×, Stop-Button danach idle?**
2. **Merge/Squash** `story/lib-update-2026-09-09` → main — User-Entscheidung.
3. **Homepage-Release-Notes** (User prüft): „Target Platform 2026-09, langchain4j 1.20.0, MCP
   über Streamable HTTP (Legacy-HTTP/SSE-Server fallen weg), jakarta.annotation 3.0, Lib-Updates;
   MCP-Fix: leer = Auto-Detect + Config-Änderungen greifen sofort" + Story/133-Skills-Zeile,
   falls unveröffentlicht.

## Offener Verdacht (wartet auf User-Smoke auf Branch-Stand)

**Stop-Button bleibt nach Compact aktiv** — Kandidat: `onCommitUi` (Clear+Re-Render) läuft VOR
`lockWhileWorking(false)` (AIChatView.java:615→617); wirft es (Browser-Hang!), bleibt der Unlock
aus. Nur der Compact-Pfad (Chat-Turn hat `onCommitUi = null`, :596). Reproduziert es sich auf
Branch-Stand → Fix: try/catch um `onCommitUi` + garantiertes Unlock (fail-open, R-ST1-Linie 613
„UI must never stay stuck"). SOLL dann in [chat-job-lifecycle.md](chat-job-lifecycle.md).

## Danach (Reihenfolge offen)

1. Bug-Fix-Zyklus: Memory-Leak-Hunt (frischer Context!) · Triage #5–#15 + ApiRetry (mit neuer
   Evidence) + AgentOrder-Edge + Fixture-Bug `PeonAiServiceTest.java:1444/1501` ·
   Compact-Komplex verifiziert ([open-to-discuss.md](open-to-discuss.md)): 1 compactSession =
   1 Compressor-Call; Kandidaten: COMPACT_HINT-Dedup · ApiRetry non-retryable · stille
   Cancellation (`AIChatView.java:624`) · Browser-Hang Chat-View. **Doppel-Ablage gefixt
   (`1f2d0b0`/`ce3483d`)** — Compact-Tool-Result = preserve only + Count==1.
2. Loop-Bewährung → Built-in-Prompts (User-Schritt, später).
3. ❓ Glossar eager · ❓ buildWithDev-Compact · ❓ Glossar „Slot" doppelt · ⏳ Jackson 2→3
   (open-points.md — beobachten, Migration erst wenn Jackson 2 komplett entfernbar, User 2026-09-10).

## Referenz

- Story/133 gemerged (Squash `e55668b`). Release-Notes: Skills `.agents/skills` + CRUD-Loop +
  Usefulness-Footer; Scaffold-Write refresht Skills; Jon liest Skills.
- Bug-Triage lib-update: MCP duckduckgo `-32022` gelöst · Model-List-URL-Lockdown gelöst
  (R-ML1) · Stop-Fenster gelöst ([chat-job-lifecycle.md](chat-job-lifecycle.md)) ·
  Bug-Kandidat `eclipseGrepFiles` mit Pfad `/docs` False-Negative (Workaround: Projekt+Extension).
- compactSession bricht bei zu langem State (>150k) ohne klare Fehlermeldung ab — Backlog
  (open-to-discuss „Status-Display nach compactSession").
- Geparkt: Query-Caches, Streaming-Präzisierungen, Edit-Tools, copy-tools-e2e-`diskRenameResource`,
  Dropdown-Umbau (Klassen gelöscht, Git-Historie).
- 🔒 IDE-Target-rot → behoben durch Eclipse-Neuinstallation (resolved-points.md).
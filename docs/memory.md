# Session-Stand (2026-09-11, Nachmittag — alles auf main gemerged, Zyklen abgeschlossen)

**Aktiver Zweig: main** — User hat `story/lib-update-2026-09-09` (34 Commits) gemergt und das
Eclipse-Update gezogen. Branch-Konsolidierung abgeschlossen: nur noch main (+ Story-Branch
historisch). **Alle Smoke-Test-Befunde aus den letzten Zyklen zählen jetzt auf main-Stand.**

## Abgeschlossene Zyklen (Referenz)

- **lib-update** · warning-cleanup · mcp-fixes (R-MCP1–3, ADR-0045) · R-ML1 · stop-fenster
  (R-ST1–3, chat-job-lifecycle.md) · CI xvfb (pipeline.md) — alle ✅.
- **Compact-Zyklus** (`1f2d0b0`/`ce3483d`/`a89cdc6`, context-message-concept.md): Compact-Result
  genau einmal (Marker `(nothing preserved)`, Count-Test über ALLE Message-Typen, Button-Re-Render
  autoritativ) + **R-ST4** System-Message-Rebuild nach In-Loop-Compact (executeLoop Compact-Zweig,
  non-default `AiAgent.buildStaticMessages`, Rot-Test `test_inLoopCompact_systemMessageIsRebuilt`).
- **Compressor-Prompt-Test** (`7105e3b`, main): `test_sendsSystemPromptToLlm` — Lücke geschlossen
  (kein Test prüfte, dass der Request die COMPRESS_SYSTEM-Message trägt); 704/0.

## Branch-Konsolidierung (2026-09-11, abgeschlossen)

Meine „3 Fixes fehlen"-Diagnose war falsch — Content kam via Squash `45f2a0d2` („Release 2026 09
06 #132"). 22 Branches gelöscht (13 nachweislich gemergt + 9 per User-Anordnung). Tips stehen
hier bis zum nächsten Aufräumen — **jetzt überholt, Branches existieren nicht mehr.**

## User-Handlungen offen

1. **Smoke-Tests auf main-Stand** (alle Zyklen): (a) MCP leeres Protocol-Feld / Config ohne
   Restart; (b) R-ML1a Base-URL-Refresh; (c) R-ST-Smokes 1–7; (d) Compact-Button: Summary 1×,
   Resume-Zeile 1×, Stop-Button danach idle; (e) In-Loop-Compact („rufe das compact tool auf"):
   nach dem Compact-Result frischer System-Prompt im Rest-Turn (R-ST4 — neue `Loading 📋`-Zeilen
   mid-Turn sind erwartbar).
2. **Homepage-Release-Notes** prüfen (User).

## Offener Verdacht (wartet auf User-Smoke auf main)

**Stop-Button bleibt nach Compact aktiv** — Kandidat: `onCommitUi` (Clear+Re-Render) läuft VOR
`lockWhileWorking(false)` (AIChatView.java:615→617); wirft es (Browser-Hang!), bleibt der Unlock
aus. Nur der Compact-Pfad (Chat-Turn hat `onCommitUi = null`, :596). Reproduziert es sich auf
main-Stand (R-ST1 ist drin!) → Fix: try/catch um `onCommitUi` + garantiertes Unlock (fail-open,
R-ST1-Linie 613 „UI must never stay stuck"). SOLL dann in chat-job-lifecycle.md.
Alt-Stand-Beobachtung (2026-09-11): Stop → llama.cpp stop/processing/cancel, UI läuft nicht
weiter; Re-Trigger hilft. Gilt für Alt-Stand — auf main neu testen.

## Danach (Reihenfolge offen)

1. Bug-Fix-Zyklus: Memory-Leak-Hunt (frischer Context!) · Triage #5–#15 · **ApiRetry** (Da Mek
   2026-09-11 daran gestorben: „AI call canceled while waiting to retry" — 3. Evidence,
   non-retryable-Klassifikation priorisieren) + AgentOrder-Edge + Fixture-Bug
   `PeonAiServiceTest.java:1444/1501` · COMPACT_HINT-Dedup (Hint bleibt nach Compact in Memory,
   §9.1 issues/overview-fixed-compact-issue.md) · stille Cancellation (`AIChatView.java:624`) ·
   Browser-Hang Chat-View · compactSession >150k ohne klare Fehlermeldung (open-to-discuss).
2. Loop-Bewährung → Built-in-Prompts (User-Schritt, später).
3. ❓ Glossar eager · ❓ buildWithDev-Compact · ❓ Glossar „Slot" doppelt · ⏳ Jackson 2→3
   (open-points.md — beobachten).

## Referenz

- Bug-Triage: MCP `-32022` gelöst · Model-List-URL-Lockdown gelöst · Stop-Fenster gelöst ·
  Bug-Kandidat `eclipseGrepFiles` Pfad `/docs` False-Negative (Workaround: Projekt+Extension).
- Story/133 gemerged (Squash `e55668b`): Skills `.agents/skills` + CRUD-Loop + Usefulness-Footer.
- Geparkt: Query-Caches, Streaming-Präzisierungen, Edit-Tools, copy-tools-e2e-`diskRenameResource`.
- 🔒 IDE-Target-rot → behoben durch Eclipse-Neuinstallation (resolved-points.md).
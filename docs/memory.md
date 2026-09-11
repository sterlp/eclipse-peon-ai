# Session-Stand (2026-09-11, Nachmittag — main pushed, ein Branch, repo clean)

## 🔴 AKTIVER AUTONOMER ZYKLUS (2026-09-11 Nachmittag — **User ist zurück, interaktiv**; Cleanup läuft weiter)

**Compressor-Bug gefunden & gefixt (User-Anfrage „Compact agent kaputt?"):** `AiCompressorAgent.java:40`
invertierte Dedup-Bedingung (`>= 0` statt `< 0`) → Compact-UserMessage ging IMMER leer an die LLM
(Commit `d1c2191d`, „user change" #136). Beweis: roter Test `test_sendsSystemPromptToLlm` (Blank-Input,
erweitert: 3 Messages + Duplikat 1× + Chat-Monitor), Fix 1 Zeile. **Branch `fix/compressor-empty-compact-input`
(Commit `8d7cc2a` FROM main 57c7ac0, gepusht) — MERGE = User.** Danach: Cleanup-Zyklus weiter
(planWithPlanAgent-Versuch #1 starb mit „AI call canceled" — neu anstoßen).
⚠️ docs/memory.md war uncommitted und ist mit auf den Fix-Branch gewandert (uncommitted, kehrt zurück).

**Mission (User-Wortlaut):** Bug sweep + Architecture Review im **core**. Fragestellung: was aus
dem Plugin gehört in den core, ist die Architektur sauber, ist alles sauber abstrahiert?
**Nordstern-Szenario:** Jon mit den Disk-Tools als Web-App aus Eclipse portieren — alle
Eclipse-Tools fallen weg. Könnten wir das? Was im Plugin müsste portiert werden?
**Regeln:** KEINE neuen Features — nur aufräumen, Bugs fixen, Architektur verbessern, Code
einfacher machen. Bugs brauchen roten Test als Beweis; UI-Bugs ohne Test-Fokus → Fix erlaubt,
User-Smoke nachträglich. Arbeiten auf eigenem Branch (alles drauf, Name frei wählbar —
**`core-cleanup-2026-09-11`**). Keine App bauen — nur Aufräumen.
**Da-Mek-Regel angepasst:** User weg → bei SOLL-Lücken NICHT askUser (blockiert) — SKIP die
Story, als ❓ in open-points.md, weiter mit der nächsten. Technische Entscheidungen → ADR,
ableitbare Annahmen → ⏳ in open-points.md.
**Bug-Sweep-Triage-Liste (core, `299c26b` = 5 rote Tests committed auf `core-cleanup-2026-09-11`):**
1. ✅**FIX** `StreamingBridge.onError:196` blindes `errorRef.set(error)` überschreibt CancellationException nach Cancel → ApiRetry retryt gestoppte Calls (**Root-Cause-Kandidat** für „canceled while waiting to retry", memory #21!). Fix: `errorRef.compareAndSet(null, error)` — Cancel gewinnt.
2. ✅**FIX** `SkillPromptFile.readRelativeFile:86-92` skill-qualifizierter Pfad = Dead Code (Guard läuft nach Resolve → immer „Path traversal") — False Negative. Fix: startsWith-Check gegen `skillDir` korrekt/gestrichen, Skalierung `skillDir.getParent()` prüfen.
3. ✅**FIX** `ToolService.addCompactHintIfNeeded:205` NPE bei `agent==null` (legal @Nullable) — Guard wie Zeile 163.
4. ✅**FIX** `SkillPromptFile:88` NPE `skillDir.getParent()==null` bei parentlosem relativem Dir → „File not found"-IAE statt NPE.
5. ✅**FIX** `AgentOrder.sort:100-110` dupl. matchende Namen still verworfen (seen-Set nur bei peek). Fix: Log/Warnung + nicht still; Verhalten NICHT ändern (SOLL offen: dedup gewollt? → ⏳-Vermerk, Fix = nur Sichtbarkeit).
6. 🚫**SKIP** `PeonAiServiceTest:1444/1501` Over-Delete `<test_project>/.agents` komplett (real, aber OSGi-Lauf braucht Workspace-Trust → User). Fix erlaubt (Test-Fixture-only), Smoke/Neu-Lauf durch User. „Getrackte Datei"-Prämisse war stale (auf main nicht getrackt).
Verifiziert stale: ToolService:181 (schon gefixt, Test existiert). Gate nach Sweep: 710 Tests, 5 rot (die Beweise), 705 grün.

**Arch-Review-Verdicts (searchAgent, 2026-09-11) — Kategorien:**
- **A Moves (mechanisch, tun wir):** `SimpleDiff` · `WorkspaceGuideline` (Jackson-Record) · `ThinkValueSupport` · `AiAgentStatusModel` — alle 0 Eclipse-Imports, je 1 Import-Fix im Plugin.
- **B Moves (dokumentiert, NICHT in diesem Zyklus — mehrgliedrig):** `AskUserTool` → core (braucht Presenter-Interface) · `WorkspaceMemoryTool` → core mit Store-Interface (LlmConfigStore-Muster!).
- **C Dead Code:** `UiCommand`-Hierarchie (LiveStatus/Scroll/SetTheme etc.) tot — **BEHALTEN**: gehört zum WIP-MVP-Plan agenten-status-im-header (index.md:48-49). Nicht löschen, im ADR vermerken.
- **D ADR-Material (Web-App-Port):** Core import-rein (0 Eclipse-Imports ✓) · Kontrakte UI-agnostisch (AiMonitor/ToolLoopRequest/ConfiguredChatModel/LlmConfigStore ✓) · Lücken der Web-App: Build/Test-Runner/Java-Navigation/Console · **Composition-Root-Gap** (PeonAiService + AgentContextComponent + BuildPoAgentComponent Eclipse-typisiert; Plan nur IFile-basiert) · `StaticContextItem` verweist auf Plugin-Klasse + eclipse-Guidance hartkodiert · StringMatcher vendored (EPL, Header intakt — lassen) · QualifiedPathValidator sauber injiziert ✓ · `peon.test.project` existiert NICHT mehr.
**ADR schreiben:** docs/adr/0046-core-portability-review.md (Bestandsaufnahme + Moves + Gaps + Composition-Root-Gap).
**Wenn User zurück ist:** 1) Glossar-Thema lösen (User: „müssen wir lösen" — eager-Loading ❓ +
Slot-Doppeltbelegung ❓, beide in open-points.md) · 2) Bug-Sweep-Verdicts + Smoke-Tests vorlegen.

---

**Aktiver Zweig (vormittags): main** — `57c7ac0` lokal = origin/main. Repo-Konsolidierung
abgeschlossen: alle lokalen Branches gelöscht, nur noch main; Plan-Archive bewusst NICHT
gesichert — User: „Pläne sind transient, gehen wir nie drauf zurück".

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
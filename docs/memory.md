# Session-Stand — 2026-09-29 (Widget-Rework R-DEF-9…11 ✅ GEBAUT + REVIEWT — wartet auf Paul-Smoke)

> Achtung: docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts
> im Chat, Jon schreibt. (Da Mek hatte 2026-09-27 memory.md überschrieben — nicht wiederholen.)

## AKTUELL 2026-09-29: Widget-Rework R-DEF-9…11 — ✅ GEBAUT + DA-DOK-CONCERNS ABGEARBEITET (Paul-Smoke steht aus)

- **Story komplett auf `story/issue-149-think` (kein Push/Merge — Paul bleibt auf dem Branch):**
  Inc 1 `af27674b` (Widget live-Gate + Preserve, UC-DEF-11) · Inc 2 `c7d4e71d` (Basic „Default for
  all agents" + extraBody-Persistenz, UC-DEF-9) · Inc 3 `43c91fb2` (Advanced DEV raus + Base-Key-
  Writes raus, UC-DEF-10, Single-Owner-Beweis `performOkWritesOnlySlotKeys`) · Inc 4 `585d9f88`
  (Homepage) · inc-5 `6c275676` (Da-Dok-G1-Test `thinkClearedByListSwitchStaysClearedAfterNoneTransition`
  + 2 stale Kommentare) · Docs-Commit `4c34f6e9` (meine docs/** + ADR-0063 + Plan) · Plan archiviert
  (planImplemented).
- **Zahlen (Surefire Ground Truth):** core **1054/0/0/0** · PDE-Runner **313/0/0** · Tycho headless
  **313/0F/0E/32S** · Lint UC-DEF: 8/8 clean (nur MANUELL-Info UC-DEF-6, legitim R-DL-22).
- **Da-Dok-Review: CONCERNS → abgearbeitet.** Alle Befunde non-blocking: G1 (kein Test für die
  R-MCW-6-Präzedenz) → Test gebaut + Regel in UC-DEF-11 ergänzt („Clearing bleibt vor dem Preserve,
  geleerter Wert wird nicht auferweckt") · G2/G3/G4 → open-points.md (SwtUtil.setExcluded-Extract,
  Plan-Text-Präzisionen, D7-Guard-Deklaration) · G5/G6 clean. Kein Skill-Gap (Vorzyklus-Empfehlung
  bereits in `5beb28d7`).
- **Status-Flips von mir (Jon):** R-DEF-9/10/11 ✅ + BDD-Tabelle + index.md-Zeile. Historische
  UC-DEF-4/5/7 zu Fließtext degradiert (DocParser kennt kein ☠️-Status — `statusFromEmoji`
  nur ✅/❌/🚧, DocParser.java:209-216; Gap als ❓ in open-points.md; mein testGlobs-Muster
  `**/src/test/**` greift nicht bei OSGi-Test-Layout `src/` → `**/src/**/*.java` verwenden).
- **OLLAMA-Korrektur aus dem G1-Test:** OLLAMA ist Toggle (editable Combo), NICHT None — None =
  Gemini/Mistral (ProviderCapabilitiesTest :79-80). Doc-Texte sagen None-Provider, Beispiele
  konkret Gemini — SOLL unverändert.
- **Paul-Smoke (UC-DEF-9/10 Rendering):** Basic-Seite = Gruppe „Default for all agents" mit
  Extra body (JSON) + Temperature; Provider-Wechsel → Extra-Body/Think live; Advanced ohne
  DEV-Sektion; Speichern von Basic + Advanced nacheinander (Doppelt-Editor-Falle: Basic-Edits
  überleben); OLLAMA-Extra-Body versteckt → gespeicherter Wert bleibt. Danach Merge = Paul.
- **Da Mek-Context:** nach 88 %-Warnung kompaktiert (compactDev) — gleiche Story, Build fertig.

## Vorherige Zyklen (unverändert offen)

- **Default-Inheritance 2026-09-28 ✅ + Model Config Widget/Think 2026-09-28 ✅ + Issue #149 Think
  ✅** — Paul-Smoke für alle drei (vier jetzt) Zyklen steht GEMEINSAM aus; danach Push/Merge
  (Paul). Branch-Konsolidierung: alles auf `story/issue-149-think`.
- **Branch:** ~25 Commits ahead of origin, NICHT gepusht. Merge → main = Paul-Entscheidung.

## Neu 2026-09-29 (vor dem Rework — Referenz)

- **Paul-Smoke-Befunde, die das Rework motivierten:** (1) Extra-Body-Feld provider-gated nur bei
  Page-Bau + stille Löschung beim Advanced-OK mit geschlossenem Gate; (2) Doppelt-Editor-Falle
  (bewiesen): Dialog performt OK auf jede besuchte Seite, Advanced-Stale gewann; fehlender
  Provider-Key materialisierte OLLAMA-Default. → ADR-0063 (Single Owner).
- **Neue Bugs (open-points.md ❓):** Compact-Button ohne Monitor (UI-Feedback-Loch); Auto-Compact
  bei `exceed_context_size_error` (Fall: Mek 431005 vs. 170240 — Tool-Ergebnis-Verdacht, grep
  „MUTATION" 1 Match, wasn't the culprit per se — klären, was 400k+ trug).

## Offen / Nächste Schritte

1. **Paul:** gemeinsamer Smoke aller 4 Zyklen (Punkte oben + per-agent-think/model-loading-Einträge
   in index.md) → dann Push/Merge (Pauls Reihenfolge: alles auf EINEM Branch, Reihenfolge egal).
2. Autonom danach: TrimService-Story (⏳, nächster Architecture-&-Bug-Sprint) · R-CC-7 · Think-BDD-
   Lücken (a)–(g) · per-Agent-Provider-Override (❓) · Linter-Gap ☠️-Status (❓) · Backlog in
   open-points.md (SwtUtil.setExcluded, 4-Arg-Konstruktor Dead Code, Header-State-Leak, Bug-Fix-Backlog).
3. ApiRetry-Verdacht (memory 21) unverändert offen — 5× Evidence.

## Paul-Feedback 2026-09-27 (Config-UI)

- Widget-vs-Store-Stale-Reads in Preference-Pages: eclipse-dpe-Skill erweitert. R-ML2 = dokumentierte
  Entscheidung (Refresh liest Live-Widget-Werte), SOLL-Änderung nur auf Paul-Wunsch.

# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## NACHTZYKLUS 2026-10-23 — ABGESCHLOSSEN (Details kompakt)

- Alle 3 Stories gebaut + gereviewt + geflippt: **webSearch** (ADR-0074, eigener DDG-Client),
  **Compact-UI-Sichtbarkeit** (🟡 Roster, Caller-Monitor, Agent-Name, Deferred-Reset),
  **Skill-Roots** (3 Roots, Präzedenz Projekt > `~/.agents` > `~/.peon`).
  Gates: Core/PDE/Lint-Baseline grün (Zahlen je Commit-Message). Branch `story/night-2026-10-23`
  (von main), Close-Commit + Retro im Git-Log. Merge = Pauls Entscheidung
  (Squash bevorzugt — danach memory#53 beachten).
- **Offen für Paul aus dem Zyklus** (Details: [open-points.md](open-points.md)):
  1. ⏳ Snippet-Disclosure-Zeile webSearch · ⏳ Deferred-Timing-Rückversicherung ·
     ⏳ First-Launch-Dir (`~/.agents/skills` anlegen?) · manueller Smoke §8 (Roster-🟡,
     Chat-Rebuild, Sklaven-Pfad, FAILED_EMPTY, Pre-Turn pending/flush).
  2. Scaffold-Prompt-Diff (Inc 3, Nachtzyklus-Branch) — 4-Augen by Paul.
  3. FF-Merge `story/docs-restructure-2026-10-22` → main (falls noch offen);
     `--force-with-lease`-Push; Update-Site-Konflikt README L48 vs. memory-Tool #46.

## BUG-TRIAGE 2026-10-24 (Paul: "speichern, einen nach dem anderen anarbeiten")

Quelle: Paul-Chat + [org.sterl.llmpeon.test/ai-e2e-test/webtools-test-issues.md](../org.sterl.llmpeon.test/ai-e2e-test/webtools-test-issues.md)
(E2E-Run 2026-10-24: webSearch, webFetchAsMarkdown, webGet). Ablauf je Issue: IST-Verifikation
(rot wo möglich) → Triage → Fix. Docs erst bei bestätigtem Bug.

- **T-1 (Paul, chat):** Skill-Routes nach Reload-Command — Meldung „Neu geladen aus /Users/sterlp/.peon:
  5 Agents, 18 Skills, 6 Commands", aber vermutlich falsche/unvollständige Routes zurück.
  `change-agent-issue.txt` ist LEER (nur Leerzeile) — Details fehlen. → Erst IST klären
  (ReloadCommand/ChangeAgent), ggf. Paul nach Details fragen.
- **T-2 = I-1 ✅ GEBAUT (2026-10-24):** R-WEB-13/UC-WEB-13 — Content-Type-Gate in WebFetchTool
  (text/html & *+xml → Flexmark, sonst raw), Pfad-Label im Footer (`(converted from HTML)` /
  `(raw text)`), Cache-Value = Text+Label (`FetchedPage`), neuer @Tool-Text. 10 neue/angepasste
  Tests inkl. Null-Header/uppercase/UTF-8-Default-Mutation-Guards. Surefire grün (Commit-Message),
  Lint baseline (UC-WEB-13 belegt). Da-Dok CONCERNS → alle aufgelöst.
  Offen für Paul: ⏳ `+xml` bewusst BREIT (SVG/RSS werden konvertiert + „converted from HTML" gelabelt,
  Pauls Issue-Vorschlag) — Verengung auf text/html+xhtml wäre Doc-Änderung; ⏳ Paul-Smoke
  (JSON/plain raw, Webseite konvertiert, Cache-Label stabil).
- **T-3 = I-2 ✅ GEBAUT (2026-10-24):** R-WEB-14/UC-WEB-14 — Rate-Limit-Meldung nennt Handlungsoption
  (nicht sofort retryen, webFetchAsMarkdown-Alternative), KEIN Auto-Retry (evidenzbasiert: Sperre hielt
  Minuten), @Tool-Text warnt + rät zum Staffeln. Pattern-Erkennung (rate limit|bot check|HTTP 202/429,
  CASE_INSENSITIVE) + Null-Cause-Guard; 5 Tests, alle Zweige mutationsgepinnt, callCount==1 = Retry-Beweis.
  Surefire grün (Commit-Message). Offen für Paul: ⏳ Paul-Smoke gegen
  echtes DDG-Rate-Limit; ⏳ Engine-Seite „kein Retry" untestbar in diesem Inkrement (Request-Zähler im
  Engine-Test = Follow-up); Cleanup-Kandidat typisierte `WebSearchRateLimitException`.
- **T-4 = I-3 (niedrig):** HTML→Markdown läßt `<pre>`/Code-Blöcke zerfließen (Zahlen-Gutter,
  Leerzeilen je Code-Zeile) → Zeilennummern wertlos, Token-Ballast.
- **T-5 = I-4 ✅ GEBAUT (2026-10-24):** R-WN-4/R-WN-5 in workspace-nesting.md — Round-Trip-Garantie
  (Resolver-Last-Resort über Disk-Lokation, `resolved via`-Disclosure; Treiber S4/S3 aus Da-Mek-Messung
  S1–S5), Diagnose statt nacktem „not found" (closed/disk-only/unbekannt), `findOpenProject` exakt
  (Substring-`contains` raus). Supersedes Paul 2026-10-16 „ohne Projekt-Disclosure" (False-Negative-Klasse
  teurer — in eclipse-read-tools.md dokumentiert, in Summary melden). `src/`-Magic-Entfernung ⏳ (falsch-positiv,
  Inventar erst). PDE grün (Commit-Message). ⏳ T-1/T-1a brauchen Paul; T-4 offen (niedrig).
- **T-8 ✅ GEBAUT (2026-10-24):** PlanTool — R-PT-1 Archivierung toleriert disk-geschriebene Pläne
  (refreshLocal auf peon-plan vor exists/move, beide Fallen: stiller Return + ResourceException,
  Fehler nie still, Cancellation läuft ungefangen) + R-PT-2 planRead/hasPlan/planUpdate sehen
  disk-geschriebene Pläne (kein „No plan exists"-False-Negative, gemeinsamer Helfer refreshPlanDir,
  tote Guards raus). PlanToolTest 6 Tests, alle mutationsgepinnt (inkl. Move-Failure via
  read-only-Ordner). Neue Feature-Docs: org.sterl.llmpeon/docs/plan-tool.md (✅) + index.md-Zeile.
  PDE-Suite grün (Commit-Message). ⏳ AgentContextComponent.readPlan
  Injection-Klasse erst bei Befund (Doc-Offener-Punkt); Befund: Stop wirft nackte
  OperationCanceledException — bewusst ungefangen (Regel im Doc).
- **T-1a OFFEN (Paul):** Reload-Output per-Root-Skill-Zählung — mein Vorschlag gestellt
  („Skills: N effective — <root>: n, …", leere Roots entfallen), Ja/Nein steht aus.
- **T-6 (Paul-Smoke compact-agents-issue.md, Story-2-Abnahme fehlteilig):** Compressor-Streaming
  unsichtbar — schwarzer Balken „Waiting for AI…" ohne ankommende Tokens (UC-CT-8 „log-only").
  Paul: „Zumindest das Streaming der token sollte ich sehen — das hatten wir schon viel besser."
  → revidiert R-CT-5/R-CC-14-Streaming-Klausel (SOLL-Revision durch Owner im Smoke erklärt).
- **T-7 (dito):** Sub-Agent-Compact-Ergebnis fehlt im Caller-Chat — nach „🗜 Compressing
  Peon-Plan: 6 messages 969 tokens …" kommt nur „📋 Loading Agent Memory", nie done/success +
  Ergebnisgröße (messages/tokens). Compact des Agenten selbst: „Session compacted:"-Ergebnis
  sichtbar — das ist laut Paul gut so.
- **T-6 + T-7 ✅ GEBAUT (2026-10-24, Story-2-Follow-up R-CT-8):** Code+Tests+Homepage, Review-F1
  (Sequenz-Test End-Zeile-nach-onCompactCompleted + Cancellation-Test, 2 Mutationen bewiesen),
  Docs-Flips ✅ + planImplemented-Archiv (`peon-plan/overview-done-2026-10-24-10-21.md`).
  Surefire grün (Commit-Message). Docs: compact-lock R-CT-8 ✅
  (+ revidierte R-CT-5, UC-CT-8 entfallen), compact.md R-CC-14/16/18.1, compact-architektur Δ,
  compact-input-budget R-CIB-3, sub-agent-timing, tool-result-size R-RS-1, agenten-status-im-header,
  context-message-concept. **Neue Nebeneffekte (gewollt, dokumentiert):** Header zählt Compressor-
  Tokens mit; Stop bricht den laufenden Compressor-Call ab. Naming: End-Zeile nutzt `getName()`
  („Peon-Dev compacted", konsistent mit Start-Zeile) — Paul in Summary nennen. Offen: manueller
  Smoke Paul (Button aktiv/Sklave, compactSession im Loop). ❓ F3: AgentsTool.subAgentCompact hat
  keinen Test-File — Regression der gestrichenen reportAction fände keinen Wächter (zurückgestellt).
  **Neuer Befund:** `PlanTool.planImplemented` (:90) schlägt fehl, wenn der Plan per Disk-Tool
  geschrieben wurde (stale Workspace-Tree) — vor dem `move` fehlt refresh/`IResource.refreshLocal`
  → als T-8 triagiert.
- **T-10 ✅ GEBAUT (2026-10-24, close-build-issue.md):** R-OD-8/UC-OD-11 — onProblem-Zeile nennt Tool-Name,
  Uhrzeit (HH:mm, StringUtil.HH_MM), Exception-Klasse + Cause-Chain (Wrapper-Glieder, die nur die Cause
  wiederkäuen, entfallen; Stack-Frame-Zeilen aus mehrzeiligen Messages entfernt, Whitespace normalisiert,
  null/blank → `(no message)`); Stack bleibt in Log + Tool-Ergebnis (LLM). Helfer `StringUtil.toolFailureLine/
  exceptionChain`, CallStats nutzt StringUtil.HH_MM, `exceptionChain(null)`-Dead-Guard entfernt (F5).
  Surefire/Lint grün (Commit-Message), Da-Dok CONCERNS aufgelöst.
  Ursache des konkreten „closed" selbst: nicht rekonstruierbar — gleiche Klasse wie OP-STREAM-1/ApiRetry.
  Backlog-Nebenbefund: `HH:mm` dreifach im Core (StringUtil/LiveStatus/UserMessageQueue) — Folge-Inkrement.
- **T-9 ⏳ GEPARKT (Paul 2026-10-24):** Shutdown vs. laufende Jobs (job-issue.md) — Plattform cancelt Jobs beim
  Shutdown (~300 ms Toleranz), unser Poll (`StreamingBridge` 1500 ms, `ApiRetry`-Backoff 1-s-Chunks) bemerkt
  den Cancel zu spät → „Job found still running after platform shutdown". Kein Datenverlust. Ausgearbeitete
  Lösung (Core-Poll verkürzen + preShutdown/dispose-Cancel, geräuschlos) parkt Paul — Analyse steht in
  [chat-job-lifecycle.md](../org.sterl.llmpeon/docs/chat-job-lifecycle.md) „Offener Punkt". Kein Fix.
- **T-1b erledigt (kein Bug):** TeamException im change-agent-issue.txt = Egit-Provider-
  Instanziierung, von Eclipse Core geschluckt (getProvider fängt, loggt, markAsUnshared) —
  Agent-Wechsel ohne Schaden; Root-Cause steht im !ENTRY oberhalb des Stacks (Pauls Eclipse/EGit).
  Kein Fix in peon-ai.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Squash** Nachtzyklus-Branch + Smoke. Danach Branch löschen.
2. ❓ **P6 externes Review:** `docs/issue-163-tool-calls-retten.md` als Issue-Body nach GitHub #163, dann Datei löschen (Tripel-Duplikat auflösen).
3. ⏳ Delegations-Compact pro Delegation (Kosten, ADR-0071) · ⏳ Compact-Briefing-Schema
   (compressor.md) · ❓ Fixture-Duplikat · ❓ R-Regeln ohne Linter-Gate · ❓ In-Loop-Direkt-Compact
   fängt Exceptions nicht · ⏳ Reserved-Name `codegraph` · ⏳ R-CG-15 Monorepo-State-Refresh ·
   ⏳ R-CC-19-Semantik+Tilde · ⏳ Windows-`cmd /c`-Fallback · 🔒 S3 (🗜 in PoDelegateTool).
4. ❓ Testnamen-im-Fließtext-Slice · ❓ Linter-Guard gegen Evidenz-Drift ·
   vorbestehende R-CC-18-/R-DEF-Doppedefinitionen.

## EXTERNES REVIEW 2026-10-24 (Paul: „mit Vorsicht genießen, nachbessern bei echten Problemen")

Quelle: peon-plan/external-review-1.md (Plan-Tool + Docs-Konsistenz). Alle Punkte verifiziert (Da-Mek-IST + Da-Dok-Review, CONCERNS aufgelöst):

1. **P1 (hasPlan I/O/Werfen) → ✅ gebaut** (R-PT-2-Nachschärfung): Query-Pfade DEPTH_ZERO nur die Datei (`PlanFileView`, injizierbarer Refresh-Seam), `CoreException` → ehrlich `false`/„No plan exists" + onProblem, kein Throw. IST-Korrektur: `hasPlan` läuft nicht pro Turn (nur Agent-Wechsel + Tool) — Review-These zum Turn-Abbruch falsch, aber Halb-Aktivierung bei Fehler war real.
2. **P2 (R-PT-2 „nie" vs offener Punkt) → ✅ gebaut** (R-PT-3 + R-PT-4, verschärfter IST-Befund): ohne `setPlan` wird gar nicht gelesen (Lücke nicht konstruierbar); das ECHTE Risiko war der Ghost-Handoff — nach `planImplemented` blieb die Referenz leben → nächster Handoff löscht Dev-Memory + Erfolg ohne Plan-Inhalt. Fix: `onPlanArchived()` → `clearPlan()` + Härtung „leerer Plan-Text wie kein Plan" (Jon-Entscheidung) + Da-Dok-F2 (Guard prüft Rohtext, nicht readPlan-Ausgabe — leere overview.md deckt der Tail-Guard allein nicht). Per-Turn-`appendPlanReference` nutzt jetzt denselben refreshten Stand (Supplier-Wiring `planTool::visiblePlan`).
3. **P3 (planUpdate stale) → kein Fix nötig** (verifiziert): `IoUtils.writeFile` ist Workspace-Operation (IFile.setContents/create) — als Klarstellung in UC-PT-2 dokumentiert.
4. **P4 (Evidenz in docs) → ✅ aufgeräumt:** Surefire-Zahlen + Commit-Hashes aus memory.md/web-tools.md/tool-output-disclosure.md/plan-tool.md entfernt.
5. **P5 (ADR-0074) → ✅ umbenannt** + alle Links (Index, Architektur-Doc, 4 Javadocs) angeglichen.
6. **P6 (issue-163 im Root-docs) → OFFEN für Paul:** Inhalt soll als Issue-Body nach #163, Datei dann löschen; Tripel-Duplikat mit open-points/memory.md. Ich kann das GitHub-Issue nicht setzen.
7. **P7 (stale R-CT-8-Aussagen) → ✅ korrigiert:** Core-Index-Compact-Zeile + R-CC-16-Formulierung, sub-agent-timing-Satz.
8. **P8 (R-CT-8 ↔ R-CC-16) → ✅ Doc-Kante geschlossen:** R-CC-16 folgt als Erweiterung der EINE End-Zeile (kein drittes Format); Feld wird heute noch nirgends erfasst (❌ backlog).
9. **P9 (compact ohne Monitor) → ✅ gebaut (Paul-Zustimmung):** 4-Arg-Overload + „Log-only"-Javadoc gelöscht (Clean Break), 19 CompactServiceTest-Sites → 5-Arg mit NULL_MONITOR, Monitor = Pflichtparameter.
10. **Nebeneffekte:** Depth-Mutations-Nachweis für `refreshDir` (UC-PT-1-Pflicht), `PlanTool.refreshPlanDir` bleibt (lebt als CoreException-Übersetzung, nicht tot), 2 neue Null-Type-Safety-Warnings (PlanFileView/PlanTool) → known-benign-Warnings-Doc nachziehen (klein, offen).

Gates: Core/PDE grün (Zahlen je Commit-Message), Lint Baseline 8 (UC 195/195). Plan archiviert.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio** (wichtiger als async Agents). Danach async Agents (Top-3 in
  [async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- **Issue #163 („Tool Calls retten")** — recherchiert: langchain4j NICHT das Problem; Gegenmittel =
  Rescue-Heuristik im ToolService-Loop (~60–100 Zeilen). Paul entscheidet, ob/wann.
- Lint-Baseline-Befunde in einen Docs-Zyklus aufnehmen.
- R-CG-14 (Generation + `disposed` statt Lock) · R-CIB-4 Stufe 2 · R-CC-7 Fehlerklassen-Tabelle
  ApiRetry (❓) · Header-State-Leak (❓) · Context-Overflow-Zyklus (❓) · McpService
  `connect()`-Rollback-Test (ADR-0069).
- **OP-STREAM-1:** Stream-Hänger bei Verbindungsverlust/Notebook-Sleep, keine Aliveness-Erkennung
  → [streaming-display.md](../org.sterl.llmpeon/docs/streaming-display.md).

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20): nichts Repo-Spezifisches, minimale
  Änderungen, keine Docs-Inhalte — nur Verweise. Übersicht: docs/system-prompts.md.
- **Commit-Regel:** immer EXPLIZITE Pfadliste (`git add -- docs/ ...`), nie `git add -u`;
  Pauls WIP nie committen; Surefire-Ground-Truth + Commit-Hash aktiv einfordern, DONE-Claims
  gegen den IST prüfen. Evidenz (Surefire-Nummern, Messreihen) nie in docs — Commit-Messages +
  memory-Tools (memory#52).
- **Tool-Trap:** `diskRenameResource` Erfolg melden ohne zu verschieben — nach Renames
  verifizieren. Edit-Fallen (replaceLines-Insert, Edit-Deletion-Teil-Match): nach JEDEM Edit
  Zielbereich zurücklesen + Dubletten-Check; Ganzdatei-Rewrites via `diskWriteFile` bevorzugen.
- **Windows-Falle im Renderer:** in `lineSeparator()`-Strings immer `indexOf('\n')` suchen.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Docs-Restrukturierung (2026-10-22):** Feature-Doc-Pfade sind modul-qualifiziert
  (`org.sterl.llmpeon.core/docs/x.md`, `org.sterl.llmpeon/docs/x.md`); ADRs + Root-Docs
  bleiben in `docs/`. Linter-Discovery findet Modul-Roots automatisch.
- **Issues/**: asm-Versions-Tabellen (issue-142) sind Fachinhalt, keine Evidenz — bewusst behalten.

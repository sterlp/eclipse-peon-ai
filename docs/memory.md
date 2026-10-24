# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## NACHTZYKLUS 2026-10-23 — ABGESCHLOSSEN (Details kompakt)

- Alle 3 Stories gebaut + gereviewt + geflippt: **webSearch** (ADR-0074, eigener DDG-Client),
  **Compact-UI-Sichtbarkeit** (🟡 Roster, Caller-Monitor, Agent-Name, Deferred-Reset),
  **Skill-Roots** (3 Roots, Präzedenz Projekt > `~/.agents` > `~/.peon`).
  Gates: Core 1320/0/0/0 · PDE 412/0/0/52 · Lint Baseline. Branch `story/night-2026-10-23`
  (von main `703916ce`), Close-Commit `4cecbd8c`, Retro `db93698a`. Merge = Pauls Entscheidung
  (Squash bevorzugt — danach memory#53 beachten).
- **Offen für Paul aus dem Zyklus** (Details: [open-points.md](open-points.md)):
  1. ⏳ Snippet-Disclosure-Zeile webSearch · ⏳ Deferred-Timing-Rückversicherung ·
     ⏳ First-Launch-Dir (`~/.agents/skills` anlegen?) · manueller Smoke §8 (Roster-🟡,
     Chat-Rebuild, Sklaven-Pfad, FAILED_EMPTY, Pre-Turn pending/flush).
  2. Scaffold-Prompt-Diff (Inc 3 `4a215831`) — 4-Augen by Paul.
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
  Tests inkl. Null-Header/uppercase/UTF-8-Default-Mutation-Guards. Commits `8964fcc8` (Code+Tests+
  Homepage+web-tools.md), `520ab5fd` (+xhtml-Test), `f90406f5` (+3 Zweig-Tests). Surefire
  **1334/0/0/0**, Lint baseline (UC-WEB-13 belegt). Da-Dok CONCERNS → alle aufgelöst.
  Offen für Paul: ⏳ `+xml` bewusst BREIT (SVG/RSS werden konvertiert + „converted from HTML" gelabelt,
  Pauls Issue-Vorschlag) — Verengung auf text/html+xhtml wäre Doc-Änderung; ⏳ Paul-Smoke
  (JSON/plain raw, Webseite konvertiert, Cache-Label stabil).
- **T-3 = I-2 ✅ GEBAUT (2026-10-24):** R-WEB-14/UC-WEB-14 — Rate-Limit-Meldung nennt Handlungsoption
  (nicht sofort retryen, webFetchAsMarkdown-Alternative), KEIN Auto-Retry (evidenzbasiert: Sperre hielt
  Minuten), @Tool-Text warnt + rät zum Staffeln. Pattern-Erkennung (rate limit|bot check|HTTP 202/429,
  CASE_INSENSITIVE) + Null-Cause-Guard; 5 Tests, alle Zweige mutationsgepinnt, callCount==1 = Retry-Beweis.
  Commits `81b4a2dc`, `5edfffbf`, `062d43ae`. Surefire **1341/0/0/0**. Offen für Paul: ⏳ Paul-Smoke gegen
  echtes DDG-Rate-Limit; ⏳ Engine-Seite „kein Retry" untestbar in diesem Inkrement (Request-Zähler im
  Engine-Test = Follow-up); Cleanup-Kandidat typisierte `WebSearchRateLimitException`.
- **T-4 = I-3 (niedrig):** HTML→Markdown läßt `<pre>`/Code-Blöcke zerfließen (Zahlen-Gutter,
  Leerzeilen je Code-Zeile) → Zeilennummern wertlos, Token-Ballast.
- **T-5 = I-4 ✅ GEBAUT (2026-10-24):** R-WN-4/R-WN-5 in workspace-nesting.md — Round-Trip-Garantie
  (Resolver-Last-Resort über Disk-Lokation, `resolved via`-Disclosure; Treiber S4/S3 aus Da-Mek-Messung
  S1–S5), Diagnose statt nacktem „not found" (closed/disk-only/unbekannt), `findOpenProject` exakt
  (Substring-`contains` raus). Supersedes Paul 2026-10-16 „ohne Projekt-Disclosure" (False-Negative-Klasse
  teurer — in eclipse-read-tools.md dokumentiert, in Summary melden). `src/`-Magic-Entfernung ⏳ (falsch-positiv,
  Inventar erst). Commits `0a3ab0c9` (R-WN-5), `b2d30906` (R-WN-4; korrigiert Da-Meks Halluzination in
  0a3ab0c9s Message). PDE **426/0**. ⏳ T-1/T-1a brauchen Paul; T-4 offen (niedrig).
- **T-8 ✅ GEBAUT (2026-10-24):** PlanTool — R-PT-1 Archivierung toleriert disk-geschriebene Pläne
  (refreshLocal auf peon-plan vor exists/move, beide Fallen: stiller Return + ResourceException,
  Fehler nie still, Cancellation läuft ungefangen) + R-PT-2 planRead/hasPlan/planUpdate sehen
  disk-geschriebene Pläne (kein „No plan exists"-False-Negative, gemeinsamer Helfer refreshPlanDir,
  tote Guards raus). PlanToolTest 6 Tests, alle mutationsgepinnt (inkl. Move-Failure via
  read-only-Ordner). Neue Feature-Docs: org.sterl.llmpeon/docs/plan-tool.md (✅) + index.md-Zeile.
  Commits `28ae9fed`/`4e563e11`/`411bc3b6`. PDE-Suite **418/0**. ⏳ AgentContextComponent.readPlan
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
- **T-6 + T-7 ✅ GEBAUT (2026-10-24, Story-2-Follow-up R-CT-8):** Commits `2d55eba0` (Code+Tests+
  Homepage), `14857b8c` (Review-F1: Sequenz-Test End-Zeile-nach-onCompactCompleted + Cancellation-
  Test, 2 Mutationen bewiesen), `00dbbb6f` (Docs-Flips ✅ + planImplemented-Archiv
  `peon-plan/overview-done-2026-10-24-10-21.md`). Surefire 1324/0/0/0. Docs: compact-lock R-CT-8 ✅
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
- **T-1b erledigt (kein Bug):** TeamException im change-agent-issue.txt = Egit-Provider-
  Instanziierung, von Eclipse Core geschluckt (getProvider fängt, loggt, markAsUnshared) —
  Agent-Wechsel ohne Schaden; Root-Cause steht im !ENTRY oberhalb des Stacks (Pauls Eclipse/EGit).
  Kein Fix in peon-ai.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Squash** Nachtzyklus-Branch + Smoke. Danach Branch löschen.
2. ⏳ Delegations-Compact pro Delegation (Kosten, ADR-0071) · ⏳ Compact-Briefing-Schema
   (compressor.md) · ❓ Fixture-Duplikat · ❓ R-Regeln ohne Linter-Gate · ❓ In-Loop-Direkt-Compact
   fängt Exceptions nicht · ⏳ Reserved-Name `codegraph` · ⏳ R-CG-15 Monorepo-State-Refresh ·
   ⏳ R-CC-19-Semantik+Tilde · ⏳ Windows-`cmd /c`-Fallback · 🔒 S3 (🗜 in PoDelegateTool).
3. ❓ Testnamen-im-Fließtext-Slice · ❓ Linter-Guard gegen Evidenz-Drift ·
   vorbestehende R-CC-18-/R-DEF-Doppedefinitionen.

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

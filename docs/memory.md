# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.
> Diese Datei ist ein Zwischenstand, kein Protokoll: Erledigtes fliegt raus.

## 2026-10-27 — Plugin-Regression-Zyklus — GEBAUT, Review CONCERNS, Abschluss offen

- **Branch:** `web-factory` (Paul: kein 2. Branch). Paul will **eine Sache pro Nachricht**, kurz.
- **Gebaut (Core Surefire 1392/0):** Inc 1 `1d11430a` Aufräumen (OriginMonitor & Co.) · Inc 3
  `993c0525`+`238b7cd3` R20 Turn-Zeit · Inc 4 `1438f019` R-CC-18/UC-CC-2 · Inc 2a/b/c
  `7b6e5bf4`/`2b74aaa0`/`d0b7e2a0` R-CT-7 Variante B · Nachtrag `2d804212` (C1 tote `callBlocking`-
  Überladung raus, Skill `repo-api-traps` +UserMessage-Join, CHANGELOG `[Unreleased]`).
- **✅ geflippt:** R20, R-CC-18, UC-CC-2, UC-CT-10, UC-CT-16. **R-CT-7 + UC-CT-17 bleiben ❌** bis zum
  manuellen Smoke (Da Dok C4: View-Hälfte ist fehlender Code, kein Test fängt ein Wiedereinbauen).
- **Uncommitted (Jon):** Status-Flips in compact-lock.md, compact.md, streaming-display.md,
  org.sterl.llmpeon/docs/index.md, homepage/src/usage/agents.md:54, docs/memory.md → mit Abschluss-Commit.
- **Offen für Paul (je eine Frage):**
  1. Manueller Smoke R-CT-7: lange Session → Pre-Turn-Compact → Delegation; frischer Chat → erste
     Runde delegiert/searchAgent; Button-Compact. Achten: AI-Antwort doppelt bei Rebuild im Turn?
  2. `non-peon-ai-agents-md/AGENTS*.md` gelöscht im Arbeitsbaum — gewollt? eigener Commit?
  3. `org.sterl.llmpeon.test` baut schon vor dem Zyklus nicht (`jakarta.annotation [2.1,3)` fehlt im
     Target) → Plugin-Tests blind.
  4. `StreamingBridge`-`@deprecated` → „maybe" war Pauls WIP, mitcommittet (Da Dok C2) — ok?
  5. Regressionsschranke View „kein Rebuild an `onCallStart`" (Da Dok) — braucht Plugin-Test-Setup (3).
- **Klein/Backlog:** `MutableClock` 7× dupliziert → Test-Fixture (Da Dok C3) · `AbstractAgent.asyncCall`
  protected ohne Aufrufer → löschen? · `CompactRun.messageCountAfter` bleibt (Jon).
- **Danach:** planImplemented (Da Mek) · Agenten clear. aifactory-Docs-Review läuft (Stand:
  [aifactory/docs/memory.md](../aifactory/docs/memory.md)).
- Paul will `aifactory/` später als Geschwister-Ordner aus dem Repo ziehen (er selbst).

## 2026-10-27 — Neues Thema: Doc-Regeln (Paul)

- a) ✅ entschieden: R-DL-27 🚧 (docs-linter.md) — Hinweis `ZU_LANG` > 1000 Zeilen, ALLE Docs, schwach.
  po.md:88 umformuliert (1000 Zeilen → User fragen). Linter-Bau offen; ZU_LANG erst nach Bau in po.md
  nennen (Prompt-Pfad im Plan ausweisen!). Jon-Skill (GitHub) braucht dieselbe Zeile — Paul pflegt.
  Hinweis: 15k-Zeilen-Doc nicht gefunden; größte docs-linter.md ~1060, po-agent-jon.md 966.
- b) po.md-Regel geschrieben (Produkt-Vision, eine pro Produkt, ≤ 100 Z., Vision/Zusammenfassung/
  Kernfunktionen/ist–ist nicht/1 Diagramm). **Zwei Produkte** (Paul): Plugin → `docs/product-vision.md`,
  Web-App → `aifactory/docs/product-vision.md`. Inhalte noch offen. Jon-Skill (GitHub) nachziehen — Paul.
- c) ✅ `org.sterl.llmpeon.core/docs/ground-rules.md` GR-1…3 (Core = shared lib, Erweiterungen nur klar
  generisch). Verlinkt: Core-index, core/AGENTS.md, AF-ADR-001 Pkt. 4 + Consequences nachgezogen.
- d) ✅ `docs/product-vision.md` abgestimmt (Agenten-System, local first/cloud second, kein Code-Completion).
  Web-App-Vision später mit aifactory-Docs-Reparatur.
- ✅ R-DL-27/UC-DL-100 `f4bdd688` (Core 1393, 1 Test, 5 Mutationen rot) · Docs `f989cf16` · po.md gekürzt
  + ZU_LANG `e52be183`. Plugin zeigt ZU_LANG erst nach Neustart (alter Core geladen).
- **Prompt-Neutralität (Paul: keine Sprache/Plattform bevorzugen):** Kandidaten gefunden, NICHT geändert —
  search-agent.md:11-13 „Eclipse workspace tools" als Prio 1 · planner.md:17 `pom.xml/package.json/
  build.gradle` (Beispiele, eher ok) · scaffold-agent.md:38/93 „Java startsWith/regex" (Implementierungs-
  fakt, ok) · :73 Beispiel-Tool `eclipse`. Nächst: mit Paul entscheiden.
- ✅ search-agent.md Tool-Priorität plattformneutral (Paul ja) — `575488af`, Core 1393/0.
- **aifactory-Docs-Review läuft** — Stand nur in [aifactory/docs/memory.md](../aifactory/docs/memory.md).
- Lint-Baseline Core: 14 UNBELEGT_ERLEDIGT (UC-DL-48…53/55 Tool-Zuordnung, ATC-4, TF-2/4, OD-1/10,
  TD-3/4) + 12 VERWAIST UC-DEF-* in Core-Tests (Def-Doc liegt im Plugin) → eigenes Aufräum-Thema.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ Merge/Squash `story/night-2026-10-23` + Branch löschen.
2. ❓ Reseed-Arbeit (R-CC-9, 2026-10-25) — prüfen, ob inzwischen committet (`2eef6804`?).
3. ❓ E2E-Doc-Fixes an `compact-e2e.md`, `search-agent-e2e-test.md`, `webtools-test.md`.
4. ⏳ Delegations-Compact pro Delegation · ⏳ Compact-Briefing-Schema · ❓ Fixture-Duplikat ·
   ❓ R-Regeln ohne Linter-Gate · ❓ In-Loop-Direkt-Compact fängt Exceptions nicht ·
   ⏳ Reserved-Name `codegraph` · ⏳ R-CG-15 · ⏳ R-CC-19-Semantik+Tilde · ⏳ Windows-`cmd /c`.

## Ältere, noch offene Fäden (kurz)

- **Bug-1-Restlücken:** Pre-Turn-Check zählt frische Message + Turn-Context nicht mit · Budget
  (`autoCompactAfter` 168k) nie gegen Modellfenster (170k) gedeckelt — Paul nicht gefragt.
- **Bug-Triage 2026-10-24:** T-1 Skill-Routes nach Reload · T-1a per-Root-Reload-Output · T-4
  HTML→Markdown `<pre>` · T-9 Shutdown vs. Jobs ([chat-job-lifecycle.md](../org.sterl.llmpeon/docs/chat-job-lifecycle.md)) · T-3.
- **E2E-Triage 2026-10-25:** E2 Compact-Hint-Zeile · Refresh-Seam-Duplikat.
- **Homepage 2.15.0** (webSearch, Compact-Sichtbarkeit, Skill-Roots, last-search-rolling) steht aus.
- `result 0k` (Zeichen statt k bei < 1000) — nicht gebaut.
- **Doc-Hygiene-Kandidat:** historische `*-plan.md` (`sklaven-kontext-plan.md`,
  `agenten-status-im-header-mvp-plan.md`) — Paul: löschen oder historisch markieren.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio**, danach async Agents ([async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- **agent-origin** (Plugin): Agentenname je Chat-Nachricht + Experten-Einstellung „nur finale
  Agenten-Antwort" (Paul 2026-10-27, später). Web braucht es nicht (AF-ADR-002).
- TempFileTool Slice 2 · Issue #163 (⏳ geparkt) · Lint-Baseline · R-CG-14 · R-CIB-4 Stufe 2 ·
  R-CC-7 ApiRetry-Tabelle (❓, Overflow blind retried) · Header-State-Leak · McpService-Rollback-Test.
- **OP-STREAM-1:** Stream-Hänger bei Verbindungsverlust → [streaming-display.md](../org.sterl.llmpeon/docs/streaming-display.md).

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20). Übersicht: docs/system-prompts.md.
- **Commit-Regel:** EXPLIZITE Pfadliste, nie `git add -u`; Pauls WIP nie committen (`README.md`,
  `peon-plan/`, `ai-e2e-test/**`, `non-peon-ai-agents-md/**`); Surefire + Commit-Hash einfordern.
- **Lint-Aufruf:** testRoots auf Core/Plugin beschränken, sonst 86 Schein-VERWAIST aus aifactory.
- **Tool-Trap:** `diskRenameResource` verifizieren; nach JEDEM Edit Zielbereich zurücklesen.
- **Windows-Falle im Renderer:** `indexOf('\n')` in `lineSeparator()`-Strings.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Docs-Pfade** modul-qualifiziert; ADRs + Root-Docs in `docs/`.

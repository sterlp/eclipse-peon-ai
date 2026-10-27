# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.
> Diese Datei ist ein Zwischenstand, kein Protokoll: Erledigtes fliegt raus.

## 2026-10-27 — Plugin-Regression-Zyklus (Paul-Go) — SOLL fertig, Plan als nächstes

- **Branch:** `web-factory` (Paul: kein 2. Branch). Plugin Code+Docs+Homepage; Web nur Docs, Web-Code
  darf brechen. Paul will **eine Sache pro Nachricht**, kurz.
- **SOLL geschrieben (alle ❌):**
  - Aufräumen Core: `OriginMonitor`, `onAgentCallCompleted`, `onDelegatedAnswer`, `ToolResultAttributes`,
    `AbstractTool.resultAttributes` raus; Feld `origin` bleibt ungenutzt. `FileLlmConfigStore` +
    `pathScopePrompt` bleiben (generisch). → AF-ADR-002 neu geschrieben.
  - Bug 3 → R-CT-7 (compact-lock.md, Variante B, Paul ok): Rebuild allein über
    `onCompactCompleted(agentName)` == aktiver Agent; Pre-Turn feuert nach Anfügen der frischen Frage.
    DEFERRED/`pendingChatReset`/Flush in `onCallStart` fallen. Interface bleibt. UC-CT-10/16/17.
    `withinTurn`-Param an `onCompactCompleted` fällt (Paul ok).
  - Bug 2 → R20 (streaming-display.md): Turn-Startzeit gehört dem Agenten (working-Phase), Bridge nur
    Call-Timer (Total-Timer + Bridge-`startedAt` raus). IST-Ursache `ConfiguredChatModel.callBlocking`
    → `new StreamingBridge()`.
  - Bug 1 → R-CC-18 + UC-CC-2 (compact.md): Erfolg sperrt keinen weiteren Direkt-Compact
    (`attemptedDirect`, HandleToolLoopCompactComponent:33). Keine Regression, alter Bug.
  - compact-architektur.md Δ 2026-10-27 (Turn-Zeit-Besitz, Rebuild in View, Mermaid).
- **Nächst:** Plan (Da Thinka) → Abnahme → Inkremente: 1 Aufräumen (nur-löschen) · 2 R-CT-7 ·
  3 R20 · 4 R-CC-18 · 5 Homepage-Changelog. Danach Review (Da Dok), ✅-Flip mit lintDocsAndTests.
- **Commit-Liste Jon-Dateien:** docs/memory.md, org.sterl.llmpeon.core/docs/{compact-lock,compact,
  compact-architektur,index}.md, org.sterl.llmpeon/docs/{streaming-display,index}.md,
  proposals/agent-origin-proposal.md, aifactory/docs/{adr/002-…,adr/index,aifactory-architektur,
  web-prototype,index,memory,open-points}.md.
- Paul will `aifactory/` später als Geschwister-Ordner aus dem Repo ziehen (er selbst).
- **aifactory-Docs NICHT Ground Truth** (Paul 2026-10-27): weitgehend von schwächerer AI selbst
  geschrieben (MVP-Web-UI statt Plugin). Jon darf/soll sie reparieren — eigenes Thema, Zeitpunkt offen.
- **Plan-Q4 offen (Jon klärt technisch):** UC-CT-17 ehrlich testen — A Core-Test (nur Agent-Hälfte,
  Marker?) · B `ChatResetGate` in Core · C Plugin-Test (Da Thinka: ohne Display nicht; CI hat aber xvfb → prüfen).
- Plan-Entscheide: Q1=B, `withinTurn` raus, Q2 `[Unreleased]` in Root-CHANGELOG (Text Jon), Q3 `SearchAgentTool.NAME` raus.

## 2026-10-25 — RESEED-FORM ✅ single UserMessage (offen: Commit)

- SOLL R-CC-9/ADR-0076: nach Compact genau **eine** UserMessage (Turn-Context + letzter TextContent
  `Session compacted:` + Summary). R-CT-7 hängt jetzt daran (UC-CT-16).
- Tests nachgezogen (Da Mek), **UNCOMMITTED** — Stand vor diesem Zyklus prüfen.
- Offen klein: `result 0k` (Zeichen statt k bei < 1000) — nicht gebaut.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ Merge/Squash `story/night-2026-10-23` + Branch löschen.
2. ❓ Commit der Reseed-Arbeit — auf welchem Branch?
3. ❓ E2E-Doc-Fixes an `compact-e2e.md`, `search-agent-e2e-test.md`, `webtools-test.md`.
4. ⏳ Delegations-Compact pro Delegation · ⏳ Compact-Briefing-Schema · ❓ Fixture-Duplikat ·
   ❓ R-Regeln ohne Linter-Gate · ❓ In-Loop-Direkt-Compact fängt Exceptions nicht ·
   ⏳ Reserved-Name `codegraph` · ⏳ R-CG-15 · ⏳ R-CC-19-Semantik+Tilde · ⏳ Windows-`cmd /c`.

## Ältere, noch offene Fäden (kurz)

- **Bug-Triage 2026-10-24:** T-1 Skill-Routes nach Reload · T-1a per-Root-Reload-Output · T-4
  HTML→Markdown `<pre>` · T-9 Shutdown vs. Jobs ([chat-job-lifecycle.md](../org.sterl.llmpeon/docs/chat-job-lifecycle.md)) · T-3.
- **E2E-Triage 2026-10-25:** E2 Compact-Hint-Zeile · Refresh-Seam-Duplikat.
- **Homepage 2.15.0** (webSearch, Compact-Sichtbarkeit, Skill-Roots, last-search-rolling) steht aus.
- **Doc-Hygiene-Kandidat:** historische `*-plan.md` (`sklaven-kontext-plan.md`,
  `agenten-status-im-header-mvp-plan.md`) — Paul: löschen oder historisch markieren.
- Optional: Budget-Deckel aufs Modellfenster (`autoCompactAfter` 168k vs. Server 170k) — Paul nicht gefragt.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio**, danach async Agents ([async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- **agent-origin**: „Monitor kennt den aktuellen Agenten" (Proposal §7a) → dann UC-WP-39/40 neu.
- TempFileTool Slice 2 · Issue #163 (⏳ geparkt) · Lint-Baseline · R-CG-14 · R-CIB-4 Stufe 2 ·
  R-CC-7 ApiRetry-Tabelle (❓, Overflow blind retried) · Header-State-Leak · McpService-Rollback-Test.
- **OP-STREAM-1:** Stream-Hänger bei Verbindungsverlust → [streaming-display.md](../org.sterl.llmpeon/docs/streaming-display.md).

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20). Übersicht: docs/system-prompts.md.
- **Commit-Regel:** EXPLIZITE Pfadliste, nie `git add -u`; Pauls WIP nie committen (`README.md`,
  `peon-plan/`, `ai-e2e-test/**`); Surefire + Commit-Hash einfordern.
- **Tool-Trap:** `diskRenameResource` verifizieren; nach JEDEM Edit Zielbereich zurücklesen.
- **Windows-Falle im Renderer:** `indexOf('\n')` in `lineSeparator()`-Strings.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Docs-Pfade** modul-qualifiziert; ADRs + Root-Docs in `docs/`.

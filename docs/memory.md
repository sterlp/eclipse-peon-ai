# Open Ends / Memory — Session-Zwischenstand

> docs/** — insbesondere memory.md — schreibt AUSSCHLIESSLICH Jon. Agenten liefern Facts im Chat.

## NACHTZYKLUS 2026-10-23 — läuft (Paul-Order: "compact und nachtzyklus - wir sind auf main -- alles andere bauen was wir aufgenommen haben")

- **Branch:** `story/night-2026-10-23` von aktuellem main (Da Mek anlegen lassen; vorher Git-IST prüfen — memory#53). Pauls WIP NIE committen; explizite git add-Pfadliste.
- **Issue-163-Doc:** `docs/issue-163-tool-calls-retten.md` ✅ geschrieben (Ticket-Entwurf, ohne Links); open-points.md verlinkt.
- **✅ Story 1 webSearch FERTIG (2026-10-23 Nachtzyklus):** 4 Inkremente — `ba6db4dc` (Engine+Parser), `8873d6c6` (Tool), `b5e7cd17` (Registrierung+Homepage), `6f309afc` (Review-Fixes F1–F5). Core 1294/0/0/0, Plugin 408/0/0, Lint WEB-Scope sauber. Da-Dok CONCERNS → alle Findings behoben (Bot-Check an Selektor gebunden — echter Selektor `div.anomaly-modal__modal[data-testid=anomaly-modal]`, Live-Smoke 202). Docs geflippt ✅ (web-tools.md, -architektur.md, core/docs/index.md). ADR-0074. Offen für Paul: ⏳ Snippet-Disclosure-Zeile (open-points). planImplemented/Archivierung durch Da Mek im Close-Commit.
- **Story-Reihenfolge** (je Voll-Loop: plan → Jon-Abnahme → build → Da-Dok-Review → ❌→✅ + lintDocsAndTests-Gate):
  1. **webSearch** — R-WEB-10…12, `org.sterl.llmpeon.core/docs/web-tools.md` + `-architektur.md`. Details: Sektion "Story 3" unten. Plan-Risiko: community-DDG-Modul zu langchain4j 1.22.0 passen (pom.xml:14); im Aggregator-Checkout nur google/tavily/searchapi → Fallback dünner eigener DDG-Client.
  2. **Compact-UI-Sichtbarkeit** — R-CT-4…7 (`compact-lock.md`) + R-CC-14 (`compact.md`). 🟡 Compact-Status Roster; onChatMessage→Caller-Monitor (Tests `compressorRequestStartReachesCallerMonitor`/`compressorStreamingStaysLogOnly` — alt ersetzt, Inc 2 `aece6f1e`); Agent-Name in AbstractAgent.java:406-408; Chat-Reset bei aktivem Agent bei ALLEN Triggern (heute nur AIChatView.java:499 Button-Pfad); stale Kommentar AIChatView.java:512-513 im Inkrement fixen.
  3. **Skill-Roots** — R17 (`project-skills.md`), 3 Roots, Präzedenz Projekt > `~/.agents/skills` > `~/.peon/skills`; `.peon/skills` deprecated aber gelesen+SCHREIBBAR; Scaffold `.agents`-Empfehlung; Scaffold-Migration auf User-Wunsch; Homepage `setup/agents-and-skills.md:78` im Inkrement.
- Paul nicht erreichbar → Abnahme durch Jon (memory#39, Inkrement-Gating). Bericht nach jeder Story.

## Stand 2026-10-23 — Story 3: Web-Suche (webSearch) — Docs fertig, NICHT gebaut · Issue #163 recherchiert

- **webSearch (Paul 2026-10-23):** Docs stehen — web-tools.md R-WEB-10…12 ❌ + web-tools-architektur.md
  (WebSearchTool-Zeile, DDG über langchain4j `WebSearchEngine`-Interface, Community-Modul
  `langchain4j-community-web-search-engine-duckduckgo`). Design: Trefferliste (Titel/URL/Snippet)
  max 8 in den Kontext, Disclosure „showing N of M", Seiteninhalte nie in den Kontext (Nachlesen via
  webFetchAsMarkdown), NICHT isEditTool → alle Agenten (Paul „allen geben"), kein Toggle.
- **Plan-Risiko:** Community-Modul muss zu langchain4j 1.22.0 passen (im Aggregator-Checkout
  /langchain4j existiert NUR google-custom/searchapi/tavily — DuckDuckGo liegt im community-Repo);
  Fallback = dünner eigener DDG-Client. DDG-Tutorial: langchain4j docs/docs/integrations/web-search-engines/duckduckgo.md.
- **Issue #163 („Tool Calls retten") — recherchiert, Backlog (open-points.md 2026-10-23):**
  langchain4j NICHT das Problem (Provider mappen nur strukturierte Felder — kein Upstream-Issue).
  Gegenmittel wäre Rescue-Heuristik im ToolService-Loop („function=<Name>"-Text → ToolExecutionRequest,
  nur registrierte Namen). Paul entscheidet, ob/wann.

## Stand 2026-10-23 — Story 2: Skill-Roots (~/.agents/skills) — Docs fertig, NICHT gebaut

- **Paul-SOLL:** Skill-Service liest 3 Roots: `~/.agents/skills` (NEUER Default, APM-/MS-Agent-
  Package-Manager-Kompatibilität, auch auf Homepage) · `~/.peon/skills` (deprecated, gelesen UND
  schreibbar — Support fällt NICHT weg) · `<Projekt>/.agents/skills` (unverändert). Präzedenz:
  Projekt > ~/.agents > ~/.peon (Paul „ja"). Scaffold: Empfehlung IMMER `.agents` (User-Root oder
  Projekt, nach Nachfrage R11) — `.peon/skills` schreibbar aber nicht empfohlen. Homepage:
  Info/Warnung — `.agents` (User + Projekt) empfehlen. **Migration:** keine automatische (Clean
  Break) — auf User-Wunsch führt der SCAFFOLD die Migration aus (beide User-Roots = seine
  Validator-Roots). Commands/prompts folgen SPÄTER evtl.
  (nicht Teil dieser Story).
- **Docs geschrieben:** project-skills.md (Ziel, R2, R17 ❌ + 5 BDD, R10/R11 amendiert, R14-Note,
  BDD-Tabelle R17) · glossary.md Config-Skills · configuration.md Skills-Zeile ·
  peon-config-directory.md Skills-Zeile · ADR-0041 Consequence-Zeile (Skills-Default zieht um).
- **Stale entfernt:** advanced-configuration.md „First-Launch Directory Resolution"
  (`~/.claude/skills` → `~/.llmpeon/skills`) — existiert NICHT im Code (verifiziert: keine
  `.claude/skills`-Resolution in Java; configDir = `~/.peon` via LlmPreferenceInitializer/LlmConfigLoader).
- **Homepage** (setup/agents-and-skills.md:78 nennt ~/.peon/skills) — Update gehört ins Build-Inkrement
  (AGENTS-Regel: user-facing im selben Increment). Da Mek erhält die Pfadliste im Plan.
- **Tests-Neu:** `SkillServiceTest.threeRoots_precedenceProjectOverAgentsOverPeon`,
  `agentsRootMissingLoadsEmpty`. Betroffene IST-Stellen: SkillService (configComponent → Liste),
  PeonAiService-Wiring, Scaffold-Validator-Roots (ADR-0043 — bleibt mit configDir-Root), Scaffold-
  Prompt-Empfehlung (`.agents` statt configDir), Homepage-Warnung (setup/agents-and-skills.md:78).
- **Nächster Schritt:** Plan + Build (nach Pauls Stories).

## Stand 2026-10-23 — Compact-UI-Sichtbarkeit (Docs, Paul-WTF-Meldung)

- **Anlass:** Paul sieht beim Compact nichts im UI — `CompactService` ist monitor-frei (R-CC-14).
  IST-Verifikation: Chat-Reset existiert NUR beim Button-Pfad (`AIChatView.java:499`);
  `compactSession`/Pre-Turn/In-Loop-Direct bauen die View NICHT neu (HTML läuft voll).
  Start-Zeile `AbstractAgent.java:406` nennt keinen Agent. Stale Kommentar
  `doCompressAgent:512-513` („summary streams into the chat live") — stimmt seit R-CC-14 nicht mehr.
- **Pauls SOLL (2026-10-23):** UI zeigt, dass der Compressor arbeitet · Compact-Ereignisse nennen
  den Agent · Main-Agent-Compact → Chat-View-Reset (HTML läuft nicht voll). Async-Hinweis bewusst
  NICHT aufgeschrieben („das haben wir gar nicht").
- **Revision 2 (Paul, 19:24):** alte Entscheidung R-CT-4 (2026-09-22 „working reicht, kein separater
  Compact-State") GELÖSCHT und ersetzt: **eigener Compact-Status** (🟡 `compacting` im Roster,
  `AiAgentStatusWidget.text()`-Präfix) — Blatt-Regel unverändert. 🟡-Zeile aus open-points entfernt.
- **Docs geschrieben (❌ specified, kein Code):**
  - `compact.md` R-CC-14: Feedback-Teil revidiert — `onChatMessage` erreicht Caller-Monitor,
    Streaming-Text bleibt log-only.
  - `compact-lock.md` R-CT-5 (Compressor working sichtbar, UC-CT-7/8) · R-CT-6 (Agent-Name,
    UC-CT-9) · R-CT-7 (Chat-Reset aktiver Agent, UC-CT-10/11/12).
  - open-points ❓ „Compact-Button ohne Monitor" (2026-09-29) — als Spezifikation aufgelöst, entfernt.
- **Nächster Schritt:** Plan + Build über Da Thinka/Da Mek (Tests: `compressorRequestStartReachesCallerMonitor`
  verschärfen, UI-Trigger teilweise manuell). Kein ADR — Entscheidung folgt meiner Empfehlung.
- **Status (Paul 2026-10-23):** 🟡-Roster-Status BESTÄTIGT. **NICHT bauen** — Paul hat noch
  2 weitere Stories, danach Planung dieses Inkrements.

## Stand 2026-10-23 — Docs-Slim-Zyklus ABGESCHLOSSEN

- **Paul hat den Branch per SQUASH gemerged** (`2a094b5d` #164, Inhalt byte-identisch bis `56d3cc65`)
  — mein Weiterbauen auf dem alten Branch erzeugte Pseudo-Konflikte; Da Mek hat `b9259ed5`–`4ce61a1d`
  (+ Pauls Code-Commits, 7 Commits) per `git rebase --onto main 56d3cc65` auf main gehoben:
  Branch `0a5bc5b8`, **0 Konflikte**, alle Gates grün (Lint = Baseline, PDE-Suite grün).
  main kann jetzt FF-mergen.
  Backup: `backup/docs-restructure-prerebase-2026-10-23` (= alter `4ce61a1d`, danach löschen).
  Remote `origin/story/docs-restructure-2026-10-22` divergiert (24/8) — Push nur mit
  `--force-with-lease`, Pauls Entscheidung. Danach: `releng/llmpeon-target` installen, Branch löschen.
- Danach auf Branch: Cycle-Close-Commits `b9259ed5` (docs clean) + `4ce61a1d` (R-CG-16:
  CodegraphContextItem nennt „Codegraph indexiert keine *.md files", 2 Tests, Plugin-Suite grün).
- Branch trägt auch **Pauls Code-WIP** (`fb66b8b4`: AgentsTool/AsyncStreamingBridge/AsyncMonitor +
  AbstractAgent/AiAgent/Timer/ToolLoopRequest; `573eb601`: `org.sterl.llmpeon/pom.xml` −1 Zeile) —
  für diese Teile lief kein Core-Suite-/Plugin-Build.
- **Skill-Evolution:** `peon-build-test` gepatcht (Core-Single-Class-Gate offline OHNE `-am`/Reactor,
  `clean` zwingend). Ledger `.agents/skills/wiki/skill-impact.md` = Accepted.
- **Neu im Zyklus:** OP-STREAM-1 (Stream-Hänger bei Verbindungsverlust/Notebook-Sleep, keine
  Aliveness-Erkennung → streaming-display.md) · ADR-0073 (slf4j-api bleibt gebündelt, OSGi-Resolve
  negativ bewiesen) · SwtUi-Guard (Tests skippen headless statt NPE).
- **README-Befund (Paul fragen, nicht selbst ändern):** Update-Site-Konflikt — README L48 +
  open-points sagen `https://sterlp.github.io/eclipse-peon-ai/` (2026-10-21), memory-Tool #46 sagt
  `https://peon-ai-4e.sterl.org/` (ebenfalls 2026-10-21). Beide datieren denselben Tag — Konflikt,
  hängt am offenen ⏳ p2-Pfad-Verifikation. Plus Proposal-only: doppelte „## Links"-Sektionen,
  Emoji-Legende, „very old stuff" in README.

## Offen für Paul (Details: [open-points.md](open-points.md))

1. ❓ **Merge/Squash** Docs-Branch → main + Smoke (Docs-Linter-Report, Root-Index auto-load). Danach Branch löschen.
2. ❓ **Merge/Tag 2.14.1** — `story/workspace-nesting-2026-10-19` lokal fertig; vorher
   `mvn -B clean verify`. Smokes: Dev/Plan/Review gleicher Prozentsatz ·
   Delegation-Compact > 70 % vor Auftrag · Monorepo · Codegraph nach Neustart.
3. ⏳ **Delegations-Compact feuert pro Delegation** (Kosten, ADR-0071) — Faktor anheben oder nur
   bei Themenwechsel? Rückversicherung steht aus.
4. ⏳ **Compact-Briefing-Schema** für `compressor.md`: 3 Ergänzungen vorgeschlagen (STANDING-RULES-Slot,
   preserve-Pfade erweitern, memory.md = complement-not-replace) — Freigabe steht aus.
5. ❓ Fixture-Duplikat in ≥3 core-Testklassen · ❓ R-Regeln ohne Linter-Gate (`DocsLinter:241/254`)
   · ❓ In-Loop-Direkt-Compact fängt Exceptions nicht (R-CC-20.5-Nachbarloch) · ⏳ Reserved-Name
   `codegraph` · ⏳ R-CG-15 Monorepo-State-Refresh · ⏳ R-CC-19-Semantik+Tilde · ⏳ Windows-
   `cmd /c`-Fallback · 🔒 S3 (🗜 in PoDelegateTool) blockiert auf Pauls WIP-Merge.
6. ❓ **Testnamen-im-Fließtext-Slice** (Paul-Entscheid 2026-10-19, noch nicht gefahren) ·
   ❓ Linter-Guard gegen Evidenz-Drift (positiv nachweisen) · vorbestehende R-CC-18-/R-DEF-Doppedefinitionen.

## Backlog (nächste Session)

- **Async-Shell-Tool — Paul-Prio** (wichtiger als async Agents). Danach async Agents (Top-3 in
  [async-agents.md](../org.sterl.llmpeon.core/docs/async-agents.md)).
- **TempFileTool** — Slice 2 Output-Trim 🚧 in design.
- Lint-Baseline-Befunde in einen Docs-Zyklus aufnehmen.
- R-CG-14 (Generation + `disposed` statt Lock) · R-CIB-4 Stufe 2 · R-CC-7 Fehlerklassen-Tabelle
  ApiRetry (❓) · Header-State-Leak (❓) · Context-Overflow-Zyklus (❓) · McpService
  `connect()`-Rollback-Test (ADR-0069).

## Notizen

- **System-Prompts generisch halten** (Paul 2026-10-20): nichts Repo-Spezifisches, minimale
  Änderungen, keine Docs-Inhalte — nur Verweise; Best Practice für alle Projekte.
- **Commit-Regel:** immer EXPLIZITE Pfadliste (`git add -- docs/ ...`), nie `git add -u`;
  Pauls WIP nie committen; Surefire-Ground-Truth + Commit-Hash aktiv einfordern, DONE-Claims
  gegen den IST prüfen.
- **Tool-Trap:** `diskRenameResource` kann Erfolg melden ohne zu verschieben — nach Renames immer
  verifizieren. Edit-Fallen (replaceLines-Insert, Edit-Deletion-Teil-Match): nach JEDEM Edit
  Zielbereich zurücklesen + Dubletten-Check; Ganzdatei-Rewrites via `diskWriteFile` bevorzugen.
- **Windows-Falle im Renderer:** in `lineSeparator()`-Strings immer `indexOf('\n')` suchen.
- **UserMessage-Join in `ThreadSafeMemory.add`:** Seed-Fixtures ggf. `AiMessage` als Trenner.
- **Docs-Restrukturierung (2026-10-22):** Feature-Doc-Pfade sind modul-qualifiziert
  (`org.sterl.llmpeon.core/docs/x.md`, `org.sterl.llmpeon/docs/x.md`); ADRs + Root-Docs
  (Glossar, memory, open-/resolved-points, Notes) bleiben in `docs/`. Linter-Discovery findet
  Modul-Roots automatisch.
- **Issues/**: asm-Versions-Tabellen (issue-142) sind Fachinhalt, keine Evidenz — bewusst behalten.

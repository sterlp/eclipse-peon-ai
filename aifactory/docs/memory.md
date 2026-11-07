# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** — gilt auch für Core-Tests. `AGENTS*.md` = nur Dauerwissen (Plan-Dateien sind das
Memory); Docs duplizieren nie Core-Regeln; Plan-Archive werden **gelöscht**, nie committet.

## Zyklus 2026-11-06/07 — Runde 4: AntD-Umbau (autonom; Paul offline, „durchbauen")

Branch `web-factory-mvp-2`, Spitze `a0a2c3f2` = origin. Backlog + Reihenfolge:
[open-points.md](open-points.md) („Runde 3 — AntD-Umbau"). Kette Inc 4: `832a9772` (4a) → `b51571b2`
(N1 Blätter-Guard) → `6249d59a` (N2 `.chat-line.pending`) → `eb103276` (4b) → `9704013a` (Turn-Id-Beweis
+ Docs) → `98f55700` (4c) → `8da14901` (4c-N1 Positionsnachweis) → `a0a2c3f2` (4c-N2 ein Entscheider
+ Docs-Flip).

**Inkrement 4 — Timeline — ist fertig und abgenommen ✅** ([R-WP-40/UC-WP-72](chat.md),
[R-WP-29/UC-WP-55](chat.md)):

* **4a kein `hr`** — `rule`/`<hr>` + CSS raus; Guard liest **alle** Blätter (`sheets.ts::allDeclarations`),
  `.chat-line.pending` malt nur noch `border-left-style`.
* **4b Werkzeug-Schritte** — Stempel = Name, Ankunftszeit am **geschlossenen** Schritt, Indikator am
  **letzten Schritt des offenen Turns** (Identität `turn`, **nicht** „letztes Element"), Dauer bleibt am
  Turn, Reload erfindet keine Zeit.
* **4c Delegation** — eigener Eintrag, typisiertes Anfang/Ende-Paar `{member, working}` aus den
  Core-Listenern (`PoDelegateTool.setStartListener/setEndListener`, **kein** Core-Eingriff), Wort
  „working" nur im offenen Turn, Ende ohne offenen Eintrag = No-op, Eintrag **steht an seiner Stelle**
  (nicht am Verlaufsende), Warteleiste nennt das Mitglied. Auftrag wird **nie** geparst — er steht in der
  Werkzeugzeile darüber (PK-2, unten).

**Stand gemessen (Review + Dev unabhängig gleich):** UI **191/191, 21 Dateien ~9 s**, `tsc -b` 0,
oxlint 0/0 · Backend **227/0**, `missCount 1` · Doc-Lint **29** Findings = nur Fremd-Docs (PC 14, CR 14)
+ `UC-WP-70` (Inc 5a). Dev/Review melden weniger — deren Lint scopet enger: **immer selbst messen.**
**Beweislage:** `openTimelineItem` (`chat.live.ts:87`) ist der **eine** Entscheider für „was arbeitet"
(Schritt **oder** Delegation, Identität, nie Position) — „je Turn genau ein Indikator" ist Konstrukt, kein
Kommentar; die Position des Delegations-Eintrags ist gegen die Antwortzeilen bewiesen
(`aDelegationStandsWhereItHappenedAndNotAtTheEndOfTheLog`). Bewusst **offene Restlücken:** zwei
Delegationen an dasselbe Mitglied paaren LIFO ohne Test (Draht trägt keine Call-Id) und das
Turn-Endereignis (❓ oben).

**Build-Merkpostel (neu):** `mvn -pl factory-backend` zieht `llmpeon-core` als **`2.8.1-SNAPSHOT` aus
`~/.m2`**. Pauls `PoDelegateTool`-Listener waren in der Quelle, nicht im Artefakt ⇒ two „Symbol nicht
gefunden". Lösung war Mechanik, kein Core-Eingriff: Core `clean test` (1482/0) → `install -DskipTests` →
`javap`-Gegenprobe. **Vor dem Fabrik-Bau immer erst das Core-Artefakt frisieren.**

**Nächste Schritte:** **Inc 5a** Sider + URL + Projektbaum ([R-WP-38/UC-WP-70](ui.md), C-3,
`aria-current` zurück) → **5b** Files-Unit/Responsive. Danach
Rest-Backlog: C-7 (Agent-Blick read-only live), C-2a, C-1 TurnClaim-Timeout, F8/F10, Mini 3c
(`data:`-Bild ⇒ Alt statt leerem `src`). Stagehand ⏳ parked (Node ≥22.18 vs. Bau 22.12).

## Entscheidungen dieser Runde (Kurzfassung)

AntD wird Design-Basis, Bootstrap raus ([R-WP-39](ui.md)); Navigation = **Projektbaum im Sider**, Klick
aufs Projekt öffnet den Chat, **kein „Jon"-Label** ([R-WP-38](ui.md)); **kein `hr`** zwischen Zeilen
([R-WP-29](chat.md)); Timeline für Werkzeuge/Delegationen ([R-WP-40](chat.md)); Markdown mit GFM +
einfachen Umbrüchen ([R-WP-28](chat.md)); Eingabe Auto-Grow/Refocus ([R-WP-41](chat.md)); **Zeiten bleiben
live** (kein zweiter Speicher, kein Text-Parsing). Tastatur: Enter sendet, Shift+Enter **und**
Ctrl/Cmd+Enter = neue Zeile ([R-WP-30](chat.md), Merkposten in [resolved-points.md](resolved-points.md)).
`!` = Prio-Nachricht des Cores, Fabrik reicht sie nur durch
([`queued-user-messages.md`](../../org.sterl.llmpeon.core/docs/queued-user-messages.md) R8/R11). Grenze:
[AF-ADR-003](adr/003-reference-ui-is-a-donor-not-a-source.md) — die Referenz `ai-schulung` gibt Muster,
**nie** unseren Zustand (C-5-Fix bleibt grün). [glossary.md](glossary.md) definiert **Jon** und **Paco**.

## Offen für Paul (❓/⏳ in [open-points.md](open-points.md))

1. **PK-2 entschieden (⏳):** Ein Delegations-Eintrag nennt das **Mitglied**, der **Auftrag** bleibt der
   Wortlaut der Werkzeugzeile darüber — der Listener trägt keinen Prompt, und ihn aus dem Text zu lesen
   wäre das Parse-Verbot der Regel. Auftrag **im** Eintrag = Core-Erweiterung, nur mit deinem Proposal.
2. **❓ Turn ohne Endereignis** (unbestätigt): `ProjectRuntime` sendet `done` nur bei nicht-null Antwort;
   ein entkommener `Error` könnte Indikator + Warteschleife stehen lassen. Empfehlung: eigene kleine Story
   „je Turn genau ein Endereignis".
3. **Inkrement-4-Auslegungen** (⏳): „arbeitet" = letzter Schritt des laufenden Turns; ein **Diff** ist
   kein Schrittende; Think-Abschnitt bleibt unsichtbar; Stempel ist praktisch immer „Jon".
4. Core: abgebrochener Turn verliert eine gepollte Queue-Eingabe (`UC-WP-50` bleibt ❌) — nur per
   Proposal. C-2 Core-Test-Proposal; Paco-Story; Dev-Slot-Wirkung; C-1, C-7, F3/F5/F8/F9/F10;
   Bild-Support-Anzeige.

## Fallstricke (gemessen)

* Bauen: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`.
  Backend `mvn -o -f aifactory/pom.xml -pl factory-backend clean test`
  (`-Dsurefire.failIfNoSpecifiedTests=false`), UI `cd factory-ui &&
  COREPACK_ENABLE_PROJECT_SPEC=0 pnpm test` (pnpm **10.28.0**, Node 22.12). Nur-UI-Änderung ⇒ nur
  UI-Tests. Smoke: `install -DskipTests` → `-pl factory-backend spring-boot:run
  -Dspring-boot.run.arguments=--server.port=8099` → `http://localhost:8099/ui/` (Port 8080 = IDE).
* **Anzeige eines offenen Zustands immer an das laufende Objekt binden** (Turn-Identität), nie an
  „Flag + letztes Element" — Live-Items überleben ihren Turn. Und: **Identität schützt das _Ob_, nicht das
  _Wo_** — ein neuer Timeline-Eintrag braucht auch den **Anker-Beweis** (Idiom
  `chat.page.test.tsx::toolLinesSurviveTheDoneRefresh`).
* `sheets.ts::declarations()` trifft nur den **ersten** Block ⇒ `allDeclarations` für Flächen-Verbote;
  Selektorwortlaut nie in CSS-Prosa; jsdom 26.1.0 ohne Layout/Farbe (`matchMedia`/`ResizeObserver`
  polyfilliert); Vitest ohne `.as()`; `expect(Optional).contains()` prüft Gleichheit.
* `MockLlmServer`: kein `getRequestCount()`, keine `system`-Nachrichten, SSE in einem Stück ⇒
  Cancel-mitten-im-Stream nur mit später antwortendem Endpoint. Guards
  `OneChatPerProjectTest`/`SharedProjectChatTest`/`ChatTurnTest`/`TeamInternalsTest` nur **verschärfen**.
* `lintDocsAndTests`/`nextIds`: Pfade relativ zum Session-cwd (`docRoots:["docs"]`,
  `testRoots`: `factory-backend/src/test`, `factory-ui/test`, `factory-ui/src`) — **`idPattern`
  weglassen** (sonst 0/0-Definitionen).
* Git mit Paul im selben Repo: **nie amend/rebase auf seinen Commits** (SHA halten), nur
  `aifactory/`-Pfade committen, Commit nimmt den ganzen Index. Tool-Quirks: `eclipseEditFile` verliert
  `oldString` bei langen Blöcken → `diskEditFile` kurz ankern; „line required" → `diskReplaceLines`;
  `*InsertLines` scheitern an Dateiende mit Leerzeile.
* Dev-Antworten kommen manchmal als rohes Tool-Fragment ⇒ **nie neu bauen**, Text-Status verlangen.
  Review-Verdicts und neue Inkremente kommen **nur von Jon**. Dev-Kontext >85 % → `compactDev`.
  **Review-Befunde nicht nur in `overview.md`** — der nächste Plan überschreibt sie; Vorgeschichte nach
  `peon-plan/dev-notes-antd.md`.
* `web-prototype.md` existiert nicht mehr — SOLL in `chat.md`/`files.md`.

# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** — gilt auch für Core-Tests. `AGENTS*.md` = nur Dauerwissen (Plan-Dateien und
`peon-plan/review-findings.md` sind das Memory); Docs duplizieren nie Core-Regeln; Plan-Archive werden
**gelöscht**, nie committet.

## Zyklus 2026-11-06/07 — Runde 4: AntD-Umbau („durchbauen"; Paul inzwischen wieder da, parallel im Core)

Branch `web-factory-mvp-2`, Spitze `680f2239` = origin. Kette Inc 4: `832a9772` (4a) → `b51571b2` (N1)
→ `6249d59a` (N2) → `eb103276` (4b) → `9704013a` → `98f55700` (4c) → `8da14901` (N1) → `a0a2c3f2`
(N2 + Docs-Flip) → `5cfd251e` (memory). Inc 5a: `c04f8ed1` (Sider + Docs) → `a32409ad` (URL = Name) →
`87949a90` (Cleanup) → **`680f2239` (5a-N1 Review-Delta)**.

**Inkrement 4 — Timeline ✅ abgenommen** ([R-WP-40/UC-WP-72](chat.md), [R-WP-29/UC-WP-55](chat.md)):
kein `hr`; Werkzeug-Stempel = Name + Ankunftszeit am **geschlossenen** Schritt; Indikator am letzten
Schritt **des offenen Turns** (Identität, nie Position; `chat.live.ts::openTimelineItem` ist der **eine**
Entscheider); Delegation als typisiertes Paar `{member, working}` aus den Core-Listenern (kein
Core-Eingriff), Eintrag **steht an seiner Stelle**, Auftrag wird **nie** geparst (PK-2, ⏳ unten).

**Inkrement 5a — Sider + URL gebaut und reviewed** ([R-WP-38/UC-WP-70](ui.md) G1+G3, R-WP-1, UC-WP-51/52,
R-WP-35). Review 2026-11-07: **ein BUG, nur im Test** — „Markierung = Funktion der Adresse" war nicht
bewiesen (drei Mutationen grün: geseedeter Klick-State, beim Mount eingefrorene Auswahl, eingefrorenes
Settings-`aria-current`). Behoben in 5a-N1: Markierung jetzt in vier Weiterleitungs-Fällen **und** über
einen Adress-Walk in einem Mount behauptet (M1b/M1c/M10 ⇒ 5/5/1 Tests rot). Dazu `navKeyOfPathname`
dekodiert das Segment (Adresse mit `%20` markiert denselben Eintrag), Leichen-Pfad im Guard-Test ersetzt.
**G2 (Einheit Files) fehlt bewusst** — darum bleiben R-WP-38/UC-WP-70 **❌**, bis 5b gebaut ist.
Befunde + Zurückgestelltes: [../peon-plan/review-findings.md](../peon-plan/review-findings.md).

**Inkrement 5b-1 — Einheit = Adresse** ([UC-WP-70 G2](ui.md)), gebaut `b4c202b0`: `chat/:project/files`
als Adresse (Kind-Route, rendert nichts), „offen" entscheidet **ausschließlich** die Adresse, die
Panel-**Fläche** mountet nur mit ihr („Schließen vergisst die Position" per Konstruktion), der Tab bleibt
außen gemountet; Sider-Eintrag „Files" **nur unter dem Projekt, das die Adresse nennt**, genau **ein**
`aria-current`. Review: **REJECTED — 2 BUGs, beide Nachweis/Kommentar, kein Verhalten**: (1) die
Sibling-CSS-Regel der breiten Tab-Kante war unbewiesen (Mutation „Fläche hinter den Tab" ⇒ grün) ⇒
DOM-Assertion `previousElementSibling` folgt; (2) der Kommentar in `app.routes.tsx` behauptete, eine
**Schwesterroute** remounte — **gemessen falsch** (React Router 7.18.5, kein zweiter `/messages`-Read);
Architektur-Doc korrigiert, Kommentar zieht nach. Dauerhaft: **CSS mit `+`/`~` braucht einen
Nestungs-Beweis** (jsdom sieht die Kante nicht). Alles Weitere:
[../peon-plan/review-findings.md](../peon-plan/review-findings.md).

**Gemessen (Spitze `b4c202b0`):** UI **214 Tests / 22 Dateien**, `tsc -b` 0, oxlint 0/0 (73 Dateien) ·
Backend **227/0** (in 5b nicht gebaut) · Doc-Lint **28** Findings = nur Fremd-Docs (PC 14, CR 14), kein
`UNBELEGT_ERLEDIGT`. Dev/Review scoppen enger — **immer selbst messen.**

**Nächste Schritte:** (1) **5b-1-N1** = die zwei Review-Punkte (DOM-Assertion + Kommentar), danach
**Flip R-WP-38/UC-WP-70 ❌→✅**; (2) **5b-2** = Aufräumen (LOW-1 zweiter Listen-Fetch in `app.routes.tsx`,
LOW-3 Rahmen-Test nach `test/app/`, LOW-4 Prosa kürzen); (3) Rest-Backlog: C-7 (Agent-Blick read-only live),
C-2a, C-1 TurnClaim-Timeout,
F8/F10, Mini 3c (`data:`-Bild ⇒ Alt statt leerem `src`). Stagehand ⏳ parked (Node ≥22.18 vs. Bau 22.12).

## Entscheidungen dieser Runde (Kurzfassung)

AntD wird Design-Basis, Bootstrap raus ([R-WP-39](ui.md)); Navigation = **Projektbaum im Sider** mit
**Wurzeleintrag „Projects"**, Klick aufs Projekt öffnet den Chat, **kein „Jon"-Label**
([R-WP-38](ui.md)); Adresse = **klarer Projektname** statt Registry-Id (C-3, Entscheidung gegen die Id
in [resolved-points.md](resolved-points.md)); Breakpoint `lg` = 991,98 px, `collapsedWidth={0}`,
`collapsed` **uncontrolled**; **UC-WP-51 bleibt** (Mechanik auf der Adresse `/ui/chat`, kein Eintrag);
G3-Breite wird als **„Sider auf Breite 0"** belegt, Pixel erst bei der Look-Abnahme am Browser. Dazu
Inc 4: kein `hr` ([R-WP-29](chat.md)), Timeline ([R-WP-40](chat.md)), GFM-Markdown
([R-WP-28](chat.md)), Auto-Grow/Refocus ([R-WP-41](chat.md)), Zeiten bleiben **live**. Tastatur: Enter
sendet, Shift+Enter **und** Ctrl/Cmd+Enter = neue Zeile ([R-WP-30](chat.md)). `!` = Prio-Nachricht des
Cores ([`queued-user-messages.md`](../../org.sterl.llmpeon.core/docs/queued-user-messages.md) R8/R11).
Grenze: [AF-ADR-003](adr/003-reference-ui-is-a-donor-not-a-source.md) — die Referenz `ai-schulung` gibt
Muster, **nie** unseren Zustand. [glossary.md](glossary.md) definiert **Jon** und **Paco**.

## Offen für Paul (❓/⏳ in [open-points.md](open-points.md))

1. **PK-2 entschieden (⏳):** ein Delegations-Eintrag nennt das **Mitglied**, der **Auftrag** bleibt der
   Wortlaut der Werkzeugzeile darüber — der Listener trägt keinen Prompt, ihn zu lesen wäre das
   Parse-Verbot. Auftrag **im** Eintrag = Core-Erweiterung, nur mit Proposal.
2. **❓ Turn ohne Endereignis** (unbestätigt): `ProjectRuntime.java:400` sendet `done` nur bei nicht-null
   Antwort; ein entkommener `Error` könnte Indikator + Warteschleife stehen lassen. Empfehlung: eigene
   Story „je Turn genau ein Endereignis".
3. **⏳ Mechanik, bitte zur Kenntnis:** `aifactory/peon-plan/` ist **nicht** ignoriert und
   `dev-notes-antd.md` bereits getrackt. Plan-Archive lösche ich, aber ein `git add -A` könnte sie
   wieder einfangen — `.gitignore`-Eintrag `peon-plan/` gefällig? (Ändere ich nicht ohne dich.)
4. Core: abgebrochener Turn verliert eine gepollte Queue-Eingabe (`UC-WP-50` bleibt ❌); C-2
   Core-Test-Proposal; Paco-Story; C-1, C-7, F3/F5/F8/F9/F10; Bild-Support-Anzeige.

## Fallstricke (gemessen)

* Bauen: Kern `clean test` → `install -DskipTests` → Fabrik `clean verify`; immer `clean`. Backend
  `mvn -o -f aifactory/pom.xml -pl factory-backend clean test`; UI `cd factory-ui &&
  COREPACK_ENABLE_PROJECT_SPEC=0 pnpm test` (pnpm 10.28.0, Node 22.12). Nur-UI-Änderung ⇒ nur UI-Tests.
  Smoke: `spring-boot:run -Dspring-boot.run.arguments=--server.port=8099` → `http://localhost:8099/ui/`.
* **Bei „Zustand kommt aus Quelle X" muss die Mutation geseedeten State UND Mount-Einfrieren treffen**;
  `unmount()` + Neu-Mount prüft zwei Mounts, nie eine Zustandsänderung. Und: Anzeige eines offenen
  Zustands immer an das **laufende Objekt** binden, nie an „Flag + letztes Element"; **Identität schützt
  das _Ob_, nicht das _Wo_** (Anker-Beweis, Idiom `chat.page.test.tsx::toolLinesSurviveTheDoneRefresh`).
* AntD 6.7.0: hell malt **`lightSiderBg`** (nicht `siderBg`); Responsive-Effekt ruft `matchMedia` **beim
  Mount** ⇒ `matches: true` genügt, aber **gefiltert** stubben (`includes('max-width')`); `collapsed`
  controlled ⇒ Effekt ignoriert; Zero-Width-Trigger ist ein **`span` ohne Rolle**; `Menu` liefert **keine**
  `navigation`-Landmarke ⇒ eigenes `<nav aria-label>`; `aria-current` am Eintrag = ANTD-A9.
* `sheets.ts::declarations()` trifft nur den **ersten** Block ⇒ `allDeclarations`; Selektorwortlaut nie in
  CSS-Prosa; jsdom 26.1.0 ohne Layout/Farbe; Vitest ohne `.as()`; `act()`-Rauschen bei AntD+React 19 ist
  Baseline.
* `MockLlmServer`: kein `getRequestCount()`, keine `system`-Nachrichten, SSE in einem Stück. Guards
  `OneChatPerProjectTest`/`SharedProjectChatTest`/`ChatTurnTest`/`TeamInternalsTest` nur **verschärfen**.
* `lintDocsAndTests`/`nextIds`: Pfade relativ zum Session-cwd (`docRoots:["docs"]`,
  `testRoots`: `factory-backend/src/test`, `factory-ui/test`, `factory-ui/src`) — **`idPattern`
  weglassen**.
* Git mit Paul im selben Repo: **nie amend/rebase auf seinen Commits**, nur `aifactory/`-Pfade,
  **kein Modell-/Tool-Name und kein `Assisted-by`-Trailer**. Tool-Quirks: `eclipseEditFile` verliert
  `oldString` bei langen Blöcken → `diskEditFile` kurz ankern; `*InsertLines` scheitern an Dateiende mit
  Leerzeile; Mutations-Restore nur per `cp` + `diff -q`, nie `git checkout`.
* Dev-Antworten kommen manchmal als rohes Tool-Fragment ⇒ **nie neu bauen**, Text-Status verlangen.
  Review-Verdicts und neue Inkremente kommen **nur von Jon**. Dev-Kontext >85 % → `compactDev`.
* `web-prototype.md` existiert nicht mehr — SOLL in `chat.md`/`files.md`.

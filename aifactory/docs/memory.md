# Session-Stand — AI Factory (OAF)

Paul: **ein Thema pro Nachricht**, kurz. Nur Jon schreibt `docs/`. Paul committet selbst (Core);
Fabrik-Inkremente committet Dev je Schritt und pusht. **Core-Änderungen NUR mit Pauls Approval, per
Proposal mit WARUM** — gilt auch für Core-Tests. `AGENTS*.md` = nur Dauerwissen (Plan-Dateien und
`peon-plan/review-findings.md` sind das Memory); Docs duplizieren nie Core-Regeln; Plan-Archive werden
**gelöscht**, nie committet.

## Runde 5 — Browser-Abnahme läuft (2026-11-08)

**Pauls Grundsatz (2026-11-08, dauerhaft):** wie Paul Dinge „im Prinzip" entschieden hat, steht im
**Eclipse-Plugin** — vor jeder Paritäts-/SOLL-Frage zuerst dort nachschauen (chat.html, Plugin-Dialoge),
nicht neu fragen.

**Paul antwortet auf JEDE Nachricht nur EINE Frage** (ausdrücklich angemahnt). Look-Abnahme am Browser
lief als Feedback-Liste: **A1–A8 = Reparaturen** (1 Warte-Rahmen gestrichelt → „ganz weg", 2 Sider-Rand,
3/4/5 Chat-Fläche füllt Bildschirm nicht, Margen asymmetrisch, 6 Scroll-to-bottom unzuverlässig — nach
Renderen ans Ende, 7 Tool-Results nach Restore weg — wiederherstellen, prüfen ob Core-History sie
speichert, sonst Core-Proposal, 8 Agenten-Blick = anderes Widget → C-7), **B1–B6 = neues SOLL**
(1 Blasen volle Breite, Höhe = Text → R-WP-29 wird umgeschrieben, 2 Input unten weiß/volle Breite,
Senden rechts, 3 AntD-Widgets überall, 4 wiederverwendbare Widget-Klassen, 5 Settings-Seite weit weg
vom Plugin-Look → in die Config-Story, 6 AGENTS-DEV → später Projektskill „backend/frontend bauen",
⏳ offen lassen). **Test-Politik Paul: neue Tests nur für neue Funktionalität — sonst Tests abbauen
bzw. zusammenfassen.**

**Entschieden 2026-11-08 (Abnahme, laufend):** Top-Menü bleibt (Projekte · Chat · Settings, R-WP-43). **Sider-Baum neu:
[R-WP-44/UC-WP-78](ui.md) ❌** — Klapp-Gruppe je Projekt (Titel = Projektname), Kinder = Chats/Einheiten
(Jon; Paco später; Files als letztes), **kein „Projects"-Eintrag** (doppelt), leerer Sider = leer,
Einklap-Griff (`MenuUnfoldOutlined`/`MenuFoldOutlined`, Slide). Klick auf Chat-Eintrag öffnet Adresse,
Gruppen-Titel klappt nur. „Einheiten nur unter dem geöffneten Projekt" und „kein Jon-Eintrag"
zurückgenommen; „Chat" vs. „Jon" als Label egal („nicht ums Wording, sondern das Design"). R-WP-38/70/77,
open-points (🔒 Sider-Baum, 🔒 „Jon muss weg"), index.md nachgezogen.
**Warte-Rahmen gelöscht** (Paul 2026-11-08: „zwei Indikatoren"): R-WP-31/UC-WP-65 ❌ neu gefasst —
Arbeit zeigt der **Status-Indikator der Kachel-Reihe**, **R-WP-45/UC-WP-79 ❌** (blau arbeitet · gelb
kompaktiert · grün, größerer Kreis, bereit), Reihe **ohne Abstand über dem Chat**; falls doch eine
Blasen-Kennzeichung: AntD **`BorderBeam`** (Paul-Link), nie ein eigener gestrichelter Rahmen.
**Blasen: volle Breite, Höhe = Text** → R-WP-29 ❌ nachgetragen. Timeline-Lade-Punkt etwas größer
(R-WP-40). F3 → 🔒 (R-WP-45). Neue offene Punkte Runde 5: Status-Farbe bei **Fehler = rot** (🔒
2026-11-08, AntD-Fehler-/Alert-Widget falls vorhanden), A7
Tool-Results-Restore (⏳, erst prüfen ob Core-History sie trägt — sonst Core-Proposal).
**R-WP-46/UC-WP-81 ❌ (ui.md):** AntD zuerst — Widget-vor-eigenbau; UI-Funktionen immer als
**wiederverwendbare Widgets** bauen und nutzen (unabhängig testbar, kein zweiter Anzeige-Pfad).

**Scroll entschieden (A6, Paul 2026-11-08):** Auto-Scroll nur am Ende ✅, nach eigener Eingabe immer,
Zurück ans Ende bewaffnet neu, schwebender „Scroll bottom"-Knopf (`FloatButton`, umgekehrtes
`BackTop`) — **R-WP-35/UC-WP-80 ❌**; Chat-Fläche volle Bildschirmbreite bei behaltenem Rand,
Eingabe weiß/volle Breite, Senden rechts (in R-WP-35 ❌-Anteil). Lade-Punkt Timeline = AntD
`Spin`/`LoadingOutlined` small (R-WP-40), Spin auch am Kachel-Status (R-WP-45).

**Offen (je EINE Frage an Paul, Reihenfolge):** — ALLE ❓ der Abnahme geklärt (Stand 2026-11-08):
Modell/Kontext im Chat = später 🔒; in der **Config** MVP: Modell-Feld = Auswahl ODER Eingabe
(AntD `AutoComplete`, keine Dropdown-Only-Liste — nicht jeder Provider hat eine Liste; F8 ⏳).
Nächster Schritt: **Inkrement 4 planen** — Sider-Baum R-WP-44, Look-Reparaturen (Sider-Rand A2,
Fläche/Margen A3–A5), Warte-Rahmen raus + Status-Indikator R-WP-31/45, Scroll R-WP-35/UC-WP-80,
Blasen R-WP-29. Danach A7 Restore (Core prüfen!), C-7, Config-Story.
**Mehrere Chats: NICHT im MVP** (Paul 2026-11-08) — PR-5 hält, „es ging mir ums Layout"; auch mit
Paco bleibt es zunächst bei einem Chat je Agent. **Sider-Voreinstellung:** < 4 Projekte ⇒ alle
Gruppen offen, sonst nur die erste (in R-WP-44/UC-WP-78 nachgetragen).

## Runde 4b — Look-Restart (2026-11-07)

Paul hat `web-factory-mvp-2` in **main gemergt** (`542d9f4`, Branch remote gelöscht) — alles Gebaute
ist auf main; andere Branches (`web-factory-mvp`, `web-factory-goon`) sind Vorgänger und drin. Worktree = main.

Pauls Befund: IST ≈ altes Design — **Top-Menü weg, Text-Links statt Buttons, Mockup/Referenz nie
übernommen**. Konflikt gelöst: die R-WP-38-Fassung „Sider statt Top-Menü" (2026-11-06) ist
zurückgenommen. **[R-WP-43](ui.md) ❌** (Restart, Mockup + `ai-schulung`): dunkles Top-Menü
(Projekte · Chat · Settings), Sider-Einheiten **Chat · Paco · Files als letztes**, Chat-Eingabe =
Footer-Zeile, **Aktionen = echte Buttons/Selects** (auch Clear/Compact an den Kacheln). Look-Absatz in
[ui.md](ui.md) neu: AntD-6-Elemente modern, Referenz-Muster in **AntD-6-Pendants** (nie antd-5-Code
kopieren), alte chat.html-Optik **kein Ziel mehr**, Farben bleiben unsere Tokens. **UC-WP-76/77 ❌**.
[index.md](index.md): UI → ◐. Offen für Paul: mehrere Chats je Agent (❓, widerspricht PR-5),
Modell-Auswahl + Kontextanzeige (❓ MVP?), Chat-Optionen-Platzierung (⏳), Sprechblasen/Zeit unten
(⏳ später), Top-Menü-Einträge + Ordnung (⏳) — siehe open-points „Runde 4b".

**Inkrement 1 gebaut + reviewed (N1) + geflippt ✅:** Branch **`web-factory`** (ab main `542d9f4`,
`web-factory-look` gelöscht): `42b08c02` (1a Top-Menü) → `d759ce7d` (1b Einheiten) → `99ffb67d`
(N1: Nachweise) → `d666836e` (Findings-File, jetzt **getrackt**). Stand: **224 Tests / 22 Dateien**
grün, tsc 0, oxlint 0/0. Review-Verdict war REJECTED (2 Nachweis-Lücken: `ant-menu-dark/-horizontal`
unkalibriert, Turn-Test startete adressgleich) — N1 geschlossen, 2 Proben rot. `R-WP-43/UC-WP-76/77 ✅`
(Verdrahtung; **Look-Abnahme am Browser steht aus** — Band, Menühöhe, Hell/Dunkel `#1f2328`/`#101316`,
Auswahl-Kontrast, `border-bottom`, LOW-2/LOW-4, 2 Suspicions; Details in
`peon-plan/review-findings.md`). Settings-Button rechts **entfernt** (Q1), kein Paco-Eintrag (Q2),
Marke an der Chat-Einheit (Q3), dunkles Band (Q4).

**Pauls Abnahme 2026-11-07 (nur dunkel, Sider defekt, kein Umschalter) ⇒ Zweite SOLL-Wende:**
**Design-Übernahme** — Palette/Layout/Element-Optik werden aus `ai-schulung/app/frontend`
(`App.css`/`index.css`) + Mockup **kopiert, nicht nachgebaut** (Look-Absatz + [R-WP-39](ui.md) ❌ neu);
Sider mit **fester Breite 200** (`Menu inline`, Mockup); Referenz-Defekte **nicht** mitkopieren
(Select überlappt Top-Menü = CSS-Fehler dort); Zweifelsfall = `chat.html` des Plugins; **manueller
Hell/Dunkel-Umschalter** (OS = Startwert, überlebt Reload). **R-WP-29 geöffnet:** Verlauf folgt der
Referenz-Optik (Bubbles) — flache Zeilen zurückgenommen (⏳). **Design-Tests-Regel:** für Look
**keine neuen Tests**, im Weg = stabilisieren/löschen (Token-Paritäts-/Band-Assertions fliegen),
Abnahme am Browser. Vorherige Fassungen („Farb-Tokens bleiben unsere", „kein zentrierter Container")
sind zurückgenommen.

**Nächster Schritt:** ~~Inkrement 2~~ **Inkrement 2 GEBAUT + reviewed + geflippt (2026-11-07):** Branch
`web-factory` — 2a `193a22e8` (Palette+Rahmen) → 2b `bd69c101` (Verlauf/Bubbles) → 2c `1b213920`
(Umschalter) → N1 `71bcaef6` → LOW `2b99bcc4` → Close-out `f8aa65b2`. **R-WP-39 ✅, Bubbles 🔒**, R-WP-29
❌ bis Look-Abnahme. **Inkrement 3 (Nachtzyklus) GEBAUT + reviewed:** 3a `9ef506ec` (Maße ant-design.md +
grauer Warte-Rahmen) → 3b `95e55fdb` (AntD-Timeline-Widget Werkzeug+Delegation, 22 Fälle umgehängt,
Mutationen bewiesen) → 3c `c96e6965` (**Think = Item wie Tool**, 218/218) → N1 `71a8866e` (Review-Fix:
Steps-Geometrie im Chat-Scope überschrieben — AntD liest `itemPaddingBottom` nur im Alternativ-Layout;
TraceEntry-Helper; 4px-Grid). Review-Verdict nach N1 akzeptiert. **R-WP-40 ✅** (mit Geometrie-Klausel),
**UC-WP-72 ✅ mit Think-GIVEN**. Browser-Abnahme steht aus: Blasen-Maße, grauer Rahmen, appweit 14 px
(❓2 — Blatt-Konsequenz, Rücknahme auf `.chat-log` wäre eine Zeile), Timeline-Dots, Think-Slot an (❓1/F5).

**Danach:** Sider-Inkrement (kein „Projects"-Eintrag, kein Files-Slide-out — open-points Runde 4b ⏳),
Chat-Aktionen-Story (R-WP-43 Buttons), Config-Story (reasoning_effort, Save-Button, Key-Delete —
`aifactory/ui-bugs.md`). Plan archiviert.

Übernehmbare Muster aus `ai-schulung` (Survey 2026-11-07): dunkles horizontales Menü
(`app/frontend/src/App.tsx`), Sidebar-Collapse je Agent + Hover-Aktionen (`ThreadNav.tsx`),
Modell-`Select` mit Label (`ModelDropdown.tsx`), Steps + Collapse-ghost für Tools
(`ToolCallSteps.tsx`/`ToolResultCard.tsx`), Badge+Popover (`AgentToolsBadge.tsx`), FloatButton +
Drop-Zone, fixe Bottom-Bar mit TextArea+primärem Button. Farben der Referenz **nicht** übernehmen
(unere Tokens gelten).


## Zyklus 2026-11-06/07 — Runde 4: AntD-Umbau („durchbauen"; Paul inzwischen wieder da, parallel im Core)

Branch `web-factory-mvp-2`, Spitze `2ec9d4f5` = origin. Kette Inc 4: `832a9772` (4a) → `b51571b2` (N1)
→ `6249d59a` (N2) → `eb103276` (4b) → `9704013a` → `98f55700` (4c) → `8da14901` (N1) → `a0a2c3f2`
(N2 + Docs-Flip) → `5cfd251e` (memory). Inc 5a: `c04f8ed1` (Sider + Docs) → `a32409ad` (URL = Name) →
`87949a90` (Cleanup) → `680f2239` (5a-N1). Inc 5b: `b4c202b0` (5b-1) → `6578719d` (N1 Docs) →
`83a63547` (N1 Code) → `583719d0` (Jons Docs) → **`2ec9d4f5` (5b-2)**.

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
**G2 (Einheit Files) kam mit 5b-1** — nach dessen N1-Delta ist der Flip erfolgt:
**R-WP-38/UC-WP-70 ✅**, Feature **UI ✅** in der [index.md](index.md) (offen bleibt nur der Look am
Browser).
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

**Inkrement 5b-1-N1 — nur Nachweis + Kommentar** (Docs `6578719d`, Code `83a63547`): DOM-Assertion
`expect(tab.previousElementSibling).toBe(panel)` in `filesTabSitsOnThePanelTop` — Mutation „Fläche hinter
den Tab" ist damit **rot**; Kommentar in `app.routes.tsx` sagt jetzt **Grammatik, nicht Mount**, und das
Architektur-Doc hält den gemessenen Befund (eine **Schwesterroute** remountet den Chat **nicht**,
React Router 7.18.5 — Mount-Kontinuität beweist nur ein erzwungener Remount).

**Inkrement 5b-2 — Aufräumen, abgenommen ohne zweite Review-Runde** (`2ec9d4f5`): Chat löst seinen Namen
aus der Rahmen-Lesung (`useAppContext`), **zweiter Registry-Read weg**; `loaded` = „Antwort da, **auch eine
abgelehnte**" — sonst hinge ein Deep-Link für immer in der Ladezeile (beides neu gepinnt:
`aChatReadsTheProjectListOnce`, `aRefusedProjectListShowsTheListNotALoadingLine`, je eine Mutation ⇒ rot).
Rahmen-Test `theFrameShowsTheProjectTree` nach `test/app/app.shell.test.tsx` **verschoben** (`// UC-WP-1`
wanderte mit), Kommentar-Prosa in `app.sider.tsx`/`project.list.ts`/`last.project.ts` auf Regel-IDs
gekürzt — WHY blieb (`collapsed` nie controlled, `matchMedia` beim Mount, Segment = Name).

**Gemessen (Spitze `2ec9d4f5`):** UI **216 Tests / 22 Dateien**, `tsc -b` 0, oxlint 0/0 (73 Dateien),
`AGENTS-DEV.md` 120 Zeilen · Backend **227/0** (in 5b nicht gebaut) · Doc-Lint **28** Findings = nur
Fremd-Docs (PC 14, CR 14), kein `UNBELEGT_ERLEDIGT`. Dev/Review scoppen enger — **immer selbst messen.**

**Nächste Schritte:** AntD-Umbau ist **durch** (Inc 1–5b ✅, Plan archiviert). Weiter im Backlog:
**C-7** (Agent-Blick read-only live im gleichen Chat-Widget — neuer Plan nötig), dann C-2a,
C-1 TurnClaim-Timeout (`ProjectRuntime.java:348`), F8/F10, Mini 3c (`data:`-Bild ⇒ Alt statt leerem
`src`). **Look-Abnahme am Browser steht aus** (Pixel, dunkel, Sider-Breite — open-points #3/#6).
Stagehand ⏳ parked (Node ≥22.18 vs. Bau 22.12). `peon-plan/review-findings.md` ist **ungetrackt**
(siehe #3).

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

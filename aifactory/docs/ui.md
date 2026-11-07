---
idPrefix: WP
---
# UI — Rahmen der App

> Navigation, Adressen, Auslieferung und Look. Was auf einer Seite passiert, steht im Feature-Doc der Seite:
> [Projekte](project.md) · [Jon-Chat](chat.md) · [Paco](paco.md) · [Config](config.md).

## Ziel

Eine schlanke Browser-App mit **Top-Menü und Projektbaum im Sider** ([R-WP-43](ui.md),
[R-WP-38](ui.md)), die unter `/ui/` ausgeliefert wird und einen Reload übersteht.

**Look — Design-Übernahme (Paul 2026-11-07: „übernimm das Design aus der ai-schulung bzw. dem Mockup" — ersetzt die Fassung „Muster übernehmen, Farben eigene"):**
Basis bleibt **AntD 6** — Palette, Layout und Element-Optik werden aus der Referenz
`ai-schulung/app/frontend` (`App.css`/`index.css` + Komponenten-Struktur) und dem Mockup
(`factory-ui-mockup.tsx`) **übernommen, nicht nachgebaut**: dunkles Top-Band mit Menü, **Sider mit
fester Breite** (200 px, `Menu mode="inline"` — der Seitenmenü-Defekt der bisherigen Fassung ist damit
gestellt), Chat-Fläche zentriert über `--content-max`, Element-Optik wie dort. **Defekte der Referenz
werden nicht mitkopiert** — bekannt: das `Select`, das das Top-Menü überlappt (CSS-Fehler). Was die
Referenz nicht löst, wird eigen gebaut (wie das Tool-Zeit-Ding dort, nicht final) — bei Zweifeln
entscheidet die **Referenz-Optik** (hell); das `chat.html` des Plugins bleibt nur Herkunfts-Hinweis
für Labels und Wortlaut. **Hell/Dunkel:** ein manueller
**Umschalter** im Header (Paul: „dark/light mode … würde ich aber nehmen") — die helle Ansicht ist das
Referenz-Design, die dunkle wird daraus abgeleitet; Startwert ist die Betriebssystem-Vorliebe, die
Auswahl überlebt den Reload. **Tests:** Design-Änderungen ändern **keine** UI-Tests — für Look
gibt es **keine neuen Tests**; Tests, die einer Übernahme im Weg stehen, werden **stabilisiert oder
gelöscht** ([R-WP-39](ui.md)). Abgenommen wird **am Browser** — jsdom beweist nur Verhalten
(Adressen, Queue, Mount), nie Optik.
## Regeln

### R-WP-1 — Seiten, stabile Adressen, Reload ✅ (geschärft Paul 2026-11-02; Rahmen 2026-11-07 nach [R-WP-43](ui.md))

Die App erreicht **genau diese Seiten**: global *Projekte* und *Settings* (Config,
[R-WP-16](config.md)), je Projekt den *Jon-Chat* und den Datei-Bereich; die *Paco*-Seite kommt mit
seiner Story ([Paco](paco.md)). Wie diese Seiten erreichbar sind, regeln [R-WP-43](ui.md)
(Top-Menü) und [R-WP-38](ui.md) (Projektbaum im Sider) — die Fassung „Sider **statt** Top-Menü"
(2026-11-06) hat Paul am 2026-11-07 zugunsten des Mockup-Rahmens (Top-Menü **und** Sider)
zurückgenommen.

**URL-stabil:** jede Navigation (Projekt öffnen, Einheit wählen, View wechseln) ändert die URL
(**Aktion → URL → UI-Change**) — kein rein funktionierender `useState`-Zustand; ein Reload bleibt auf
der Seite (UC-WP-43). Die Adresse `/ui/chat` **ohne** Projekt führt auf **Projekte**; war zuletzt ein
Projekt aktiv, öffnet sich dessen Chat (UC-WP-51) — der Menüeintrag „Chat" führt dorthin
([R-WP-43](ui.md)).

#### UC-WP-1 — pagesOfTheMvpApp ✅ (früher „showsExactlyTheseFourPages", umgebaut Paul 2026-11-02)
- GIVEN die App WHEN der Nutzer navigiert THEN erreicht er global *Projekte* und *Settings* (Config,
  [R-WP-16](config.md)), je Projekt den *Jon-Chat* — keine weiteren Seiten im MVP; Datei-Bereich und
  Team-Kacheln liegen im Jon-Chat. Die *Paco*-Seite kommt mit der Paco-Story ([Paco](paco.md)).

#### UC-WP-43 — reloadStaysOnThePage ✅
- GIVEN der Nutzer ist im Jon-Chat eines Projekts WHEN er den Browser neu lädt THEN ist er wieder im
  Jon-Chat desselben Projekts, mit vollständigem Verlauf.
- GIVEN eine unbekannte Adresse unter `/ui/` WHEN sie aufgerufen wird THEN kommt die App
  (`index.html`) — kein `500`, keine Weiterleitungsschleife, und `/ui/assets/**` bleibt davon
  unberührt.

#### UC-WP-51 — chatOpensLastActiveProject ✅
- GIVEN mehrere Projekte WHEN `/ui/chat` **ohne** Projekt geöffnet wird (Adresse, Lesezeichen,
  Weiterleitung oder der Menüeintrag „Chat", [R-WP-43](ui.md)) THEN öffnet der Chat des
  zuletzt aktiven Projekts; ohne zuletzt aktives Projekt führt die Adresse auf die Projektliste.

#### UC-WP-52 — everyNavigationChangesTheUrl ✅
- GIVEN jede Navigation (Projekt öffnen, View wechseln) WHEN der Nutzer sie auslöst THEN ändert sich
  die URL entsprechend, und der direkte Aufruf dieser URL zeigt denselben Stand — kein Zustand nur
  im `useState`.

### R-WP-22 — Die App wird unter `/ui/` ausgeliefert ✅

Die Factory ist ein Browser-Produkt: wer `http://<host>/ui/` öffnet, sieht die App. `index.html` und
die gebauten Assets (JS, CSS mit Hash im Namen) kommen als das an, was sie sind — `/api/**` bleibt
davon unberührt, und `/` führt nach `/ui/`. Eine Adresse, die niemand bedienen kann, ist ein Fehler
und keine Weiterleitung: kein Request darf in einer Schleife aus Weiterleitungen enden.

#### UC-WP-34 — indexHtmlServedAtUiRoot ✅
- GIVEN der laufende Server WHEN `GET /ui/` oder `GET /ui` THEN kommt `200` mit `text/html` und dem
  Inhalt der gebauten `index.html`; `GET /` leitet nach `/ui/` weiter.

#### UC-WP-35 — hashedAssetsServedAsThemselves ✅
- GIVEN die gebauten Assets liegen im Klassenpfad WHEN `GET /ui/assets/<name>.js` oder
  `/ui/assets/<name>.css` THEN kommt `200` mit dem Dateiinhalt und dem passenden `Content-Type`
  (`text/javascript` bzw. `text/css`) — nie JSON, nie `text/plain`, kein `500`.

### R-WP-27 — Englische UI, Strings im Catalog ✅

Die UI ist **englischsprachig**, und sichtbare UI-Texte stehen **nicht** im Komponenten-Code — sie
kommen aus einem String-Catalog (`content.ts` bzw. Locale-Dateien), damit Tests und spätere i18n sie
adressieren können (Befund Paul 2026-11-02; [AGENTS.md](../AGENTS.md): „UI: English").

#### UC-WP-53 — uiTextsComeFromTheCatalog ✅
- GIVEN die App WHEN sie gerendert wird THEN ist die Oberfläche englisch und kein sichtbarer UI-Text
  ist im Komponenten-Code hart verdrahtet — er kommt aus dem String-Catalog.

### R-WP-38 — Navigation: Projektbaum im Sider, Einheiten je Projekt ✅ (Paul 2026-11-06, AntD-Umbau; gebaut Inkremente 5a und 5b, 2026-11-07; Header und Einheiten-Ordnung seit 2026-11-07 in [R-WP-43](ui.md))

Die Navigation besteht aus dem **Top-Menü im Header** ([R-WP-43](ui.md)) und einem **Sider**
(`Layout.Sider`) links mit einem Eintrag **je Projekt**. **Sein erster Eintrag ist „Projects"** — die
Projektliste, in der Projekte **hinzugefügt und entfernt** werden ([R-WP-2](project.md)); er ist
keine weitere Seite, sondern die bestehende Projekte-Seite — im Sider wie im Top-Menü.
**Ein Klick auf das Projekt öffnet seinen Chat** — es gibt **keinen eigenen „Jon"-Eintrag** und kein „Jon"-Label
in der Navigation (Paul 2026-11-06: „Jon … muss weg"; der Agent heißt in den Docs weiter Jon, und
seine Kachel behält den Namen, [R-WP-7](chat.md)). Unter dem Projekt stehen seine **Einheiten** —
die Bereiche eines Projekts mit eigener Adresse; die vollständige Ordnung nennt
[R-WP-43](ui.md). Einheiten zeigt der Sider **nur
unter dem Projekt, das die Adresse nennt** — ein Projekt, das nicht offen ist, hat keine eigene
Einheits-Adresse. Der **Header** trägt Marke, gewählten Nutzer und die globale Navigation — geregelt
in [R-WP-43](ui.md). Eine **Breadcrumb-Zeile gibt es nicht**: der Sider sagt, wo man ist. Die
gewählte Einheit ist
im Sider markiert, und Sider-Markierung und URL sind einig (R-WP-1). Der **Datei-Bereich ist eine eigene
Adresse je Projekt**: sein Auf- und Zuklappen **ist** der Adresswechsel — es gibt keinen zweiten
Panel-Zustand, und der „Files"-Tab im Chat ([R-WP-33](files.md)) wie der Sider-Eintrag sind zwei Griffe
auf dieselbe Adresse. Die Chat-Seite bleibt dabei **dieselbe Instanz**: ein laufender Turn endet nicht am
Klick auf eine Einheit, und der Chat bleibt neben dem Panel sichtbar ([R-WP-33](files.md)). Fällt das
Fenster unter den Sider-Breakpoint, **klappt der Sider** und die Chat-Fläche nutzt die Breite; die
Auswahl bleibt über
den ausgeklappten Sider erreichbar. Die **Chat-Eingabe** gehört zur **Chat-Spalte** (dockt unten
innerhalb ihrer Spalte, [R-WP-35](chat.md)) — sie läuft nicht unter dem Sider durch, der Sider läuft
in voller Höhe daneben. Für die Markierung **und** für die Frage, welches Projekt die Adresse nennt, gilt
**ein einziger Lesevorgang**: die Antwort kommt aus der Liste, die der Rahmen gelesen hat — die App fragt
die Registry nicht zweimal. Ist diese Lesung **abgelehnt**, ist sie trotzdem zu Ende: der Nutzer sieht die
Projektliste mit dem Grund, nie eine Ladezeile, die auf eine Antwort wartet, die schon da war.

#### UC-WP-70 — projectTreeAndUnitEntries ✅
- GIVEN die App WHEN sie öffnet THEN zeigt der Sider **zuerst „Projects"** und darunter **ein Eintrag je
  Projekt**, ohne „Jon"-Eintrag und ohne „Jon"-Label; ein Klick auf das Projekt öffnet dessen Chat, ein
  Klick auf „Projects" die Projektliste (Hinzufügen, Entfernen) — Markierung und URL einig.
- GIVEN ein geöffnetes Projekt WHEN der Nutzer die Einheit **Files** wählt THEN wechseln
  Sider-Markierung und URL einig auf dieselbe Einheit, der Chat bleibt daneben sichtbar, und ein Reload
  landet wieder dort; ein laufender Turn endet nicht an diesem Klick.
- GIVEN ein Fenster unter dem Sider-Breakpoint WHEN die App zeichnet THEN ist der Sider auf **Breite 0**
  eingeklappt und die Chat-Fläche füllt die Zeile allein; über den ausgeklappten Sider bleibt die
  Auswahl erreichbar — **auch „Projects"**. Der Beleg nennt die Breite des Siders (0) **und** den
  ausgeklappten Griff; das Nachmessen der Pixel gehört zur Look-Abnahme am Browser (Look-Absatz oben).

### R-WP-39 — Design-Übernahme: die Referenz ist das Design ✅ (gebaut + reviewed 2026-11-07, Inkrement 2 auf `web-factory`; Look-Abnahme am Browser steht aus — Paul 2026-11-07: „übernimm das Design"; ersetzt die Fassung „Muster übernehmen, Farben eigene" von 2026-11-06)

Basis bleibt **Ant Design** — und **Bootstrap verlässt die App vollständig** (die Wache bleibt: kein
Bootstrap-CSS, keine `react-bootstrap`-Komponente, keine `bootstrap-icons`; zwei CSS-Basen nebeneinander
sind verboten). Das **Design selbst wird übernommen** (Look-Absatz oben): Farben und Layout kommen aus
der Referenz `ai-schulung/app/frontend` bzw. dem Mockup — die frühere Fassung „Farb-Tokens bleiben
unsere" ist damit zurückgenommen, die Dunkel-Tabelle des Grundblatts wird abgelöst (hell = Referenz,
dunkel = Ableitung). Die Token-Paritäts-Tests der alten Fassung sind **im Weg** und werden
**gelöscht** (Paul 2026-11-07: „keine Tests anlegen, Tests die im Weg sind stabilisieren oder
löschen" — Design-Änderungen ändern keine UI-Tests, die Abnahme ist am Browser). Was weitergilt: das
Zustandsmodell bleibt unser (Queue, Live-Anker, Bestätigung — [R-WP-37](chat.md),
[AF-ADR-003](adr/003-reference-ui-is-a-donor-not-a-source.md)), sichtbare Texte kommen aus dem
String-Catalog ([R-WP-27](ui.md)), ein Weg je Verantwortung.

#### UC-WP-71 — oneDesignBasisOwnChatCss ✅ (geschärft Paul 2026-11-07: die Bootstrap-Wache bleibt, die Look-Fragen gehen an den Browser)
- GIVEN die gebaute App WHEN das Bündel entsteht THEN enthält es kein Bootstrap-CSS und keine
  `react-bootstrap`-Komponente; GIVEN denselben Fehler ein zweites Mal WHEN die Wache prüft THEN ist
  die Suite rot — die Wache sieht den CSS-Import (`import 'bootstrap/…'`) ebenso wie die Komponente
  und prüft das Manifest **vollständig** (`dependencies` und `devDependencies`).
- GIVEN der Nutzer stellt den **Hell/Dunkel-Umschalter** um WHEN die App zeichnet THEN wechselt das
  Dokument zwischen `data-theme="light"` und `data-theme="dark"`, die Auswahl überlebt den Reload, und
  der Startwert ist die Betriebssystem-Vorliebe — der jsdom-Anteil (Auswahl schlägt die OS-Antwort,
  Startwert wird nie zurückgeschrieben, Klick schreibt die Auswahl) sitzt im bestehenden
  `appRootSetsTheDocumentTheme`, Klick und Reload am Browser abnehmen (Design-Tests-Regel oben).

### R-WP-43 — Rahmen: Top-Menü, Einheiten im Sider, Aktionen als Elemente ✅ (Paul 2026-11-07 — Mockup `factory-ui-mockup.tsx` + Referenz `ai-schulung`; gebaut als Inkrement 1a/1b+N1 auf `web-factory`, 2026-11-07 — die Verdrahtung ist bewiesen, der Pinselstrich wird am Browser abgenommen)

Der **Header** trägt Marke, das horizontale **Top-Menü** (AntD `Menu`, `mode="horizontal"`, dunkel —
wie die erste Version der App und wie im Mockup), den gewählten Nutzernamen und **Settings**. Die
Menü-Einträge sind die globalen Seiten **Projekte · Chat · Settings** (Config, [R-WP-16](config.md)):
„Chat" öffnet den Chat des zuletzt aktiven Projekts (UC-WP-51), „Projekte" die Projektliste. Die
**Markierung im Menü folgt der Adresse** ([R-WP-1](ui.md)) — Menü und Sider markieren dieselbe
Adresse einig.

Der **Sider** bleibt der Projektbaum ([R-WP-38](ui.md)); unter einem geöffneten Projekt stehen dessen
**Einheiten in dieser Ordnung: Chat, Paco (sobald dessen Story gebaut ist), Files als letztes**. Der
Chat-Eintrag und der Klick auf das Projekt sind **zwei Griffe auf dieselbe Adresse**
([R-WP-38](ui.md)) — ein laufender Turn endet an keinem von beiden. Die **Chat-Eingabe** bleibt die
untere Zeile der Chat-Spalte ([R-WP-35](chat.md)) — sie ist die Footer-Zeile des Mockups.
**Aktionen** (Clear, Compact, Senden, …) sind **echte AntD-Elemente** — `Button` statt nacktem
Text-Link, `Select`/`Dropdown` statt Textliste — auch an den Kacheln ([R-WP-7](chat.md)); die
Chat-Optionen (Clear/Compact je Chat) sind ein eigener Punkt (⏳ [open-points](open-points.md)).

#### UC-WP-76 — topMenuNavigates ✅ (Verdrahtung bewiesen, Look am Browser abzunehmen)
- GIVEN die App WHEN sie zeichnet THEN trägt der Header ein dunkles horizontales Menü mit genau
  **Projekte · Chat · Settings**.
- GIVEN der Nutzer wählt „Chat" WHEN kein Projekt aktiv ist THEN öffnet sich die Projektliste; mit
  zuletzt aktivem Projekt dessen Chat (UC-WP-51).
- GIVEN die Adresse wechselt WHEN der Rahmen zeichnet THEN folgt die Menü-Markierung der Adresse —
  Menü- und Sider-Markierung sind einig.

#### UC-WP-77 — unitsInTheProjectTree ✅ (Verdrahtung bewiesen, Look am Browser abzunehmen)
- GIVEN ein geöffnetes Projekt WHEN der Sider zeichnet THEN stehen unter dem Projekt in dieser
  Ordnung **Chat**, **Paco** (sobald gebaut) und **Files** als letztes; der Chat-Eintrag und der
  Projekt-Klick öffnen dieselbe Adresse, genau ein Eintrag ist markiert, und ein Reload landet wieder
  dort.
- GIVEN der Nutzer wählt im Sider die Einheit Chat WHEN ein Turn läuft THEN endet der Turn nicht
  (UC-WP-70, GIVEN 2).

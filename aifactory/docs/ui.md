---
idPrefix: WP
---
# UI — Rahmen der App

> Navigation, Adressen, Auslieferung und Look. Was auf einer Seite passiert, steht im Feature-Doc der Seite:
> [Projekte](project.md) · [Jon-Chat](chat.md) · [Paco](paco.md) · [Config](config.md).

## Ziel

Eine schlanke Browser-App mit **Top-Menü und Projektbaum im Sider** ([R-WP-43](ui.md),
[R-WP-38](ui.md)), die unter `/ui/` ausgeliefert wird und einen Reload übersteht.

**Look — AntD 6 als Basis, Rahmen nach Mockup (Restart Paul 2026-11-07; zuvor AntD-Basis, Paul 2026-11-06, davor Bootstrap):**
Basis ist **Ant Design 6** mit Theme-Tokens auf unsere Farben — Details und Abgrenzung in
[R-WP-39](ui.md). Der **Rahmen** folgt dem Mockup (`factory-ui-mockup.tsx`) und der Referenz
`ai-schulung`: **dunkles horizontales Top-Menü** und **Projektbaum im Sider** ([R-WP-43](ui.md)),
die Chat-Eingabe als untere Zeile ([R-WP-35](chat.md)). **Aktionen sind echte Buttons, Auswahlen
echte Selects/Dropdowns** — kein nachgebauter Text-Link (auch Clear/Compact an den Kacheln,
[R-WP-7](chat.md)). Von der Referenz `ai-schulung` werden **Muster und Widgets** übernommen (Steps,
Badge+Popover, Card, FloatButton), **nie ihr Zustandsmodell** (Queue/Anker/Bestätigung bleiben die
unseren, [R-WP-37](chat.md), [AF-ADR-003](adr/003-reference-ui-is-a-donor-not-a-source.md)) — und in
den **neueren AntD-6-Pendants**, nicht als Kopie ihres antd-5-Codes. Die Farb-Tokens bleiben unsere:
Nutzer-Zeile blau (`#0969da`, dunkel `#58a6ff`), Agent-Zeile grün (`#1a7f37`, dunkel `#7ee787`),
**Akzent beider Stimmen links** ([R-WP-29](chat.md)) — die kompakte Optik der alten `chat.html` ist
**kein Ziel mehr**; Sprechblasen und die Zeit unter der Zeile sind ein späterer Wunsch
(⏳ [open-points](open-points.md)). Der Chat nutzt die **volle Fensterbreite abzüglich eines kleinen
Randes** — kein zentrierter Container, keine statischen Seitenränder, keine Karte um die
Hauptfläche. Die **Config-Seite** bleibt Card-Struktur (AntD `Card`, Rand `--border`, Radius,
Padding, kein Hintergrund); Eingaben tragen die **Maße der Chat-Eingabe**, Statuszeilen (Key-Status)
**Mono**. Jedes Look-Inkrement wird **am Browser abgenommen** (Pixel, Dunkel, Elemente) — jsdom
beweist Verdrahtung, nie Optik.

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

### R-WP-39 — Eine Design-Basis: AntD, Chat-Zeilen eigenes CSS ✅ (Paul 2026-11-06)

Basis der Oberfläche ist **Ant Design** — Layout, Sider, Menu, Input, Button, Card, Form, Select,
Timeline, Modal/Popconfirm, Alert — mit Theme-Tokens auf unsere Farben (Look oben). **Bootstrap
verlässt die App vollständig**: kein Bootstrap-CSS, keine `react-bootstrap`-Komponente, keine
`bootstrap-icons` im Bündel — zwei CSS-Basen nebeneinander sind verboten; ein Import von
Bootstrap-**CSS** ist dieselbe Verletzung wie ein Import ihrer Komponenten. Die **Chat-Zeilen**
(Verlauf, Werkzeug-Zeilen, Diffs, Think) behalten **eigenes CSS** in unserer Optik
([R-WP-29](chat.md)): Akzent links, flache kompakte Zeilen, keine AntD-Karte um den Verlauf, nicht
zentriert. Portiert werden **Muster und Widgets** der Referenz `ai-schulung`, **nie ihr Zustandsmodell**: Queue,
Live-Anker und Bestätigung bleiben die unseren ([R-WP-37](chat.md)). Je Muster gelten eigene Regel und
eigener Status — Markdown [R-WP-28](chat.md), Timeline [R-WP-40](chat.md), Eingabe [R-WP-41](chat.md);
**diese** Regel ist für sich erfüllt, wenn die Basis allein die Oberfläche malt und der Verlauf sein
eigenes CSS behält.

**Dunkel ist eine Tabelle.** Die App setzt `data-theme` am Dokument auf den Wert der
Betriebssystem-Vorliebe, und die Farb-Tokens der Basis stammen aus **derselben** Dunkel-Tabelle wie
das Grundblatt — zwei abweichende Dunkellooks sind ein Defekt. Und die Basis **ist** die Haut der
App: wo die App mountet, gilt ihr Theme; ein Ausschnitt, der ohne sie montiert, ist nicht die App.

#### UC-WP-71 — oneDesignBasisOwnChatCss ✅ (der Pinselstrich selbst wird im Smoke-Test abgenommen — belegt ist die Verdrahtung, der Test malt keine Farbe)
- GIVEN die gebaute App WHEN das Bündel entsteht THEN enthält es kein Bootstrap-CSS und keine
  `react-bootstrap`-Komponente; GIVEN denselben Fehler ein zweites Mal WHEN die Wache prüft THEN ist
  die Suite rot — die Wache sieht den CSS-Import (`import 'bootstrap/…'`) ebenso wie die Komponente
  und prüft das Manifest **vollständig** (`dependencies` und `devDependencies`).
- GIVEN die App mountet WHEN ein Element der Basis zeichnet THEN trägt es unsere Token-Farbe; ohne
  die Haut der App gilt keine unserer Farben.
- GIVEN das Betriebssystem dunkel wünscht WHEN die App mountet THEN trägt das Dokument
  `data-theme="dark"`, und **jedes** Farb-Feld der Basis stimmt mit der Dunkel-Tabelle des
  Grundblatts überein — nicht nur zwei Beispiele.
- GIVEN der Chat WHEN er den Verlauf zeichnet THEN tragen Nutzer-, Jon- und Werkzeug-Zeilen unsere
  Klassen und unsere Akzentlinie; AntD-Elemente stehen nur außerhalb des Verlaufs.

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

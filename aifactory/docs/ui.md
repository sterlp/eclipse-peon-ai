---
idPrefix: WP
---
# UI — Rahmen der App

> Navigation, Adressen, Auslieferung und Look. Was auf einer Seite passiert, steht im Feature-Doc der Seite:
> [Projekte](project.md) · [Jon-Chat](chat.md) · [Paco](paco.md) · [Config](config.md).

## Ziel

Eine schlanke Browser-App mit Top-Navigation, die unter `/ui/` ausgeliefert wird und einen Reload übersteht.

**Look — AntD als Basis, Chat-Zeilen eigenes Theme (Paul 2026-11-06, Umbau; zuvor Bootstrap, Paul 2026-11-02):**
Basis ist **Ant Design** mit Theme-Tokens auf unsere Farben — Details und Abgrenzung in
[R-WP-39](ui.md). Die Farben und die kompakte Optik bleiben die der alten `chat.html` des Plugins —
Nutzer-Zeile blau (`#0969da`, dunkel `#58a6ff`), Agent-Zeile grün (`#1a7f37`, dunkel `#7ee787`),
**Akzent beider Stimmen links** (keine Rechtsbündigkeit, kein Rahmen um den Chat — die Kante gehört
der Zeile), flache kompakte Zeilen statt Bubbles, Tool-/Think-Zeilen klein und grau. Der Chat nutzt
die **volle Fensterbreite abzüglich eines kleinen Randes** — kein zentrierter Container, keine
statischen Seitenränder, keine Karte um die Hauptfläche (Befund Paul am AntD-Mockup: der Rand der
Demo-Karte stört). Die **Config-Seite** bleibt Card-Struktur (AntD `Card`, Rand `--border`, Radius,
Padding, kein Hintergrund); Eingaben tragen die **Maße der Chat-Eingabe**, Statuszeilen (Key-Status)
**Mono**. Von der Referenz `ai-schulung` werden Muster und Widgets übernommen, **nie ihr
Zustandsmodell** (Queue/Anker/Bestätigung bleiben unsere, [R-WP-37](chat.md)). Die endgültige
Look-Abnahme erfolgt im Smoke-Test am Prototypen.

## Regeln

### R-WP-1 — Seiten, stabile Adressen, Reload ✅ (geschärft Paul 2026-11-02; Form der Navigation 2026-11-06 nach [R-WP-38](ui.md))

Die App erreicht **genau diese Seiten**: global *Projekte* und *Settings* (Config,
[R-WP-16](config.md)), je Projekt den *Jon-Chat* und den Datei-Bereich; die *Paco*-Seite kommt mit
seiner Story ([Paco](paco.md)). Wie diese Seiten erreichbar sind, regelt
[R-WP-38](ui.md) (Projektbaum im Sider) — die frühere Top-Navigation mit drei Einträgen und dem
Grundsatz „keine linke Sidebar" ist durch Pauls AntD-Entscheidung vom 2026-11-06 ersetzt.

**URL-stabil:** jede Navigation (Projekt öffnen, Einheit wählen, View wechseln) ändert die URL
(**Aktion → URL → UI-Change**) — kein rein funktionierender `useState`-Zustand; ein Reload bleibt auf
der Seite (UC-WP-43). „Chat" ohne gewähltes Projekt führt auf **Projekte**; geöffnet wird das
**zuletzt aktive Projekt** (UC-WP-51).

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
- GIVEN mehrere Projekte WHEN der Nutzer „Chat" klickt THEN öffnet sich der Chat des zuletzt aktiven
  Projekts; ohne aktives Projekt führt „Chat" auf die Projektliste.

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

### R-WP-38 — Navigation: Projektbaum im Sider, Einheiten je Projekt ❌ (Paul 2026-11-06, AntD-Umbau)

Die Navigation ist ein **Sider** (`Layout.Sider`) links mit einem Eintrag **je Projekt**. **Ein Klick
auf das Projekt öffnet seinen Chat** — es gibt **keinen eigenen „Jon"-Eintrag** und kein „Jon"-Label
in der Navigation (Paul 2026-11-06: „Jon … muss weg"; der Agent heißt in den Docs weiter Jon, und
seine Kachel behält den Namen, [R-WP-7](chat.md)). Unter dem Projekt stehen seine **Einheiten**:
**Files**, und **Paco**, sobald dessen Story gebaut ist (mehrere Paco-Instanzen je Projekt sind die
spätere Vision — der Sider ist der Ort dafür, kein zweiter Mechanismus). Der **Header** trägt nur
noch Marke, gewählten Nutzer und **Settings** — keine „Projekte"/„Chat"-Einträge, die der Sider schon
zeigt. Eine **Breadcrumb-Zeile gibt es nicht**: der Sider sagt, wo man ist. Die gewählte Einheit ist
im Sider markiert, und Sider-Markierung und URL sind einig (R-WP-1). Fällt das Fenster unter den
Sider-Breakpoint, **klappt der Sider** und die Chat-Fläche nutzt die Breite; die Auswahl bleibt über
den ausgeklappten Sider erreichbar. Die **Chat-Eingabe** gehört zur **Chat-Spalte** (dockt unten
innerhalb ihrer Spalte, [R-WP-35](chat.md)) — sie läuft nicht unter dem Sider durch, der Sider läuft
in voller Höhe daneben.

#### UC-WP-70 — projectTreeAndUnitEntries ❌
- GIVEN die App WHEN sie öffnet THEN zeigt der Sider ein Eintrag je Projekt, ohne „Jon"-Eintrag und
  ohne „Jon"-Label; ein Klick auf das Projekt öffnet dessen Chat.
- GIVEN ein geöffnetes Projekt WHEN der Nutzer die Einheit **Files** wählt THEN wechseln
  Sider-Markierung und URL einig auf dieselbe Einheit, und ein Reload landet wieder dort.
- GIVEN ein Fenster unter dem Sider-Breakpoint WHEN die App zeichnet THEN ist der Sider eingeklappt
  und die Chat-Fläche nutzt die volle Breite.

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

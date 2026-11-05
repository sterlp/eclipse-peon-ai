---
idPrefix: WP
---
# UI — Rahmen der App

> Navigation, Adressen, Auslieferung und Look. Was auf einer Seite passiert, steht im Feature-Doc der Seite:
> [Projekte](project.md) · [Jon-Chat](chat.md) · [Paco](paco.md) · [Config](config.md).

## Ziel

Eine schlanke Browser-App mit Top-Navigation, die unter `/ui/` ausgeliefert wird und einen Reload übersteht.

**Look — Bootstrap + eigenes Theme (Paul 2026-11-02):** Basis ist **Bootstrap** mit eigenem Theme; die
Farben und die kompakte Optik kommen aus der alten `chat.html` des Plugins — Nutzer-Zeile blau
(`#0969da`, dunkel `#58a6ff`), Agent-Zeile grün (`#1a7f37`, dunkel `#7ee787`), **Akzent beider Stimmen
links** (keine Rechtsbündigkeit, kein Rahmen um den Chat — die Kante gehört der Zeile), flache kompakte
Zeilen statt Bubbles, Tool-/Think-Zeilen klein und grau, Komponenten als `react-bootstrap`-Einzelimporte,
Icons als `bootstrap-icons`. Der Chat nutzt die **volle Fensterbreite abzüglich eines kleinen Randes**
— kein zentrierter Container, keine statischen Seitenränder (Befund Paul: genau das wollte er nicht).
`stara-web` bleibt Referenz für Wirkung (Abstände, Motion), **kein Abhängigkeitsziel** — nichts wird
kopiert, was wir nicht selbst verstehen und pflegen. Jeder Bereich der **Config-Seite** ist eine
**Card** wie im Chat (Rand `--border`, Radius, Padding, kein Hintergrund); Eingaben tragen die
**Maße der Chat-Eingabe**, Statuszeilen (Key-Status) **Mono** (2026-11-02, Inc 7). Die endgültige Look-Abnahme erfolgt im
Smoke-Test am Prototypen.

## Regeln

### R-WP-1 — Top-Navigation: Projekte, Chat, Settings ✅ (geschärft Paul 2026-11-02)

Die Navigation ist ein **Top-Menü mit genau drei Einträgen** — **Projekte**, **Chat**, **Settings**.
Es gibt **keine linke Sidebar** im MVP: die Hauptfläche gehört dem Chat, man erreicht den Chat eines
Projekts über **Projekte**. Im Chat zeigt die Kopfzeile nur, **welches Projekt gewählt ist** — keine
zweite „Chat"-Überschrift, die man auch im Menü sieht (Befund Paul). **„Chat"** im Menü öffnet den
Chat des **zuletzt aktiven Projekts**; ohne aktives Projekt führt „Chat" auf **Projekte**. Die
Chats-Übersicht links (Projekt-Accordion, mehrere Paco-Chats je Projekt mit **+**, Überblick über alle
Chats) ist die **spätere Vision** ([Paco](paco.md)) — kein MVP-Mechanismus, ❓ in
[Open Points](open-points.md).

**URL-stabil:** jede Navigation (Projekt öffnen, View wechseln) ändert die URL (**Aktion → URL →
UI-Change**) — kein rein funktionierender `useState`-Zustand; ein Reload bleibt auf der Seite
(UC-WP-43).

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

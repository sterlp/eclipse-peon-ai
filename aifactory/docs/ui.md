---
idPrefix: WP
---
# UI — Rahmen der App

> Seiten, Adressen, Auslieferung und Look. Was auf einer Seite passiert, steht im Feature-Doc der Seite:
> [Projekte](project.md) · [Jon-Chat](chat.md) · [Paco](paco.md) · [Config](config.md).

## Ziel

Eine schlanke Browser-App mit genau vier Seiten, die unter `/ui/` ausgeliefert wird und einen Reload übersteht.

**Look:** Orientierung ist **stara-web** — Eclipse-Projekt `/stara-web`, auf der Platte
`/Users/sterlp/dev/workset/Strata/apps/host` (React 19 + Vite + TypeScript, eigenes CSS, kein
Bootstrap, `prismjs` für Code, `katex` für Formeln). Übernehmen wollen wir die Chat-Optik: schmale
Spalte, Nachricht = Kopf (Name/Zeit) + Rumpf, abgesetzte Tool-/Statuszeilen, eigener Block für Code.
**Bootstrap ist als Mittel erlaubt, aber nicht Vorgabe** — was zählt, ist die Wirkung, nicht der
Stapel. stara-web ist eine **Referenz, kein Abhängigkeitsziel** — nichts wird von dort kopiert, was wir
nicht selbst verstehen und pflegen. **Abweichung von dort:** deutlich kleinere linke/rechte Ränder als in
stara-web — die Spalte darf breiter sein.

## Regeln

### R-WP-1 — Seiten: Projekte und Config global, Jon-Chat und Paco je Projekt ❌

Global gibt es **Projekte** (Startseite) und **Config** ([R-WP-16](config.md)). Je Projekt gibt es
**Jon-Chat** (Startseite des Projekts) und **Paco** ([Paco](paco.md)). Alles andere —
Dateien, Agenten-Kacheln, Slide-outs — ist ein **Bereich** im Jon-Chat, nie eine weitere Seite.
**Project Docs** (Docs als Website) ist eine spätere Seite und nicht Teil des MVP.

Jede Seite hat eine eigene Adresse unter `/ui/` (z. B. `/ui/projects/<id>/chat`); ein Reload bleibt
auf der Seite. Eine unbekannte Adresse unter `/ui/` liefert die App, nie ein Asset und nie eine
Weiterleitungsschleife (R-WP-22).

#### UC-WP-1 — showsExactlyTheseFourPages ❌
- GIVEN die App WHEN der Nutzer navigiert THEN erreicht er global *Projekte* und *Config*, je Projekt
  *Jon-Chat* und *Paco* — keine weitere Seite; Datei-Bereich und Team-Kacheln liegen im Jon-Chat.

#### UC-WP-43 — reloadStaysOnThePage ❌
- GIVEN der Nutzer ist im Jon-Chat eines Projekts WHEN er den Browser neu lädt THEN ist er wieder im
  Jon-Chat desselben Projekts, mit vollständigem Verlauf.
- GIVEN eine unbekannte Adresse unter `/ui/` WHEN sie aufgerufen wird THEN kommt die App (`index.html`)
  — kein `500`, keine Weiterleitungsschleife, und `/ui/assets/**` bleibt davon unberührt.

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

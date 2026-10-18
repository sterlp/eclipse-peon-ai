---
idPrefix: TR
---

# Tagged Results — Tool-Results und System-Infos als getaggte Blöcke

> **Status:** 🚧 in design (2026-10-18, Paul). **Code:** `agent/model/TaggedResult` +
> `ChatMessageUtil.extractMessages` (WIP im Tree), `CodegraphContextItem` (bereits live).
> **Verwandt:** [context-message-concept.md](context-message-concept.md) · [compact.md](compact.md) ·
> [icons.md](icons.md) · [async-agents.md](async-agents.md)

## Ziel

Der Agent bekommt Text aus drei Quellen in denselben Message-Kanal: User-Input, Harness-Infos
(Async-Ergebnisse, Fehler, Zustand) und Tool-Results. Ohne Markierung rät das Modell, wer spricht.
Ein Tag im Text macht den Absender sichtbar, ohne die langchain4j-Message-Typen zu verbiegen — und
er wird gleichzeitig zum **einzigen** Klassifikationsschlüssel: der Compact filtert und priorisiert
darüber, statt über `instanceof`.

Nebennutzen: gleiche `head`-Tags kollabieren zu einem Block (Memory/Context-Items stehen im
Compact-Input nur einmal).

## Format

```
//-- Start <head>
<body>
//-- End <head>
```

`head` = Kurzbezeichnung (z. B. `tool call: eclipseReadFile`, `context: memory`, `async result: Da Thinka`).
`TaggedResult.INFO` beschreibt das Format und steht **einmal** in der static System-Message.

## Regeln

### R-TR-1 — Prefix und Suffix stehen immer 🚧

GIVEN ein Harness-Text wird als `TaggedResult` gerendert
WHEN er in eine Message geht
THEN stehen `//-- Start <head>` und `//-- End <head>` **immer** — kein Trim, kein Weglassen, `head`
     in **beiden** Zeilen. Der Block ist damit für das Modell abgrenzbar und für Peon identifizierbar.

### R-TR-2 — Erkennung nur am Start 🚧

GIVEN ein Message-Text
WHEN er mit `TaggedResult.PREFIX` beginnt
THEN gilt er als getaggt: er bekommt **keinen** zusätzlichen `USER:`/`AI:`-Header.
     Das Suffix ist Lesbarkeit für das Modell, **nicht** Teil der Identität — Prüfung ohne `endsWith`,
     ohne `strip` (angehängte Disclosure wie `(trimmed)` darf die Erkennung nicht kippen).

### R-TR-3 — Jedes `ContextItem` rendert ein `TaggedResult` 🚧

GIVEN der Turn-Kontext wird gebaut
WHEN ein `ContextItem` Inhalt liefert
THEN liefert es ein `TaggedResult` mit `head` = Item-Name; `ContextItem.render()` ist für **alle**
     Implementierungen gleich. `null` = das Item hat in diesem Turn nichts beizutragen.

### R-TR-4 — Codegraph rendert nur im CONNECTED-Zustand 🚧

GIVEN Codegraph ist verbunden
WHEN der Turn-Kontext gebaut wird
THEN liefert das Item ein `TaggedResult` (Pfad-Mapping + Server-Instructions).

GIVEN Codegraph ist OFF oder der Connect ist fehlgeschlagen
WHEN der Turn-Kontext gebaut wird
THEN liefert das Item **nichts** (`null`). Der Zustand ist am Header-Icon sichtbar (rot + Tooltip)
     und über `label()` im Chat — kein zweiter Kontextweg. (Paul 2026-10-18: „bei Fehler muss kein
     ContextItem sein".)

### R-TR-5 — Zustand trägt die Instructions (ein Supplier) 🚧

GIVEN der Codegraph-Service liefert seinen Zustand
WHEN das Item ihn liest
THEN enthält `State` die Instructions (beim Connect captured). Das Item hat genau **einen**
     Supplier und keine zweite Quelle — `render()` ist `State.instructions` oder `null`.

### R-TR-6 — `INFO` steht einmal in der static System-Message 🚧

GIVEN ein Agent, der getaggte Ergebnisse bekommen kann
WHEN sein static context gebaut wird
THEN enthält die System-Message `TaggedResult.INFO` genau einmal (gecacht mit dem static context,
     **nicht** pro Turn und **nicht** als Turn-Context-Item).
     Async-Tool-Results / Async-Agent-Results beschreibt `INFO` nur so weit, wie es sie heute gibt;
     die eigentliche Async-Tool-Fähigkeit (Shell!) ist eine eigene Story.

### R-TR-7 — Der Compact klassifiziert über die Tags 🚧

GIVEN der Compact baut seinen Input
WHEN Messages gerendert werden
THEN bestimmt er Typ und Kürzungsstufe anhand der Tag-Präfixe (`THINK:`, `TOOL REQUEST:`,
     `TOOL RESULT:`, `SYSTEM:`) — **nicht** über `instanceof` über langchain4j-Message-Typen.
     Ein neuer Nachrichtentyp braucht damit keinen neuen Compact-Zweig, nur einen Tag.

### R-TR-8 — Kein Cap im Renderer 🚧

GIVEN beliebiger Text
WHEN `ChatMessageUtil` ihn rendert
THEN wird **nichts** gekürzt (kein `toolMessageSize`, kein `thinkCapChars`, kein `maxSize`).
     Kürzen ist alleinige Aufgabe des Compact (Stufen + Disclosure am Ende, R-CIB).

### R-TR-9 — Gleicher `head` wird im Compact-Input einmal eingefügt 🚧

GIVEN zwei getaggte Blöcke mit demselben `head` (z. B. das Memory-Context-Item in mehreren Turns)
WHEN der Compact-Input gebaut wird
THEN steht nur einer davon im Input; die Zahl der kollabierten Blöcke wird **genannt**
     (kein hidden filter). Reihenfolge und Duplikate innerhalb eines Blocks bleiben unangetastet.

## Wie sieht `INFO` aus? (Token-Budget)

`TaggedResult.INFO` ist heute ~70 Wörter Prosa + Beispielblock. Für ein static-Prompt ist das
vertretbar, aber straffbar: Beispielblock (4 Zeilen) konserviert das Format, die Prosa davor kann auf
einen Satz plus Tag-Liste schrumpfen. Vorschlag — ein Satz, die Tag-Liste, dann das Beispiel:

```
Harness blocks (system infos, async results, tool results) look like this:
//-- Start <head>
<body>
//-- End <head>
Do not answer them as user input.
```

Der letzte Satz ist der Kern (Adressat), der Rest erklärt sich aus dem Beispiel.

## Test-Konvention (gilt für den Bau)

- Tests **außerhalb** von `ChatMessageUtilTest` prüfen **Inhalt** (`contains`), nicht die exakte
  Render-Struktur. Wer die Struktur braucht, ruft `ChatMessageUtil` im Assert auf — sonst ist es ein
  Test-Bug (Paul 2026-10-18).
- Der Umbau von `toString` auf `extractMessages` ist formatgleich; abweichende Alt-Tests werden
  vereinfacht, nicht nachgezogen.

## Offene Punkte

- Async-Tool-Results (Shell!) sind für Paul **wichtiger als async Agents** — eigene Story, `INFO`
  hält dafür nur den Platz frei.
- Doppel-Tagging: Tool-Results tragen schon ein Symbol (🔍/📜/🗜, [icons.md](icons.md)). Symbol =
  Werkzeug, Tag = Absender — gewollt, aber im Smoke prüfen, ob es nicht redundant wirkt.

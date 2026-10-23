---
idPrefix: TR
---

# Tagged Results — Tool-Results und System-Infos als getaggte Blöcke

> **Status:** ✅ done (2026-10-18, Branch `story/night-5-2026-10-16`,
> Da-Dok-Review CONCERNS ohne Blocker + Nacharbeit). **Code:** `agent/model/TaggedResult`,
> `shared/ChatMessageUtil` (einziger Renderer, uncappt), `context/ContextItem`, `compact/ContextTrimComponent`.
> **Verwandt:** [context-message-concept.md](context-message-concept.md) · [compact.md](compact.md) ·
> [icons.md](../../org.sterl.llmpeon/docs/icons.md) · [async-agents.md](async-agents.md)

## Ziel

Der Agent bekommt Text aus drei Quellen in denselben Message-Kanal: User-Input, Harness-Infos
(Async-Ergebnisse, Fehler, Zustand) und Tool-Results. Ohne Markierung rät das Modell, wer spricht.
Ein Tag im Text macht den Absender sichtbar, ohne die langchain4j-Message-Typen zu verbiegen — und
er wird gleichzeitig zum **einzigen** Klassifikationsschlüssel: der Compact filtert und priorisiert
darüber, statt über `instanceof`.

Nebennutzen: ein reiner Block ist auf den ersten Blick erkennbar und kann vor dem Compact-Input
komplett entfallen (R-TR-9) — kein head-Matching, keine Inhalts-Deduplizierung nötig.

## Format

```
//-- Start <head>
<body>
//-- End <head>
```

`head` = Kurzbezeichnung (z. B. `tool call: eclipseReadFile`, `context: memory`, `async result: Da Thinka`).
`TaggedResult.INFO` beschreibt das Format und steht **einmal** in der static System-Message.

## Format-Fakten des Renderers (Bau I1, 2026-10-18)

Der Renderer (`ChatMessageUtil.extractMessages`) liefert **je `TextContent` einen Eintrag**; ein
echter `TaggedResult`-Block bleibt dabei unverändert und ohne Header, alles andere bekommt seinen
Tag. Konkrete Renderformate (die Stufen klassifizieren später über genau diese Präfixe):

| Eintrag | Format |
|---|---|
| Thinking | `THINK:` + NL + Text (vorher inline `Think: `) |
| Tool-Request | `TOOL REQUEST:` + NL + `tool name: <n>` + NL + `arguments:<args>` |
| Tool-Result | `TOOL RESULT:` + NL + `tool name: <n>` + NL + `result:` + NL + Text |
| System | `SYSTEM:` + NL + Text |
| reiner Block | Block unverändert, kein Header |
| User-Text (ein TextContent) | `USER:` + NL + Text |

Konsequenz für Multi-Content-User-Messages (Turn-Kontext + echter User-Text): der Block steht als
eigener Eintrag **ohne** Header, der echte Text bekommt seinen eigenen `USER:`-Header. Erst dadurch
ist der Block ein eigener, droppbarer String (R-TR-9) — vorher lief alles unter einem Header.

## Regeln

### R-TR-1 — Prefix und Suffix stehen immer ✅ (2026-10-18, I1)

GIVEN ein Harness-Text wird als `TaggedResult` gerendert
WHEN er in eine Message geht
THEN stehen `//-- Start <head>` und `//-- End <head>` **immer** — kein Trim, kein Weglassen, `head`
     in **beiden** Zeilen. Der Block ist damit für das Modell abgrenzbar und für Peon identifizierbar.

### R-TR-2 — Erkennung über Start **und** Ende ✅ (2026-10-18, I1)

GIVEN ein Message-Text
WHEN seine erste Zeile mit `TaggedResult.PREFIX` beginnt **und** seine letzte Zeile mit
     `TaggedResult.SUFFIX`
THEN ist er ein **reiner** getaggter Block: er bekommt **keinen** zusätzlichen `USER:`/`AI:`-Header
     und wird vor dem Compact-Input entfernt (R-TR-9).

GIVEN ein Message-Text, der nur *enthält* (z. B. Tool-Result: Absenderzeile + eingebetteter Block)
WHEN er gerendert oder compactiert wird
THEN gilt er **nicht** als reiner Block — er bleibt vollständig im Input. Genau das ist der Trick:
     ein an ein Tool-Result angehängter Async-Result wird nie gedroppt, ein Context-Item immer.
     (Paul 2026-10-18.)

Die Prüfung ist **zeilenweise** (`startsWith` je Zeile), nicht `endsWith` über den ganzen Text —
`//-- End <head>` trägt den `head` (R-TR-1).

### R-TR-3 — Jedes `ContextItem` rendert ein `TaggedResult` ✅ (2026-10-18, I2)

GIVEN der Turn-Kontext wird gebaut
WHEN ein `ContextItem` Inhalt liefert
THEN liefert es ein `TaggedResult` mit `head` = Item-Name; `ContextItem.render()` ist für **alle**
     Implementierungen gleich. `null` = das Item hat in diesem Turn nichts beizutragen.

### R-TR-4 — Codegraph rendert nur im CONNECTED-Zustand ✅ (2026-10-18, I2)

GIVEN Codegraph ist verbunden
WHEN der Turn-Kontext gebaut wird
THEN liefert das Item ein `TaggedResult` mit **genau einer** Zeile: dem Disk→Workspace-Pfad-Mapping.
     Die **Server-Instructions werden nicht mehr injiziert** (Paul 2026-10-19): sie waren ~6,6k Zeichen
     Werbetext, duplizierten die MCP-Tool-Description und passten nicht zu einem Java/Eclipse-Projekt.
     Unser Satz ist das Einzige, was der Server nicht selbst liefert.

GIVEN Codegraph ist OFF oder der Connect ist fehlgeschlagen
WHEN der Turn-Kontext gebaut wird
THEN liefert das Item **nichts** (`null`). Der Zustand ist am Header-Icon sichtbar (rot + Tooltip) —
     kein zweiter Kontextweg, **kein** Chat-Label. (Paul 2026-10-18: „bei Fehler muss kein
     ContextItem sein"; das 📋-Label hängt an der erfolgreichen Injektion, sonst wäre es ein
     Spam pro Turn.)

### R-TR-5 — Zustand trägt die Instructions (ein Supplier) ✅ (2026-10-18, I2) — Injection seit 2026-10-19 entfallen

GIVEN der Codegraph-Service liefert seinen Zustand
WHEN das Item ihn liest
THEN enthält `State` die beim Connect eingefangenen Instructions. Das Item hat genau **einen**
     Supplier und keine zweite Quelle. **Präzisiert 2026-10-19 (Paul):** die Instructions reisen
     weiter im `State` (und im Dedup-Marker) — **gerendert** wird nur noch das Pfad-Mapping
     (R-TR-4). Kein zweiter Zustands-Supplier, keine Text-Durchreichung.

### R-TR-6 — `INFO` steht einmal in der static System-Message ✅ (2026-10-18, I2)

GIVEN ein Agent, der getaggte Ergebnisse bekommen kann
WHEN sein static context gebaut wird
THEN enthält die System-Message `TaggedResult.INFO` genau einmal (gecacht mit dem static context,
     **nicht** pro Turn und **nicht** als Turn-Context-Item).
     Async-Tool-Results / Async-Agent-Results beschreibt `INFO` nur so weit, wie es sie heute gibt;
     die eigentliche Async-Tool-Fähigkeit (Shell!) ist eine eigene Story.
     **Präzisiert 2026-10-19 (❌ specified):** `INFO` nennt ausschließlich, was es heute gibt —
     Context-Blöcke (System-Info, Projekt-/Codegraph-Info, Memory-Snapshot, Datei-Kontext).
     **Tool-Results gehen unverändert an das Modell** (kein Tag-Block) und Async-Ergebnisse
     existieren nicht; beide Sätze kommen erst mit der jeweiligen Fähigkeit in `INFO` (sonst
     verspricht das static Prompt ein Format, das nie ankommt). `TaggedResultTest` pinnt den Text
     (5 Zeilen, `PREFIX`/`SUFFIX`) statt ihn nur nach stderr zu drucken.

### R-TR-7 — Der Compact klassifiziert über die Tags ✅ (2026-10-18, I3)

GIVEN der Compact baut seinen Input
WHEN Messages gerendert werden
THEN bestimmt er Typ und Kürzungsstufe anhand der Tag-Präfixe (`THINK:`, `TOOL REQUEST:`,
     `TOOL RESULT:`, `SYSTEM:`) — **nicht** über `instanceof` über langchain4j-Message-Typen.
     Ein neuer Nachrichtentyp braucht damit keinen neuen Compact-Zweig, nur einen Tag.

### R-TR-8 — Kein Cap im Renderer ✅ (2026-10-18, I3)

GIVEN beliebiger Text
WHEN `ChatMessageUtil` ihn rendert
THEN wird **nichts** gekürzt (kein `toolMessageSize`, kein `thinkCapChars`, kein `maxSize`).
     Kürzen ist alleinige Aufgabe des Compact (Stufen + Disclosure am Ende, R-CIB).

### R-TR-9 — Reine Context-Blöcke fliegen vor dem Compact raus ✅ (2026-10-18, I3)

GIVEN eine Message, deren Text ein reiner getaggter Block ist (R-TR-2) — also ein `ContextItem`
     (Memory-Snapshot, Datei-Kontext, Projekt-/Codegraph-Info), das als **eigenes** `TextContent`
     angehängt wurde
WHEN der Compact-Input gebaut wird
THEN fällt dieser Block **vollständig** weg (nicht kürzen, nicht priorisieren, nicht kollabieren) —
     nach dem Compact hängt `renderTurnContext` die Items ohnehin frisch an.
     Die Zahl der entfernten Blöcke wird **genannt** (kein hidden filter).

Kein `head`-Matching, kein Keep-first/Rewrite nötig: „reiner Block" ist das Kriterium, `head` ist
nur die Absenderzeile. Ein an ein Tool-Result **angehängter** Block ist kein reiner Block und bleibt
(R-TR-2).

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
- Doppel-Tagging: Tool-Results tragen schon ein Symbol (🔍/📜/🗜, [icons.md](../../org.sterl.llmpeon/docs/icons.md)). Symbol =
  Werkzeug, Tag = Absender — gewollt, aber im Smoke prüfen, ob es nicht redundant wirkt.

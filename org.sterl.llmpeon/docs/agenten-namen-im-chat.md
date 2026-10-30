# Agenten-Namen im Chat-Header

> **Status: umgesetzt** (Zyklus 2026-10-30, [ADR-0078](../../docs/adr/0078-origin-in-core-owner-monitor.md),
> [ADR-0079](../../docs/adr/0079-display-names-without-ork-names.md)). Technische Heimat:
> `org.sterl.llmpeon/resources/chat/chat.html` (+ `test-chat.html` als manueller Harness).

## Ziel

Jede AI-Nachricht im Plugin-Chat zeigt **wer gerade spricht** — statt immer „Peon": `Jon`, `Plan`,
`Dev`, `Review` (Anzeigenamen, ADR-0079), die Namen der **Custom-Agents**, und für die
Sub-Agent-Loops `Search` bzw. `Compact`. Bei einem **Restore** ist die Differenzierung gleichgültig —
dann sind ohnehin nur noch die Messages des aktuellen Agenten sichtbar (`refreshChat()` lädt dessen
Memory neu). Der Wert liegt im **Live-Delegations-Fall**: Während Jon delegiert, laufen die Slaves über
denselben Monitor, und ohne Stempel erscheinen alle als „Peon" — ein gemischter Verlauf wäre
ununterscheidbar.

## Mechanismus (umgesetzt)

Der Name reist als **`origin`** (gestempelter Absender, ADR-0078) auf der `SimpleMessage` mit —
Core stempelt ihn im `OwnerMonitor`, keine `AiMonitor`-Interface-Änderung, kein Lambda bricht.
`ChatMarkdownWidget.java` ist **unverändert**: `SimpleMessage` wird als Record serialisiert und
`origin` reist gratis mit bis in den Browser.

`chat.html`:

* CSS: `.message.AI::before { content: attr(data-agent); }` — **ohne** „Peon"-Fallback:
  `appendMessage` setzt `div.dataset.agent = message.origin` nur, wenn `origin` vorhanden ist;
  eine Zeile **ohne** Agenten-Bezug trägt **keinen** Kopf statt eines erfundenen.
* Merge-Regel (A7): zwei aufeinanderfolgende AI-Blöcke verschmelzen nur, wenn Rolle **und**
  Absender (`data-agent`) übereinstimmen — sonst entstünde ein Sammel-Kopf über verschiedene
  Agenten hinweg.
* TOOL-Zeilen bleiben ohne Kopf (A8, Q5): `"Tool"`-Kopf bewusst nicht angefasst; offener Punkt
  siehe unten.

### Custom-Agents

Sind **automatisch abgedeckt**: `CustomAgent` erbt den zentralen Stempel-Pfad des Cores und trägt
den Namen aus seiner Definition — ohne Sonderbehandlung.

### Sub-Loops

`SearchAgentTool` und der Compressor (`Compact`) stempeln ihre eigenen Loops über `LoopOwner.named(…)`
(Inc 2) — ihre Zeilen tragen `Search` bzw. `Compact`, während die Zeilen des aufrufenden Agenten
dessen Namen behalten.

## BDD

```
GIVEN Jon delegiert live an seinen Dev-Slave über denselben Monitor
WHEN der Slave eine AI-Message emittiert
THEN trägt die SimpleMessage origin="Dev"
AND  der Chat-Header dieser Message zeigt „Dev"

GIVEN eine AI-Message ohne Agenten-Bezug (origin == null)
WHEN sie im Chat erscheint
THEN trägt sie keinen Kopf

GIVEN zwei aufeinanderfolgende AI-Messages verschiedener Agenten
WHEN sie gerendert werden
THEN bleiben es zwei Blöcke mit je eigenem Kopf (Merge nur bei gleichem Absender)

GIVEN ein Custom-Agent „Reviewer" antwortet
THEN trägt seine AI-Message origin="Reviewer" (aus getName())
```

## Tests / Nachweise

| Test | Inhalt |
|---|---|
| `SimpleMessageOriginJsonTest` (org.sterl.llmpeon.test) | GIVEN `new SimpleMessage(Type.AI, "text", "Dev")` WHEN mit dem Widget-`ObjectMapper` serialisiert THEN enthält das JSON `"origin":"Dev"`; GIVEN `new SimpleMessage(Type.AI, "text")` THEN `"origin":null` — das Feld erreicht den Browser |
| manuell (Harness `test-chat.html`) | Nachricht mit/ohne `origin`: mit Name erscheint der Kopf, ohne Name kein Kopf; zwei AI-Nachrichten verschiedener Agenten bleiben zwei Blöcke |

## Offene Punkte

| Status | Punkt |
|---|---|
| ❓ | TOOL-Zeilen ohne Kopf (A8): sollen sie künftig den ausführenden Werkzeug-Namen tragen? Entschei­dung bewusst zurückgestellt (Q5), kein Verhalten gebaut. |

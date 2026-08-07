# Agenten-Namen im Chat-Header (WIP)

> **Status: WIP / Design-Parkplatz.** Noch nicht gebaut. Dieses Dokument hält das aktuelle
> Wissen fest, wie man es umsetzen *könnte*, damit die Recherche nicht verloren geht. Vor der
> Umsetzung: die offene Entscheidung unten klären, dann BDD schärfen.

## Ziel

Der AI-Message-Header im Chat soll **wer gerade spricht** anzeigen — statt immer „Peon". Also
`Peon-PO`, `Peon-Plan`, `Peon-Dev`, die Namen der **Custom-Agents**, und für die
Sub-Agent-Loops `Da Sniffa` (Search) bzw. `Da Scribe` (Compact).

**Warum das wirklich hilft:** Wenn Jon delegiert, laufen die Slaves über **denselben Monitor**
(`slave.call(prompt, this.monitor)`), und der Search-Request erbt denselben Monitor. Ihre
AI-Messages fließen also live in dieselbe Chat-View und erscheinen heute alle als „Peon". Ein
gemischter Verlauf wird dadurch ununterscheidbar.

Bei einem **Restore** ist die Differenzierung egal — dann sind ohnehin nur noch die Messages des
aktuellen Agenten sichtbar (`refreshChat()` lädt dessen Memory neu). Der Wert liegt also im
**Live-Delegations-Fall**.

## Aktueller Stand heute

Das Label ist hart in CSS verdrahtet — es gibt keinen Per-Message-Namen:

```css
/* chat.html */
.message.AI::before { content: "Peon"; }
```

Der Name müsste also bis ins Label durchgereicht werden.

## Kernbefund — kein Monitor-Umbau nötig

Der naheliegende (aber falsche) Weg wäre, den Namen im Plugin über `getActiveAgent()` zu holen.
Das ist **falsch**: Während Jon delegiert, bleibt der aktive Agent `Peon-PO` — jede Slave-Message
bekäme fälschlich „Peon-PO".

Der zweite naheliegende Gedanke — „der Agent muss bei `onChatResponse` mitgeben, wer er ist" —
würde die `AiMonitor`-Signatur ändern (funktionales Interface, überall als Lambda genutzt, u. a.
`(AiMonitor)(SimpleMessage m) -> …`) und wäre invasiv.

**Beides ist unnötig.** Der Code hat einen bequemen Seam:

* Einziger Emit-Punkt für alle AI-Messages ist `ToolService.executeLoop` **Zeile ~138**:
  ```java
  ToSimpleMessage.INSTANCE.convert(response.aiMessage()).forEach(req.monitor::onChatResponse);
  ```
  Dort ist der `req` (`ToolLoopRequest`) verfügbar.
* `AbstractAgent.doCall` **Zeile ~225** baut den `ToolLoopRequest` an **einer zentralen Stelle**
  für *alle* echten Agenten (`AiPoAgent`/`AiPlanAgent`/`AiDevAgent`/`CustomAgent`). Die Slaves
  erben das automatisch, weil sie `AiPlanAgent`/`AiDevAgent` sind.

Der Name reist also **auf dem Request** mit — der Monitor bekommt weiterhin nur eine
`SimpleMessage`, jetzt mit befülltem `agent`-Feld. **Keine Interface-Änderung, kein Lambda bricht.**

## Skizze der Umsetzung (~5–6 Dateien, geringes Risiko)

1. **`SimpleMessage`** (core) — optionales Feld `agent`, rückwärtskompatibel per Zusatz-Konstruktor,
   damit die vielen `new SimpleMessage(Type.X, "…")`-Call-Sites unverändert bleiben:
   ```java
   public record SimpleMessage(Type role, String message, String agent) {
       public SimpleMessage(Type role, String message) { this(role, message, null); }
   }
   ```
2. **`ToolLoopRequest`** (core) — Feld `agentLabel` (Lombok `@Builder`, 1 Zeile).
3. **`ToolService.executeLoop`** (~Zeile 138) — `req.agentLabel` in die emittierte `SimpleMessage`
   stempeln (über `ToSimpleMessage` oder beim Weiterreichen).
4. **`AbstractAgent.doCall`** (~Zeile 225) — `.agentLabel(getName())`. **Eine** Zeile, deckt
   PO/Plan/Dev/**Custom** inkl. Slaves ab.
5. **`chat.html`** — `appendMessage` setzt `div.dataset.agent = message.agent`; CSS wird dynamisch:
   ```css
   .message.AI::before { content: attr(data-agent); }
   ```
   mit „Peon"-Fallback, wenn `agent` leer ist.

### Custom-Agents

Sind **automatisch abgedeckt**: `CustomAgent.getName()` liefert den Namen aus `promptFile.getName()`,
und `CustomAgent` erbt `AbstractAgent.doCall`. Die zentrale `.agentLabel(getName())`-Zeile stempelt
also auch deren Namen — ohne Sonderbehandlung.

### Die einzigen Sonderfälle — die zwei, die *keine* `AiAgent` sind

Diese bauen ihren Request selbst und haben **kein** `getName()`:

* **`SearchAgentTool`** — baut via `this.request.toBuilder()`, **erbt** sonst das Parent-Label
  (`Peon-PO`). Muss `agentLabel` **explizit überschreiben** (z. B. „Da Sniffa").
* **`AiCompressorAgent`** — emittiert selbst über `monitor::onChatResponse` (~Zeile 54). Label
  **explizit** setzen (z. B. „Da Scribe").

## Offene Entscheidung (vor der Umsetzung klären)

Für die AI-Header von Search/Compact — **funktional** („Search Agent" / „Compact") oder die
**Ork-Namen** („Da Sniffa" / „Da Scribe")? Empfehlung: **Ork-Namen**, damit Header und die
bestehenden `TOOL`-Fortschrittszeilen (siehe [Sub-agent tool timing](sub-agent-timing.md))
konsistent dieselbe Figur zeigen. Die Ork-Namen sind bislang UI-only — hiermit erschienen sie an
einer zweiten Stelle.

## Test-Idee (Regression)

* `SimpleMessage`-JSON trägt `agent` (Jackson) — ein Test prüft, dass ein Slave-Call über den
  gemeinsamen Monitor eine `SimpleMessage` mit `agent="Peon-Dev"` emittiert (bzw. `Peon-Plan`).
* Namens-agnostisch bleibt nur, was auch heute schon flavourful ist; der Test asserted das
  **Feld**, nicht die CSS-Darstellung (`attr()` selbst ist nicht sinnvoll testbar).

## BDD (Entwurf)

```
GIVEN Jon delegiert live an seinen Peon-Dev-Slave über denselben Monitor
WHEN der Slave eine AI-Message emittiert
THEN trägt die SimpleMessage agent="Peon-Dev"
AND  der Chat-Header dieser Message zeigt „Peon-Dev" statt „Peon"

GIVEN ein Custom-Agent „Reviewer" antwortet
THEN trägt seine AI-Message agent="Reviewer" (aus getName())

GIVEN der Search-Sub-Agent (kein AiAgent) spricht
THEN trägt seine AI-Message das explizit gesetzte Label (Entscheidung offen: „Da Sniffa")
```

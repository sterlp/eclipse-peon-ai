# API-Retry MVP (WIP / Write-Ahead)

> **Status: WIP / Write-Ahead-Plan.** Noch nicht gebaut. Kommt **nach** dem Agenten-Status im Header
> ([agenten-status-im-header.md](agenten-status-im-header.md)) — der Retry meldet sich über denselben
> Status/Progress-Kanal.

## Ziel

Wenn die LLM-API mal die Grätsche macht (Netzwerk-Blip, 5xx, Timeout), soll **nicht der ganze Turn
verloren gehen** („Jon ist weg"), sondern der Call **ein paar Mal automatisch wiederholt** werden.
Super-Mini-MVP: wenige Versuche, kurzer Backoff, sichtbar im Chat.

## Warum ein Retry hier sicher ist

* **Ein einziger Choke-Point für *alle* API-Calls** (Main-Loop, Search-Sub-Agent, Compaction):
  `ToolLoopRequest.call` (Z.112) → `StreamingBridge.call(model, request, monitor)` → `model.chat(…)`.
  Fehler kommen von dort als geworfene `RuntimeException` hoch (`StreamingBridge` Z.85–86, nachdem
  `onError` `errorRef` gesetzt hat).
* **Ein mitten im Stream gestorbener Call hat nichts committet:** Die `SimpleMessage` fürs
  Chat/Memory entsteht erst bei `onCompleteResponse` → `ToolService.executeLoop` Z.138. Ein Abbruch
  vorher hinterlässt **kein** halbes Chat-Bubble, **kein** verseuchtes Memory. Der Live-Stream ist
  nur das transiente Status-Overlay (siehe [streaming-display.md](streaming-display.md)) — ein Retro
  überschreibt es einfach neu.
* Der `StreamingBridge` **resetet seinen Per-Call-State** am Anfang jedes `call()` (Latch, Refs) —
  ein erneuter Aufruf ist unbedenklich.

→ Retry an **genau einer Stelle** (`ToolLoopRequest.call`) deckt den ganzen Stack ab, ohne
Doppel-Text oder kaputten Verlauf.

## Was wiederholt wird — und was nicht

* **Wiederholen:** transiente Fehler (Netzwerk, 5xx, Timeout).
* **NICHT wiederholen:**
  * **`CancellationException`** — der User hat abgebrochen (bzw. der Watchdog-Cancel in
    `StreamingBridge`). Sofort durchreichen.
  * (Optional, Ausbaustufe) **4xx / Validierungsfehler** — die werden beim zweiten Versuch auch nicht
    grün. Klassifizierung braucht aber Provider-Exception-Inspektion.

**MVP-Empfehlung (dumm-aber-sicher):** *jeden* Fehler außer `CancellationException` wiederholen,
gekappt auf **N Versuche** mit kurzem Backoff. Keine HTTP-Code-Klassifizierung im MVP — bewusst
minimal; 4xx laufen dann halt einmal unnötig, kosten aber nur N·Backoff Sekunden.

## Sichtbarkeit

Jeder Retry meldet sich über den Monitor, damit der User es sieht (und es zum Header-Status passt):

* `onTool("API-Fehler — Versuch 2/3 in 2s …")` vor dem nächsten Versuch.
* Nach der letzten erfolglosen Runde: Fehler wie heute hochwerfen (`onProblem` greift oben schon).

## Skizze der Umsetzung (~1–2 Dateien, sehr klein)

1. **`ToolLoopRequest.call`** (Z.112) — Schleife um `bridge.call(…)`:
   ```java
   public ChatResponse call(ChatRequest chatRequest) {
       int attempt = 0;
       while (true) {
           try {
               return bridge.call(chatModel.getChatModel(), chatRequest, monitor);
           } catch (CancellationException ce) {
               throw ce;                                  // User-Abbruch: nie retryen
           } catch (RuntimeException e) {
               if (++attempt >= MAX_ATTEMPTS || monitor.isCanceled()) throw e;
               monitor.onTool("API-Fehler — Versuch " + (attempt + 1) + "/" + MAX_ATTEMPTS
                       + " in " + backoffSeconds(attempt) + "s …");
               sleepBackoff(attempt);                     // interruptible; bei Interrupt abbrechen
           }
       }
   }
   ```
2. **Konstanten/Config** — `MAX_ATTEMPTS` (z. B. 3) und Backoff (z. B. 1s → 2s → 4s, gekappt). MVP:
   hartkodiert; später nach `LlmConfig`/`agentConfig` ziehen, falls gewünscht.

**Abbruch-Respekt:** Vor/nach dem Sleep `monitor.isCanceled()` prüfen; bei `InterruptedException`
Flag setzen und den Fehler durchreichen — der Cancel-Pfad bleibt heilig.

## Offene Entscheidungen (vor der Umsetzung klären)

1. **N Versuche + Backoff-Kurve?** *Empfehlung: 3 Versuche, 1s/2s/4s exponentiell.*
2. **Klassifizieren (4xx nicht retryen) — MVP oder Ausbaustufe?** *Empfehlung: Ausbaustufe; MVP
   retryt alles außer Cancel.*
3. **Konfigurierbar oder hartkodiert?** *Empfehlung: MVP hartkodiert, Config später.*

## Test-Idee (Regression)

* `ToolLoopRequest.call` mit einem Bridge-Stub, der **k-mal** wirft und dann eine `ChatResponse`
  liefert → erwartet: Ergebnis nach k Retries, `onTool`-Retry-Meldungen k-mal.
* Stub, der **immer** wirft → nach `MAX_ATTEMPTS` fliegt der Fehler hoch (kein Endlos-Loop).
* Stub, der `CancellationException` wirft → **sofort** durchgereicht, **kein** Retry.
* (Blackbox) Nach einem mid-stream-Fehler + erfolgreichem Retry ist **genau eine** AI-Message im
  Memory — kein Doppel-Text.

## BDD (Entwurf)

```
GIVEN die API wirft beim 1. Versuch einen transienten Fehler, beim 2. liefert sie eine Antwort
WHEN der Agent den Call macht
THEN wird der Call einmal wiederholt
AND  der User sieht "API-Fehler — Versuch 2/3 …"
AND  am Ende steht genau eine AI-Antwort im Chat (kein halbes Bubble vom Fehlversuch)

GIVEN die API wirft bei allen N Versuchen
THEN fliegt nach dem N-ten der Fehler hoch (wie heute), kein Endlos-Loop

GIVEN der User bricht ab (CancellationException)
THEN wird NICHT wiederholt, der Abbruch wird sofort durchgereicht
```

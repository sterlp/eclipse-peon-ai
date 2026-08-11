# Agent-API-Retry (Mini)

> **Status: ✅ done (released).** Umgesetzt als eigene Klasse
> `org.sterl.llmpeon.streaming.ApiRetry` + `ApiRetryTest` (7) + struktureller Test in `ToolServiceTest`;
> voller Reactor grün. Löst den alten Skizzen-Entwurf ab (fixes N=3 → jetzt aufladbares Retry-Budget).
>
> **Eine bewusste Abweichung vom Plan:** `ApiRetry` **hält den `monitor` nicht**, sondern bekommt ihn
> pro Aufruf: `ChatResponse call(AiMonitor monitor, Supplier<ChatResponse> aiCall)`. Grund: der
> `monitor` im `ToolLoopRequest` kann nach dem `@Builder`-Build noch via `.monitor(...)` gesetzt werden;
> ein bei ApiRetry-Konstruktion eingefangener Monitor wäre evtl. der falsche (NULL_MONITOR). Per-Call
> ist immer der aktuelle. Reiner Zustandshalter, gleich gut testbar.

## Ziel

Wenn die LLM-API einen transienten Fehler wirft (Netzwerk-Blip, 5xx, Timeout), soll ein **langer,
produktiver Turn nicht sofort verloren gehen**. Statt hart hochzuwerfen wird der Call **automatisch
wiederholt** — aber nur so oft, wie der Turn sich das durch bisherige Erfolge „verdient" hat.
Bewusst mini: ein Wrap, ein Zähler, sichtbarer Backoff, Abbruch heilig.

## Kernidee: verdiente Geduld statt fixer Anzahl

Nicht „immer 3 Versuche", sondern ein **Guthaben (`credit`)**, das mit erfolgreichen AI-Antworten
mitwächst — die Bereitschaft zu warten steigt mit dem, was schon investiert wurde. Zwei kleine Zähler:

- **`credit`** — verdiente Geduld: Start **0**, **+1 pro erfolgreichem Call**, max **10**.
- **`retryCount`** — aufeinanderfolgende Retries in der aktuellen Fehler-Serie: **+1 pro Retry**,
  **Reset auf 0 bei jedem erfolgreichen Call**. Bestimmt die Wartezeit.

## Fachliche Anforderungen

**A. Der Wrap**
1. Nur der **AI-Request** wird in einer eigenen Klasse gekapselt (`ApiRetry`, genau eine Stelle:
   `bridge.call`). Eine **Tool-Exception bekommt keinen Retry** (der Wrap sitzt nicht um die
   Tool-Ausführung). Da der Wrap am zentralen Choke-Point sitzt, gilt der Retry **für alle Agenten**
   (inkl. Custom-Agents) und **alle Kontexte** (Haupt-Turn, Such-Sub-Agent, Compaction) — bewusst so.
1b. **Exception-Klassifikation (Fallstrick):** `java.util.concurrent.CancellationException` **ist eine
   `RuntimeException`**. Der Retry darf sie **nie** als transienten Fehler behandeln. Die Reihenfolge
   im Wrapper ist zwingend `catch (CancellationException) → rethrow` **vor** `catch (RuntimeException)
   → ggf. Retry`. Nur „echte" transiente `RuntimeException` (Netzwerk/5xx/Timeout, inkl.
   `RateLimitException`) werden wiederholt.

**B. Guthaben & Stopp**
2. `credit` **lädt sich mit jeder erfolgreichen AI-Antwort auf** (+1, max 10).
3. Am Anfang ist `credit` **0** → der **erste** Fehler bubbelt **sofort** hoch (kein Retry).
4. Bei einem AI-Fehler: wenn **`retryCount >= credit`** → kein Guthaben mehr → **hochwerfen** (die
   bestehende `AIChatView.handleChatException` loggt die geworfene `RuntimeException` schon als WARN —
   hier **nicht** doppelt loggen). Sonst `retryCount++`, warten (Backoff), erneut versuchen.

**C. Backoff (linear, gedeckelt)**
5. `wait = Math.min(retryCount * 10s, maxWait)` — **linear**, kein `*2`. `maxWait` = **5min** in
   Produktion, im Test setzbar (z. B. **100ms**).
6. `retryCount` (und damit der Backoff) startet nach jedem erfolgreichen Call wieder bei 0.

**D. Warten & Abbruch (während des Backoffs) — nur Cancel**
7. Gewartet wird in Häppchen: pro Schleifendurchlauf `Thread.sleep(Math.min(1s, restWait))` — der
   `min(1s, …)` ist entscheidend, sonst hängt ein Test mit `maxWait=100ms` an einem 1s-Sleep. Zwischen
   den Häppchen (also ≤1s) wird geprüft, ob **abgebrochen (cancel)** wurde.
8. Es gibt **zwei Cancel-Quellen**, beide enden ohne Retry:
   - **(a) `bridge.call` wirft mitten im Stream `CancellationException`** (User drückt Stop während des
     Requests). Diese ist schon eine `CancellationException` und trägt keine transiente Root → einfach
     **durchreichen** (greift die Klassifikation aus A1b).
   - **(b) Cancel während unserer Backoff-Wartezeit** (zwischen zwei Versuchen): raus mit einer **neuen
     `CancellationException`**, die die zuletzt gefangene **transiente Root-Exception als Ursache**
     mitträgt. Diese Root wird **im Wrapper geloggt** (WARN) — nötig, weil
     `AIChatView.handleChatException` `CancellationException` still verschluckt (Z.723–725), sonst wäre
     die Root weg.
   - **Sonderfall Teilantwort:** bei Cancel *mit* schon gestreamter Teilantwort **wirft**
     `StreamingBridge` nicht, sondern **gibt die Antwort zurück** (Z.84) → der Wrapper wertet das als
     Erfolg, `executeLoop` bricht direkt danach auf `isCanceled()` ab.
   In allen Fällen endet der Turn wie heute — gequeuete Nachrichten werden ins Memory gedrained, die
   nächste Sendung nimmt sie auf.
9. Eine **während des Waits gesendete Chat-Nachricht** wird nur **gequeued** (heutiges Verhalten),
   sie bricht den Wait **nicht** ab. Will der User einen hängenden Retry loswerden → **Cancel**.
   Bewusst einfach: kein Auto-Abbruch, kein re-entry-Sonderpfad. (Relaxt die ursprüngliche
   Anforderung „neue Nachricht bricht Wait ab".)

**E. Sichtbarkeit**
10. Ein wartender Retry meldet sich über **`onProblem`** (englisch), zeigt `retryCount`, die
    `e.getMessage()` und den Stop-Hinweis:
    `API error — attempt 3, retrying in 30s. <e.getMessage()> · Use Stop to cancel.`
    `e.getMessage()` wird **auf eine Zeile getrimmt**; bei `null` der **Exception-Klassenname**
    (kein „…: null", kein Wall-of-Text). „Stop" = der Stop-Button (Tooltip „Stop current request").

## BDD (Entwurf)

```
GIVEN das Guthaben ist 0 (Turn-Start)
WHEN der erste API-Call einen transienten Fehler wirft
THEN bubbelt der Fehler sofort hoch (kein Retry)

GIVEN es gab schon k erfolgreiche Antworten (credit = min(k,10))
WHEN ein API-Call fehlschlägt und retryCount < credit
THEN wird gewartet (retryCount·10s, gedeckelt auf maxWait) und erneut versucht
AND  der User sieht "API-Fehler — Versuch n …" via onProblem
AND  ein erfolgreicher Call setzt retryCount auf 0 und lädt credit +1 (max 10)

GIVEN eine Tool-Ausführung wirft einen Fehler
THEN wird NICHT wiederholt (nur der AI-Request ist gewrappt)

GIVEN wir warten gerade auf einen Retry
WHEN der User abbricht (cancel)
THEN endet das Warten binnen ~1s mit CancellationException (Root-Exception als Ursache, geloggt)
AND  die Queue wird wie heute ins Memory gedrained (nächste Sendung nimmt sie auf)

GIVEN wir warten gerade auf einen Retry
WHEN der User eine neue Chat-Nachricht schickt
THEN wird sie nur gequeued; der Wait läuft weiter (Abbruch nur über Cancel)
```

## Umsetzungs-Naht (beschlossen)

- Der Retry (Klassifikation + Backoff + credit/retryCount + 1s-Cancel-Poll) sitzt in einer **eigenen
  kleinen Klasse `ApiRetry`** (Package `org.sterl.llmpeon.streaming`, neben `StreamingBridge`). Sie
  hält `credit`, `retryCount`, `maxWaitMs` und den `monitor` und bietet **eine** Methode:
  `ChatResponse call(Supplier<ChatResponse> aiCall)`. `ToolService.executeLoop` **und**
  `AbstractAgent.call` bleiben **unangetastet**; Tool-Ausführung liegt außerhalb → kein Tool-Retry.
- `ToolLoopRequest.call` wird zur **Einzeile** und delegiert an `ApiRetry`:
  ```java
  public ChatResponse call(ChatRequest r) {
      return retry.call(() -> bridge.call(chatModel.getChatModel(), r, monitor));
  }
  ```
  `ApiRetry` ist ein Feld auf dem `ToolLoopRequest` (per `@Builder`, Default `new ApiRetry(monitor)`),
  bekommt denselben `monitor`. So bleibt der Retry-Zustand (credit/retryCount) **pro Turn** — das
  `ToolLoopRequest` wird je User-Nachricht in `AbstractAgent.doCall` frisch gebaut, ein neuer Turn
  startet von Natur aus bei `credit=0`.
- **Testbarkeit (der eigentliche Gewinn):** `ApiRetry` ist **ohne `StreamingChatModel`/`StreamingBridge`
  testbar** — der `Supplier<ChatResponse>` ist im Unit-Test ein Stub, der k-mal wirft und dann liefert.
  `maxWaitMs` im Test klein (100ms). Der `monitor` liefert `isCanceled()` + fängt `onProblem` ab.
- Der Wait nutzt nur **`monitor.isCanceled()`** — keine neue Injection, keine Agent-Änderung.
- Vorbild fürs Sekunden-Polling: `StreamingBridge` pollt den Latch heute schon alle 1500ms.
- `isWorking`-Abbruch, „neue Nachricht bricht Wait ab" und `*2`-Backoff **gestrichen**.

## Offene Entscheidungen

Alle geklärt. (D1 = +1/Erfolg; D2 = Budget pro Turn — jetzt in `ApiRetry` (Feld am `ToolLoopRequest`);
Stopp = `retryCount >= credit`; D3 = linearer Backoff, `maxWait` 5min/100ms-Test; D4 = alle Agenten &
Kontexte; D5 = englische Meldung mit `e.getMessage()` + Stop-Hinweis; D6 = eigene Klasse `ApiRetry`
statt inline, für Testbarkeit.)

## Review: Konflikte, Fallstricke, Vereinfachungen (vor Umsetzung geprüft)

- **Cancel-Logging gehört in den Wrapper** (Fallstrick, s. B4/D8): UI verschluckt `CancellationException`
  still → Root sonst weg. Give-up-Fall loggt die UI schon, dort nicht doppeln.
- **Erster Fehler wird nie retryt — bewusst.** Der Retry greift erst, wenn `credit > 0`, also nach
  ≥1 erfolgreichem Call im selben Turn. Ein kalter Netzwerkfehler beim allerersten Call bubbelt sofort
  (verdiente Geduld). Akzeptierter Trade-off, kein Bug.
- **`credit`/`retryCount`/`maxWaitMs` liegen in `ApiRetry`**, nicht am `ToolLoopRequest`. Getestet wird
  `ApiRetry` direkt mit einem `Supplier`-Stub (kein `StreamingChatModel` nötig) — deutlich einfacher als
  der heutige Weg über echte `StreamingBridge` + Mock-Model.
- **Cancel ≠ transienter Fehler (Fallstrick, s. A1b):** `CancellationException extends RuntimeException`
  → im Wrapper zwingend `catch (CancellationException) → rethrow` **vor** `catch (RuntimeException)`,
  sonst würde ein Cancel fälschlich retryt.
- **`StreamingBridge` ist re-call-sicher**: resettet Latch/Refs am Anfang jedes `call()` (Z.56–60) →
  erneutes `bridge.call` nach einem Fehler ist unbedenklich (nichts wurde committet, kein halbes Bubble).
- **Rate-Limit wird jetzt mit-retryt**: `RateLimitException` ist eine `RuntimeException` → landet im
  Retry (gewollt, transient). Erst wenn erschöpft, greift die bestehende „API rate limit!"-Meldung.
- **Kein Konflikt mit den oberen Schichten**: gibt der Wrapper irgendwann eine Antwort zurück, läuft
  `executeLoop` normal weiter; wirft er (give-up/cancel), nimmt der bestehende Fehlerpfad
  (`AbstractAgent.call` drain + rethrow → UI) über — keine Änderung nötig.
- **Cancel-mit-Teilantwort**: `StreamingBridge` gibt bei Cancel mit vorhandener Teilantwort die Antwort
  **zurück** statt zu werfen (Z.84) → Wrapper wertet das als Erfolg, `executeLoop` bricht danach eh auf
  `isCanceled()` ab. Kein Sonderfall nötig.
- **Zwei Zähler bleiben minimal**: `credit` (wächst, Deckel) und `retryCount` (Serie, resettet) bewegen
  sich gegenläufig und lassen sich nicht sinnvoll zu einem verschmelzen.

## Test-Plan (Regression)

**Vorhandene Fehler-Abdeckung:** heute wird ein API-Fehler/Cancel nur *indirekt* getestet
(`StreamMock.buildMock` → `handler.onError`; `AbstractAgentTest.testAbortAddsMessageBeforeThrowing`
wirft `CancellationException`, prüft aber **Queue-Drain**, nicht Retry). **Kein Test deckt Retry ab →
nichts zu mergen.** Die neue Klasse sammelt das an einem Ort.

**Neu: `ApiRetryTest`** (plain JUnit5, `org.sterl.llmpeon.streaming`) — testet `ApiRetry` direkt mit
einem `Supplier<ChatResponse>`-Stub und einem Test-`AiMonitor` (fängt `onProblem`, steuert
`isCanceled()`). `maxWaitMs=100`, `@Timeout` auf jeden Test (der Cancel-Test würde sonst bei einem Bug
hängen statt zu failen):

- **Vorladen nötig:** Retry greift erst ab `credit > 0`. Stub: k-mal Erfolg (lädt `credit`), dann
  Fehler → Retry → Erfolg. Erwartung: Ergebnis nach Retries, `onProblem` je Retry.
- **Erster Fehler bubbelt:** frisch (`credit=0`), Stub wirft sofort → Exception hoch, **kein** Retry,
  **kein** `onProblem`.
- **Erschöpft:** nach Vorladen dauerhaft werfend → nach `retryCount >= credit` fliegt der Fehler hoch.
- **Cancel wird nie retryt:** Stub wirft `CancellationException` → fliegt sofort hoch, **kein** Retry
  (deckt den A1b-Fallstrick ab).
- **Cancel im Wait:** Monitor meldet nach dem ersten Backoff-Häppchen `isCanceled()=true` → binnen ≤1s
  neue `CancellationException` mit letzter transienter Root als Ursache, Root im Log.

**In `ToolServiceTest` (struktureller Blackbox-Test):** ein werfendes Tool wird **nicht** wiederholt —
belegt, dass der Wrap nur um den AI-Call sitzt (Tool-Fehler laufen gar nicht durch `ApiRetry`).

---
idPrefix: CT
---

# Compact-Lock — User-getriggerter Compact verhält sich wie ein Turn

> **Status:** ✅ gebaut (2026-09-22, gemerged auf main) — Scope wie entschieden: **nur der Lock +
> Follow-up-Trigger** (Paul: „Bauen tun wir aber nur den fix also den ‚lock' für compact");
> [ADR-0052](../../docs/adr/0052-compact-uses-working-flag.md). Die damals mitentschiedene Anzeige
> („bleibt auf `working`") ist 2026-10-23 revidiert — siehe R-CT-4 und den ADR-0052-Nachtrag.
> **Verifikation 2026-10-18:** R-CT-1…3 und UC-CT-1/2/4 sind automatisiert (Marker in
> `AbstractAgentTest`); UC-CT-3/5 sind **manueller Smoke — offen (Paul)**.
> **2026-10-27:** R-CT-7 revidiert (❌) — Chat-Rebuild allein über `onCompactCompleted(agentName)` statt
> Aufschub auf `onCallStart` (Regression 2.15.0: Chat wurde beim Start eines delegierten Sklaven geleert).
> **2026-10-23 (✅ done, Nachtzyklus):** R-CT-4 revidiert (eigener Compact-Status 🟡 statt „working reicht") und
> R-CT-5…7 gebaut — Compact-UI-Sichtbarkeit (unten). UC-CT-6 ist mit dem R-CT-4-Bau
> automatisiert (Marker in `AiAgentStatusModelTest`, `AbstractAgentTest`, `CompactRosterStatusTest`).
> Verwandt: [chat-job-lifecycle.md](../../org.sterl.llmpeon/docs/chat-job-lifecycle.md) (Backlog-Race, hier behoben),
> [queued-user-messages.md](queued-user-messages.md), [agenten-status-im-header.md](../../org.sterl.llmpeon/docs/agenten-status-im-header.md).

## Ziel

Während ein Agent kompaktiert (User-Compact über Header/Actions-Bar, Da Boss wie Sklave), ist er
**`working`** — der Roster zeigt ihn als aktiv, Eingaben werden gequeued statt parallel verarbeitet,
und nach dem Compact werden gequekte Nachrichten verarbeitet. Damit ist der dokumentierte
Memory-Race (Send während Compact = paralleler `call()` gegen dieselbe Memory, während
`compact()` sie leert) **strukturell behoben** — nicht abgefangen.

## Hintergrund (IST vor dem Fix)

`AbstractAgent.compact()` berührte weder `working` noch `messageQueue`. Der agent-initiierte
Compact (Auto-Compact vor Turn, `CompactSessionTool`) läuft bereits **innerhalb** von `call()`
mit `working == true` — dort ist die Ziel-Semantik (queue → nach Compact FIFO) heute schon korrekt.
Die Lücke war ausschließlich der User-Compact (`AIChatView.doCompressContext` /
`doCompressAgent`): Job ohne Flag → Send geht als paralleler Turn durch, obwohl `compact()`
gleichzeitig `memory.clear()` macht.

## Regeln

### R-CT-1 — Compact belegt das `working`-Flag (CAS-bedingt) ✅

`AbstractAgent.compact()` holt das `working`-Flag per CAS **nur, wenn es frei ist** (User-Compact-Pfad)
und gibt es im `finally` nur zurück, wenn **es** es geholt hat. Der In-Loop-Compact (innerhalb von
`call()`) verändert das Flag nicht — der Turn hält es ohnehin bis zum Ende. Ein paralleler `call()`
während eines User-Compacts scheitert am CAS und wird gequeued — kein zweiter Pfad, keine neue
State-Maschine.

> **WEIL** (Paul 2026-09-22): „Wichtig für mir ist, während ein Agent ein Compact macht ist er
> working!" — der Flag-Wiederverwendung schlagen einen neuen `compacting`-Zustand: Roster-Aktivität,
> Queue-Guard und Button-Disable (über `AiAgentStatusModel.compactEnabled`) fallen gratis heraus,
> und das Race-Fenster ist per CAS geschlossen statt per Guard abgefangen. Ein Future-Chain
> (`CompletableFuture` auf das Compact-Job-Ende) wurde bewusst verworfen: der Send-Pfad ist
> Job-basiert, die Queue-Ordnung (Burst-Join, Drain-on-Abort, Compact-Überlebend,
> [ADR-0017](../../docs/adr/0017-atomic-ui-chaining.md)/[0018](../../docs/adr/0018-abort-path-parity.md)) existiert und ist
> getestet — ein Future würde sie doppelt bauen.

#### UC-CT-1 — userCompactHoldsWorkingFlag ✅
- GIVEN idle Agent WHEN User-Compact läuft THEN `isWorking()` ist `true` während des Compacts und
  `false` danach — **auch bei fehlgeschlagenem Compact** (Release im `finally`).
- *(Automatisiert: `AbstractAgentTest.compactHoldsWorkingFlagDuringCompressorCall` +
  `compactFailedReleasesWorkingFlag`.)*

#### UC-CT-2 — compactDoesNotStealWorkingFromCall ✅
- GIVEN `call()` hält `working` (In-Loop-Compact / Auto-Compact) WHEN `compact()` läuft THEN es
  akquiriert **nicht** und released **nicht** — das Flag gehört dem Turn bis zu dessen Ende.
- *(Automatisiert: `AbstractAgentTest.inLoopCompactDoesNotReleaseTurnsWorkingFlag`.)*

### R-CT-2 — Eingabe während Compact wird gequeued, nicht parallel verarbeitet ✅

Send während des User-Compacts des aktiven Agenten: Nachricht in die `UserMessageQueue` (bestehender
`Skip`-Pfad in `resolveOutgoingMessage` greift, weil `isWorking()` jetzt `true` ist), Ack-Message
(„Noted, I will respond as soon as I finished…") im Chat, **kein** `submitAiJob`. Nichts geht an das
LLM, bis der Compact endet. (Die Anzeige im Chat bleibt unverändert — der User sieht den
Compact-Stream.)

#### UC-CT-3 — sendDuringCompactQueuesNoParallelTurn ❌ (manueller Smoke offen, Paul)
- GIVEN aktiver Agent kompaktiert (User-Compact) WHEN User sendet THEN Nachricht landet in der
  Queue mit Ack, kein paralleler `call()` — verifizierbar am Roster/Job (kein zweiter Turn-Job).

### R-CT-3 — Follow-up-Turn nach dem Compact — auch bei Fehler ✅

Endet ein User-Compact-Job (Erfolg **oder** Fehler) und hat der kompaktierte (aktive) Agent
gequekte Nachrichten, startet das UI einen **Follow-up-Turn**, der die Queue FIFO verarbeitet (der
`call()`-Einstieg drainiert sie). Unlock des Compact-Jobs und Follow-up-Submit laufen im **selben
UI-Thread-Runnable** (kein Unlock-Fenster). Paul 2026-09-22: „Ja, die Nachricht wird dann trotzdem
verarbeitet" — ein fehlgeschlagener Compact (Rate-Limit) ist kein Abort der Queue; die
Drain-on-Abort-Semantik greift hier nicht.

> **WEIL** (Paul 2026-09-22): Eine während des Compacts geschriebene Nachricht soll **nach dem
> Compact, eine nach der anderen** verarbeitet werden — nicht beim nächsten manuellen Turn
> „wieder aufwachen". Der Trigger ist die einzige neue Stelle; alles andere (FIFO, Ack, Drain)
> existiert getestet.

#### UC-CT-4 — queuedMessageProcessedAfterCompact ✅
- GIVEN Nachricht während User-Compact gequeued WHEN Compact endet THEN Follow-up-Turn verarbeitet
  sie (FIFO, eine nach der anderen, `[Queued Message]`-Markierung).
- *(Automatisiert: `AbstractAgentTest.callNullInitialWithQueuedProcessesQueueAsPayload` — Queue als
  Payload + Marker; der UI-Trigger selbst ist Plugin-Code und nicht headless testbar.)*

#### UC-CT-5 — failedCompactStillProcessesQueue ❌ (manueller Smoke offen, Paul)
- GIVEN gequekte Nachricht und User-Compact schlägt fehl WHEN der Job endet THEN die gequekte
  Nachricht wird trotzdem verarbeitet — kein Verlust, kein „N queued messages preserved"-Pfad.

### R-CT-4 — Eigener Compact-Status im Roster ✅ done (2026-10-23, Nachtzyklus — revidiert die 2026-09-22-Entscheidung)

Während ein Agent kompaktiert, zeigt seine Roster-Zeile einen **eigenen Compact-Status** (🟡-Präfix
in `AiAgentStatusWidget.text()`, zweiter Anzeige-Zustand `compacting`) — nicht nur das generische
🟢 `working`. Der Compact ist damit im Roster direkt erkennbar und vom regulären Turn unterscheidbar.
Die Blatt-Regel bleibt unverändert: ein kompaktierender Sklave lässt Da Boss nicht leuchten
(🟡 zählt für die Blatt-Regel als **busy** — hält Da Boss dunkel —, aber nicht als `working`:
kein 🟢 durch Compact; die kompaktierende Zeile selbst zeigt 🟡 statt 🟢).

> **WEIL** (Paul 2026-10-23): „Wir wollen den Status vom Compact setzen." Die alte Entscheidung
> (2026-09-22: „Grün ist vollkommen okay — er arbeitet ja", **kein** separater Compact-State) hat
> den Compact unsichtbar gemacht — genau deshalb wurde sie falsch abgebogen (R-CC-14-Konsequenz:
> minutenlang kein UI-Feedback). Der Compact ist ein eigener Vorgang und bekommt einen eigenen
> Status. Löst die 🟡-Offene-Punkte-Zeile (2026-09-22) ein.

#### UC-CT-6 — compactShowsDedicatedStatusInRoster ✅ (SWT-Visual: manueller Smoke offen, Paul)
- GIVEN ein Team-Mitglied kompaktiert WHEN der Roster refresht THEN zeigt die Zeile dieses
  Agenten den Compact-Status (🟡) statt nur working (🟢). *(Automatisiert: der Roster-Pull liest das
  live Flag — `AiAgentStatusModelTest.compactShowsDedicatedStatusInRoster` (Blatt-Regel),
  `AbstractAgentTest` (`isCompactingDuringCompressorCall`, `compactingClearedOnFailedCompact`,
  `skipDoesNotFlagCompacting`), SWT-Darstellung `CompactRosterStatusTest` auf dem bestehenden
  `AbstractSwtUiTest`-Harness.)*

---

## Compact-UI-Sichtbarkeit (2026-10-23, Paul)

> Anlass: R-CC-14 machte `CompactService` monitor-frei — Konsequenz war, dass während des
> (u. U. minutenlangen) Compressor-Calls **kein** UI-Feedback kommt und die Start-Zeile den
> Agenten nicht nennt („WTF, ich sehe beim Compact nichts mehr"). Der Service bleibt monitor-frei;
> der Feedback-Kanal läuft über den Caller-Monitor. Verwandt: [compact.md R-CC-14](compact.md)
> (Korrektur 2026-10-23), [chat-job-lifecycle.md](../../org.sterl.llmpeon/docs/chat-job-lifecycle.md).

### R-CT-5 — Der Compressor ist im UI als arbeitend sichtbar ✅ done (2026-10-23, Nachtzyklus; Streaming-Teil 2026-10-24 revidiert → [R-CT-8](#r-ct-8--compact-meldet-start-und-ende-wie-ein-langlaufendes-tool-streaming-sichtbar-nie-im-log--specified-2026-10-24-paul-smoke))

Der Caller-Monitor erhält das Request-Start-Event (`onChatMessage`) des Compressor-Calls — die
Chat-View zeigt während des Compact denselben Working-Zustand wie vor jedem anderen Modell-Call
(„waiting for AI…"); der Roster zeigt den eigenen Compact-Status (R-CT-4).

> **Revision 2026-10-24 (Paul-Smoke, compact-agents-issue.md):** die 2026-10-23-Klausel
> „Streaming-Chunks und Response-Text bleiben log-only" ist gestrichen — der minutenlange stille
> Balken liest sich als Hänger („das hatten wir schon viel besser"). Tokens streamen sichtbar ins
> UI und gehen nie ins Log: R-CT-8. Das alte UC-CT-8 (`compressorStreamingStaysLogOnly`) entfällt
> mit der Klausel; sein Test wird im R-CT-8-Inkrement ersetzt.

- GIVEN ein Compact läuft WHEN der Compressor-Call startet THEN zeigt die Chat-View den
  Working-Zustand, bis der Compact endet. *(UC-CT-7)*

#### UC-CT-7 — compressorShowsWorkingInUi ✅
- GIVEN der aktive Agent kompaktiert WHEN der Compressor-Call läuft THEN zeigt die Chat-View den
  Working-Zustand (`onChatMessage` am Caller-Monitor).

### R-CT-8 — Compact meldet Start und Ende wie ein langlaufendes Tool; Streaming sichtbar, nie im Log ✅ done (2026-10-24, Paul-Smoke)

Der Compact verhält sich im UI wie jeder langlaufende Tool-Call (ShellTool-Muster: Start-`onTool`,
End-`onTool` mit Dauer und chars) — **ohne** Wissen davon, für welchen Agenten er läuft (kein
Root-/Sklaven-Sonderweg, kein Responsibility-Bleed):

- **Start-Zeile** (existiert, R-CT-6): `🗜 Compressing <Agent>: …` via `onTool`.
- **End-Zeile** via `onTool` — die EINE Abschlussmeldung mit Agent-Namen, Ergebnisgröße, Dauer,
  Uhrzeit und chars (CallStats-`suffix()`-Muster, ADR-0053), z. B. `🗜 Da Mek compacted: 6 → 2
  messages, ~969 → ~210 tokens (4s, 09:40) (1200 chars)`. Die doppelte Meldung `<Agent> compact.`
  (AgentsTool `reportAction` nach `subAgentCompact`) entfällt; die Pre-Turn-Abschlusszeile nutzt
  dieselbe End-Zeile. **Kante zu [R-CC-16](compact.md)** (externes Review 2026-10-24): R-CC-16
  (❌ specified) ergänzt die End-Zeile um die vom Compressor-LLM gemeldete Input-Größe (nullable,
  neben der Stager-Estimate) — die End-Zeile ist damit bewusst **erweiterbar**; R-CC-16 wird als
  Erweiterung derselben Zeile umgesetzt, nicht als zweite Meldung (kein drittes Format).
- **Streaming:** Compressor-Tokens erscheinen live in der Chat-View des Callers — wie beim
  normalen Agent-Streaming; Chunks und Response-Text werden **nicht** ins Log geschrieben
  (das Response-/Chunk-Logging im Compact-Pfad entfällt; der R-CIB-3-Entry-Debuglog bleibt,
  Level DEBUG).

#### UC-CT-13 — endLineNamesAgentNumbersTimeAndChars ✅
- GIVEN ein Compact eines anderen Agenten läuft WHEN er erfolgreich endet THEN erscheint im
  Caller-Chat genau EINE End-Zeile mit Agent-Name, Dauer/Uhrzeit (CallStats-`suffix()`) und chars —
  keine zweite `<Agent> compact.`-Meldung. *(Tests: `CompactFeedbackTest.endLineNamesAgentNumbersTimeAndChars`,
  `PoDelegateToolTest.compactDevEmitsExactlyOneEndLine`)*

#### UC-CT-14 — compressorEventsStreamToCallerAndStayOutOfTheLog ✅
- GIVEN ein Compact läuft WHEN der Compressor streamt THEN erscheinen die Tokens live in der
  Chat-View des Callers und nichts davon im Log. *(Tests: `CompactServiceTest.compressorEventsStreamToCallerAndStayOutOfTheLog`,
  `AbstractAgentCompactResultTest.compressorStreamingReachesCallerMonitor`)*

#### UC-CT-15 — failedCompactEmitsNoEndLine ✅
- GIVEN ein Compact scheitert oder bricht ab WHEN der Lauf endet THEN erscheint die
  Problem-Meldung wie heute und keine Erfolgs-End-Zeile. *(Test: `AbstractAgentCompactResultTest.failedCompactEmitsNoEndLine`)*

### R-CT-6 — Compact-Ereignisse nennen den Agenten ✅ done (2026-10-23, Nachtzyklus)

Start- und Ergebnis-Emission JEDES Compact-Triggers (Button aktiv, Button Sklave, `compactSession`,
Pre-Turn, In-Loop-Direct) enthält den Agent-Namen. Heute nennen Pre-Turn, Direct-Compact und
`compactSession` ihn — die Start-Zeile aus `AbstractAgent.compact` nicht.

- GIVEN ein Compact über einen beliebigen Trigger startet WHEN die Start-Zeile emittiert wird THEN
  nennt sie den Agent-Namen („🗜 Compressing \<Agent\> …"). *(UC-CT-9)*

#### UC-CT-9 — compactStartLineNamesAgent ✅
- GIVEN ein Compact startet über einen beliebigen Trigger WHEN die Start-Zeile emittiert wird THEN
  enthält sie den Agent-Namen.

### R-CT-7 — Chat-View-Reset bei Compact des aktiven Agenten ❌ specified (revidiert 2026-10-27, Paul — Regression „Chat wird bei Delegation geleert")

Kompaktiert der im UI **aktive** Agent — egal welcher Trigger (Button, `compactSession` im Loop,
Pre-Turn, In-Loop-Direct) — baut die Chat-View nach erfolgreichem Compact aus dem Memory neu auf:
der alte Verlauf verlässt das Chat-HTML (es läuft nicht voll), die re-geseedete Summary bleibt
sichtbar. Sklaven-Compact: **kein** Reset (fremdes Memory — der Roster zeigt den Kontext-Drop).
Gilt für **jede** Chat-View (Eclipse-Plugin und später die Web-View der AI Factory).

**Auslöser ist allein das Compact-Ende-Event mit Absender:** `onCompactCompleted(agentName, …)`
nennt den kompaktierenden Agenten. Die View baut neu auf, wenn `agentName` der im UI aktive Agent
ist und der Compact erfolgreich war — sofort, egal ob innerhalb oder außerhalb eines Turns. Andere
Monitor-Events (`onCallStart`, `onChatMessage`) tragen keinen Absender und lösen **nie** einen
Rebuild aus: ein delegierter Sklave feuert sie auf dem Monitor des Delegierenden. Kein Aufschub,
kein Merker, keine Memory-Heuristik.

**Zeitpunkt-Pflicht des Agenten:** Das Event feuert erst, wenn das Memory den Stand zeigt, den der
Chat zeigen soll. Beim Pre-Turn-Compact heißt das: **nach** dem Anfügen der frischen Frage — sonst
fehlte sie im neu aufgebauten Chat.

> **Ausblick 🚧 (Paul 2026-10-27):** Künftig soll der Monitor bei **jedem** Event den aktuellen
> Agenten kennen. Nicht Teil dieser Regel; Design offen ([agent-origin-proposal](../../proposals/agent-origin-proposal.md)).

- GIVEN der aktive Agent kompaktiert per Button erfolgreich WHEN der Compact endet THEN Rebuild der
  Chat-View aus dem Memory (Summary sichtbar, alter Verlauf weg). *(UC-CT-10)*
- GIVEN ein Sklave kompaktiert WHEN er danach weiterarbeitet THEN bleibt die Chat-View unverändert.
  *(UC-CT-11)*
- GIVEN der Compact scheitert (FAILED_EMPTY) WHEN der Job endet THEN kein Rebuild (Memory
  unverändert), der `onProblem`-Hinweis ist sichtbar. *(UC-CT-12)*
- GIVEN der aktive Agent hat im Turn kompaktiert und delegiert danach WHEN der Sklave
  `onCallStart`/`onChatMessage` auf demselben Monitor feuert THEN bleibt die Chat-View unverändert.
  *(UC-CT-17)*

#### UC-CT-10 — mainAgentCompactResetsChatView ❌
- GIVEN der aktive Agent kompaktiert per Button erfolgreich WHEN der Compact endet THEN wird die
  Chat-View aus dem Memory neu aufgebaut.
- GIVEN der aktive Agent kompaktiert im Turn erfolgreich (Pre-Turn, In-Loop, `compactSession`) WHEN
  `onCompactCompleted` mit seinem Namen eintrifft THEN wird die Chat-View sofort aus dem Memory neu
  aufgebaut.

#### UC-CT-16 — preTurnCompactSignalsAfterFreshQuestion ❌
- GIVEN ein Agent mit langem Verlauf und eine frische Frage löst den Pre-Turn-Compact aus WHEN
  `onCompactCompleted` feuert THEN enthält sein Memory zu diesem Zeitpunkt bereits die frische Frage
  — ein Rebuild zeigt Summary **und** Frage.

#### UC-CT-17 — delegatedMemberEventsDoNotResetChatView ❌
- GIVEN der aktive Agent hat im Turn kompaktiert und anschließend delegiert WHEN der Sklave
  `onCallStart`/`onChatMessage` auf dessen Monitor feuert THEN bleibt die Chat-View unverändert.

#### UC-CT-11 — slaveCompactDoesNotResetChatView ✅
- GIVEN ein Sklave kompaktiert WHEN der Compact endet THEN bleibt die Chat-View unverändert.

#### UC-CT-12 — failedCompactKeepsChatView ✅
- GIVEN der Compact des aktiven Agenten scheitert WHEN der Job endet THEN kein Rebuild und der
  `onProblem`-Hinweis ist sichtbar.

> Umsetzungshinweis: `AiMonitor.onCompactCompleted(agentName, result)` — **ohne** `withinTurn`: die View
> unterscheidet Turn/kein Turn nicht mehr, ein ungenutzter Parameter lüde nur zu neuer Sonderlogik
> ein (Paul 2026-10-27). Feuert genau einmal
> pro gelaufenem Compact (COMPACTED **und** FAILED_EMPTY, nie bei SKIPPED_SMALL, nie auf Throw) —
> beim Button-Compact nach der Freigabe des Arbeits-Flags. Es ist der **einzige** Rebuild-Auslöser der View — für Button- und
> Turn-Compact gleich. Der bisherige Aufschub-Pfad (`DEFERRED` + `pendingChatReset` + Flush in
> `onCallStart`) entfällt — genau er hat den Chat bei Delegation geleert. Manueller Smoke: lange
> Session, Pre-Turn-Compact, dann Delegation — Summary + frische Frage bleiben stehen, der Chat wird
> beim Start des Sklaven nicht geleert.



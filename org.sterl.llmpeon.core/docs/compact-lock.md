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
> **2026-10-23:** R-CT-4 revidiert (eigener Compact-Status 🟡 statt „working reicht") und
> R-CT-5…7 ❌ specified — Compact-UI-Sichtbarkeit (unten). UC-CT-6 ist mit dem R-CT-4-Bau
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

### R-CT-4 — Eigener Compact-Status im Roster ❌ specified (Paul 2026-10-23 — revidiert die 2026-09-22-Entscheidung)

Während ein Agent kompaktiert, zeigt seine Roster-Zeile einen **eigenen Compact-Status** (🟡-Präfix
in `AiAgentStatusWidget.text()`, zweiter Anzeige-Zustand `compacting`) — nicht nur das generische
🟢 `working`. Der Compact ist damit im Roster direkt erkennbar und vom regulären Turn unterscheidbar.
Die Blatt-Regel bleibt unverändert: ein kompaktierender Sklave lässt Da Boss nicht leuchten
(🟡 zählt nicht als `working` für das Glow-Decision).

> **WEIL** (Paul 2026-10-23): „Wir wollen den Status vom Compact setzen." Die alte Entscheidung
> (2026-09-22: „Grün ist vollkommen okay — er arbeitet ja", **kein** separater Compact-State) hat
> den Compact unsichtbar gemacht — genau deshalb wurde sie falsch abgebogen (R-CC-14-Konsequenz:
> minutenlang kein UI-Feedback). Der Compact ist ein eigener Vorgang und bekommt einen eigenen
> Status. Löst die 🟡-Offene-Punkte-Zeile (2026-09-22) ein.

#### UC-CT-6 — compactShowsDedicatedStatusInRoster ❌ (manueller Smoke offen, Paul)
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

### R-CT-5 — Der Compressor ist im UI als arbeitend sichtbar ❌ specified (Paul 2026-10-23)

Der Caller-Monitor erhält das Request-Start-Event (`onChatMessage`) des Compressor-Calls — die
Chat-View zeigt während des Compact denselben Working-Zustand wie vor jedem anderen Modell-Call
(„waiting for AI…"); der Roster zeigt den eigenen Compact-Status (R-CT-4). Streaming-Chunks und
Response-Text des Compressors bleiben
log-only (kein Chat-Spam durch interne Tokens — R-CC-14 bleibt für den Text bestehen).

- GIVEN ein Compact läuft WHEN der Compressor-Call startet THEN zeigt die Chat-View den
  Working-Zustand, bis der Compact endet. *(UC-CT-7)*
- GIVEN ein Compact läuft WHEN der Compressor streamt THEN erscheinen keine Chunks und kein
  Response-Text im Chat (nur Log). *(UC-CT-8)*

> Umsetzungshinweis: der Test `compressorChatEventsDoNotReachAgentMonitor` trennt heute ALLE
> Events — er wird auf „nur `onChatMessage` erreicht den Caller-Monitor" verschärft.

#### UC-CT-7 — compressorShowsWorkingInUi ❌
- GIVEN der aktive Agent kompaktiert WHEN der Compressor-Call läuft THEN zeigt die Chat-View den
  Working-Zustand (`onChatMessage` am Caller-Monitor).

#### UC-CT-8 — compressorStreamingStaysLogOnly ❌
- GIVEN ein Compact läuft WHEN der Compressor streamt THEN landet kein Chunk und kein
  Response-Text im Chat.

### R-CT-6 — Compact-Ereignisse nennen den Agenten ❌ specified (Paul 2026-10-23)

Start- und Ergebnis-Emission JEDES Compact-Triggers (Button aktiv, Button Sklave, `compactSession`,
Pre-Turn, In-Loop-Direct) enthält den Agent-Namen. Heute nennen Pre-Turn, Direct-Compact und
`compactSession` ihn — die Start-Zeile aus `AbstractAgent.compact` nicht.

- GIVEN ein Compact über einen beliebigen Trigger startet WHEN die Start-Zeile emittiert wird THEN
  nennt sie den Agent-Namen („🗜 Compressing \<Agent\> …"). *(UC-CT-9)*

#### UC-CT-9 — compactStartLineNamesAgent ❌
- GIVEN ein Compact startet über einen beliebigen Trigger WHEN die Start-Zeile emittiert wird THEN
  enthält sie den Agent-Namen.

### R-CT-7 — Chat-View-Reset bei Compact des aktiven Agenten ❌ specified (Paul 2026-10-23)

Kompaktiert der im UI **aktive** Agent — egal welcher Trigger (Button, `compactSession` im Loop,
Pre-Turn, In-Loop-Direct) — baut die Chat-View nach erfolgreichem Compact aus dem Memory neu auf:
der alte Verlauf verlässt das Chat-HTML (es läuft nicht voll), die re-geseedete Summary bleibt
sichtbar. Sklaven-Compact: **kein** Reset (fremdes Memory — der Roster zeigt den Kontext-Drop).

- GIVEN der aktive Agent kompaktiert erfolgreich WHEN der Compact endet THEN Rebuild der Chat-View
  aus dem Memory (Summary sichtbar, alter Verlauf weg). *(UC-CT-10)*
- GIVEN ein Sklave kompaktiert WHEN der Compact endet THEN kein Rebuild der Chat-View. *(UC-CT-11)*
- GIVEN der Compact scheitert (FAILED_EMPTY) WHEN der Job endet THEN kein Rebuild (Memory
  unverändert), der `onProblem`-Hinweis ist sichtbar. *(UC-CT-12)*

#### UC-CT-10 — mainAgentCompactResetsChatView ❌
- GIVEN der aktive Agent kompaktiert erfolgreich WHEN der Compact endet THEN wird die Chat-View aus
  dem Memory neu aufgebaut.

#### UC-CT-11 — slaveCompactDoesNotResetChatView ❌
- GIVEN ein Sklave kompaktiert WHEN der Compact endet THEN bleibt die Chat-View unverändert.

#### UC-CT-12 — failedCompactKeepsChatView ❌
- GIVEN der Compact des aktiven Agenten scheitert WHEN der Job endet THEN kein Rebuild und der
  `onProblem`-Hinweis ist sichtbar.



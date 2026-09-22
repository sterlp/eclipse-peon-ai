---
idPrefix: CT
---

# Compact-Lock — User-getriggerter Compact verhält sich wie ein Turn

> **Status:** ❌ specified (2026-09-22, Paul) — gebaut wird **nur der Lock + Follow-up-Trigger**
> (Paul: „Bauen tun wir aber nur den fix also den ‚lock' für compact"). Anzeige bleibt auf
> `working`. Verwandt: [chat-job-lifecycle.md](chat-job-lifecycle.md) (Backlog-Race, hier behoben),
> [queued-user-messages.md](queued-user-messages.md), [agenten-status-im-header.md](agenten-status-im-header.md).

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

### R-CT-1 — Compact belegt das `working`-Flag (CAS-bedingt) ❌

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
> [ADR-0017](adr/0017-atomic-ui-chaining.md)/[0018](adr/0018-abort-path-parity.md)) existiert und ist
> getestet — ein Future würde sie doppelt bauen.

#### UC-CT-1 — userCompactHoldsWorkingFlag ❌
- GIVEN idle Agent WHEN User-Compact läuft THEN `isWorking()` ist `true` während des Compacts und
  `false` danach — **auch bei fehlgeschlagenem Compact** (Release im `finally`).

#### UC-CT-2 — compactDoesNotStealWorkingFromCall ❌
- GIVEN `call()` hält `working` (In-Loop-Compact / Auto-Compact) WHEN `compact()` läuft THEN es
  akquiriert **nicht** und released **nicht** — das Flag gehört dem Turn bis zu dessen Ende.

### R-CT-2 — Eingabe während Compact wird gequeued, nicht parallel verarbeitet ❌

Send während des User-Compacts des aktiven Agenten: Nachricht in die `UserMessageQueue` (bestehender
`Skip`-Pfad in `resolveOutgoingMessage` greift, weil `isWorking()` jetzt `true` ist), Ack-Message
(„Noted, I will respond as soon as I finished…") im Chat, **kein** `submitAiJob`. Nichts geht an das
LLM, bis der Compact endet. (Die Anzeige im Chat bleibt unverändert — der User sieht den
Compact-Stream.)

#### UC-CT-3 — sendDuringCompactQueuesNoParallelTurn ❌
- GIVEN aktiver Agent kompaktiert (User-Compact) WHEN User sendet THEN Nachricht landet in der
  Queue mit Ack, kein paralleler `call()` — verifizierbar am Roster/Job (kein zweiter Turn-Job).

### R-CT-3 — Follow-up-Turn nach dem Compact — auch bei Fehler ❌

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

#### UC-CT-4 — queuedMessageProcessedAfterCompact ❌
- GIVEN Nachricht während User-Compact gequeued WHEN Compact endet THEN Follow-up-Turn verarbeitet
  sie (FIFO, eine nach der anderen, `[Queued Message]`-Markierung).

#### UC-CT-5 — failedCompactStillProcessesQueue ❌
- GIVEN gequekte Nachricht und User-Compact schlägt fehl WHEN der Job endet THEN die gequekte
  Nachricht wird trotzdem verarbeitet — kein Verlust, kein „N queued messages preserved"-Pfad.

### R-CT-4 — Anzeige: `working` bleibt der Aktivitätsindikator ❌

Der Roster zeigt den kompaktierenden Agenten über denselben `working`-Status wie einen arbeitenden
Agenten (🟢, Blatt-Regel unverändert — ein kompaktierender Sklave lässt Da Boss nicht leuchten).
**Kein** separates „compacting"-UI-State, kein neuer Parameter.

> **WEIL** (Paul 2026-09-22): „Grün ist vollkommen okay — er arbeitet ja." Ein gelber Ball (🟡) als
> separater Compact-Indikator wäre ein Mini-Improvement, ist aber **bewusst nicht gebaut** —
> aufgenommen in [open-points.md](open-points.md).

#### UC-CT-6 — compactShowsAgentActiveInRoster ❌
- GIVEN User-Compact auf einem Team-Mitglied WHEN der Roster refresht THEN die Zeile dieses
  Agenten zeigt working (🟢) — kein separater Zustand, kein neuer UI-State. *(Manuelle Verifikation —
  Roster-Pull liest das live Flag; ein SWT-Test-Harness existiert nicht.)*
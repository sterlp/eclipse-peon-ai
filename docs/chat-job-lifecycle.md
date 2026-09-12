# Chat Job Lifecycle — Stop/Lock/Monitor

UI-seitiger Lifecycle eines Agent-Turns: Job-Submit → Lock → `monitorRef` → Job-`finally`
→ Unlock. Stop-Button-Semantik._owner: AIChatView-Job-Mechanik (plugin).

**Status: ✅ gebaut (2026-09-10, `ff69a3d`/`9cbb355`)** — R-ST1 (In-Flight-Counter statt
Ticket), R-ST2 (Graceful Stop, kein Code-Change — Design-Decision bewusst), R-ST3
(Turn-INFO-Logging) inkl. Counter-Invarianten; Design-Review Da Thinka + Da-Dok-Review
(verifiziert gegen Code). Verifikation manuell (SWT-Präzedenz R-UI1/R-MCP3/R-ML1a) —
User-Smoke-Steps 1–7 im Plan §7.

## IST (2026-09-10) — Bug „Stop-Fenster"

- Stop-Enable **nur** über `UserInputWidget.isWorking(boolean)` — einziger Call-Site:
  `AIChatView.lockWhileWorking(boolean)` → `chatInput.isWorking(value)`.
- `lock(true)`: `submitAiJob(...)` und `doCompressContext(...)` — **vor** `Job.create(...).schedule()`.
- `handleDoneChatResponse` im Job-`finally`: `monitorRef.set(new NullProgressMonitor())` läuft
  **synchron auf dem Job-Thread**; der Unlock (`lock(false)`, Live-Status-Hide, updateCompact)
  läuft **asynchron via `EclipseUtil.runInUiThread`** (kann durch Rendering verzögert sein).
- `monitorRef`: gemeinsame `AtomicReference<IProgressMonitor>` über alle Jobs. Stop cancelt,
  was gerade drin steht; `isCanceled()` liest sie (`StreamingBridge`-Poll, `ApiRetry.waitOrCancel`).
- Roster 🟢 pull-basiert (`agent.isWorking()`), unabhängig vom Lock-State.

**Bug (User 2026-09-10, pre-existing):** Run lebt (grüner Ball korrekt), Eingabe nutzbar
(Queuing ist Baseline), aber **Stop tot und wirkungslos**. Ursache: Clobber-Race — ein stale
Job-`finally` (verzögert durch UI-Render) resetet `monitorRef` + entlockt die UI, während der
nächste Run schon lebt. Betroffen-Fenster: bis zum nächsten Send. Logs rotieren die Evidence
weg (10 Backups) — Incident nicht mehr rekonstruierbar; Diagnose ist Code-Evidenz.

## SOLL

### R-ST1 — In-Flight-Counter statt Ticket (Design-Review Da Thinka, 2026-09-10)

`submitAiJob`/`doCompressContext` inkrementieren synchron im UI-Thread einen
`AtomicInteger inFlight` (paarig zum `lock(true)`, vor dem Schedule). Das Job-`finally`
entscheidet **innerhalb des `runInUiThread`-Runnables** (UI-serialisiert mit dem Submit):

```java
EclipseUtil.runInUiThread(parent, () -> {
    if (inFlight.decrementAndGet() != 0) {
        LOG.info("turn finally skipped: agent=" + agentName + " (run still in flight)");
        return; // kein Reset, kein Unlock — ein neuerer Run hält den Lock
    }
    monitorRef.set(new NullProgressMonitor());
    lockWhileWorking(false);
    ... // updateCompact, hideLiveStatus — nur noch hier, nie stale
});
```

- **WEIL Generation-Ticket verworfen:** (1) Entscheidung muss im UI-Runnable fallen —
  `monitorRef.set` im IST läuft auf dem Job-Thread, ein Ticket-Check im finally selbst bleibt
  eine Lücke. (2) Der Counter schließt zusätzlich das **Phantom-Job-Loch**: Doppel-Send im
  Schedule-Fenster → Job 2 wird gecasht (`working.compareAndSet` lehnt ab, queued sofort) →
  sein finally hätte das *aktuelle* Ticket und würde unlocken, während Job 1 lebt. „Latest
  ticket" ≠ „letzter Finisher"; der Counter paart Submit und Finally exakt.
- **doCompressContext:** auch der vorgeschaltete asyncExec (Memory-Replay + hideLiveStatus)
  gehört unter denselben Guard, sonst replays ein stale Compress in den Chat des lebenden Runs.
- **Keine Core-Klasse** (`TurnToken`/RunGate wäre Overkill — testet AtomicInteger, nicht unsere
  Logik; SWT-Wiring ist ohnehin untestbar, Präzedenz R-UI1/R-MCP3). Regression-Guard-Rolle:
  BDDs hier + R-ST3-Log-Zeile als Live-Evidence.
- **Kleines dokumentiertes Rest-Fenster (nicht gefixt):** zwischen Submit (Counter gezogen,
  `lock(true)`) und Job-Body-Start zeigt `monitorRef` noch alt/Null → Stop verpufft in diesem
  ms-Fenster. Fix (pre-body-Monitor) = Overkill.

**Counter-Invarianten (User-OBACHT, 2026-09-10):**
- **1:1-Paarung:** exakt ein `increment` pro Submit (UI-Thread, vor Schedule) ↔ exakt ein
  `decrement` pro Job-Lifecycle (finally-UI-Runnable, `try/finally` garantiert). Der
  Cancel-Pfad ist kein Sonderpfad: Stop bricht den Run ab → finally läuft → decrement →
  Counter zurück auf 0 → Reset+Unlock wie bei normalem Ende.
- **Nie unter 0:** das Ergebnis des `decrementAndGet()` ist `<= 0` → aufräumen (UI muss
  nutzbar bleiben, fail-open). Ein Ergebnis `< 0` ist ein **unbalancierter Counter**
  (Programmierfehler) → zusätzlich ERROR-Log. Ein Springen von `-1` auf `0` darf nie
  passieren, weil Submit und Finally exakt gepaart sind.

```
GIVEN Run aktiv, Counter = 1
WHEN der User Stop drückt und der Run abbricht
THEN das finally läuft, decrement → 0, Reset + Unlock wie bei normalem Ende

GIVEN Counter würde durch ein decrement unter 0 fallen
WHEN das finally läuft
THEN aufräumen (fail-open) UND ERROR-Log „unbalanced in-flight counter" — Programmierfehler sichtbar
```

**Mutations-Nachweis (Da-Dok-Review, 2026-09-10):** Die eine beweiswürdige Stelle ist der
Commit-Guard (`if (remaining <= 0)` + Skip-Else, `AIChatView.java:601-618`). Nachweis ohne
SWT-Harness: Guard mutieren (immer committen) → zwei überlappende Turns (Send + sofort
Compact bzw. Doppel-Send, Smoke 3) dekrementieren deterministisch auf **-1** → erwartete
Evidence: ERROR `unbalanced in-flight turn counter: -1` + **doppelte** `turn done
(reset committed in-flight 1->0)`-Zeilen in der Error Log View + UI unlockt während 🟢.
Die `<0`-Zeile ist der eingebaute Mutations-Detektor.

```
GIVEN Run B lebt und Job A's finally-UI-Runnable läuft
WHEN inFlight.decrementAndGet() != 0
THEN kein Unlock, kein monitorRef-Reset — INFO-Skip-Zeile

GIVEN ein Turn endet als letzter (inFlight → 0)
WHEN das finally-UI-Runnable läuft
THEN Unlock + monitorRef-Reset wie bisher

GIVEN Doppel-Send im Schedule-Fenster (Phantom-Job wird vom Agent-CAS abgelehnt)
WHEN das Phantom-Job-finally läuft
THEN inFlight bleibt > 0 (Job 1 lebt) — kein stale Unlock
```

### R-ST2 — Graceful Stop

Stop cancelt den aktuell lebenden Run. **Läuft nichts, ist Stop ein stiller Noop** — kein
Fehler, kein Log-Spam; der Button geht über den Run-Lifecycle aus. Mit R-ST1 ist
`monitorRef` garantiert der lebende Run (oder der Null-Monitor, wenn keiner läuft).

```
GIVEN kein Run aktiv
WHEN Stop gedrückt
THEN kein Fehler, UI unverändert

GIVEN Run aktiv
WHEN Stop gedrückt
THEN der Run bricht ab (Queue-Drain-to-Memory wie bisher, Abort-Pfad unverändert)
```

### R-ST3 — Turn-INFO-Logging

Pro Turn-Lifecycle je eine INFO-Zeile (Plugin-seitig via `Platform.getLog`/`AIChatView.LOG`,
**nicht** java.util.logging, kein IStatus-Dialog; **immer an**, nicht hinter Debug-Flag —
der Incident verlor seine Evidence, weil Logs rotieren):

- Submit: Agent (beim Submit gecaptured — `getActiveAgent()` im finally liefert nach
  Agent-Wechsel den falschen Namen) + in-flight-Wert, z.B.
  `turn submit: agent=<name> in-flight=<n>`.
- Finally: Skip-Zeile mit **eigenem und aktuellem** Wert, z.B.
  `turn finally skipped (newer run in flight): agent=<name> in-flight <vor>-><nach>` —
  das ist der direkte Clobber-Beweis; Commit-Zeile `turn done: agent=<name> reset
  committed (in-flight <vor>-><after>)`.

```
GIVEN Job A's finally, während ein neuerer Run in flight ist
WHEN das finally-UI-Runnable läuft
THEN INFO-Skip-Zeile mit Agent + in-flight-Wert, kein Reset/Unlock
```

## Backlog / out of scope

- **Senden während Compress (Da-Thinka-Fund, 2026-09-10):** `doCompressContext` setzt
  `working` nie → `resolveOutgoingMessage` queued nicht → Send während Compress = Submit →
  **paralleler Run gegen dieselbe Memory**, während `compact()` `memory.clear()` macht.
  Der Counter räumt das Lock-Bookkeeping korrekt auf, die Memory-Race bleibt — Backlog,
  eigene Story, nicht hier.
- **Live-Status im Retry-Backoff-Fenster:** aktuell by design unsichtbar (END-Chunk bei
  onError versteckt die Statuszeile, PROBLEM-Nachrichten ebenso) — User liest die Stille als
  „hängt im PP". Eigene Mini-Story, nicht hier ([open-points.md](open-points.md)).
- **Step 2 (clean as you go):** nach Grün `submitAiJob`/`doCompressContext` auf einen
  gemeinsamen `submitTurnJob(...)`-Helper dedupen (acquire+lock+schedule+finally-Guard).
  Nicht im ersten Schritt vermischen.
- Log-Rotation-Retention (nur 10 Backups) — falls Evidence-Bedarf entsteht, separat.
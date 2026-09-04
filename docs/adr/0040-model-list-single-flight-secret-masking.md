# ADR-0040 — Modell-Listen-Fetch: Single-Flight statt Cancel, und keine Secrets in `toString()`

**Status:** Accepted (2026-09-02, umgesetzt inc-24; nachträglich aus dem Plan-Archiv in die Docs
überführt 2026-09-04)

## Context

Zwei Fehler im Modell-Listen-Pfad ([model-loading.md](../model-loading.md),
[ADR-0034](0034-connection-cache-by-identity.md)), beide erst im Mehr-Agenten-Betrieb sichtbar:

1. **Race:** `SharedHttpClient` hielt ein **globales** `pendingRequest` und verwendete
   `cancelAndSend`. Öffneten mehrere Agenten mit **derselben** `ConnectionIdentity` gleichzeitig
   ihre Modell-Liste, cancelten sie sich gegenseitig — N-1 Agenten sahen eine leere Liste. Die
   Cancel-Mechanik war für einen ganz anderen Fall gedacht (Tipp-Eingabe) und im Listen-Pfad
   schlicht falsch.
2. **Secret-Leak:** `ConnectionIdentity.toString()` und `EffectiveConnection.toString()` gaben
   `apiKey` und extra body im Klartext aus — und landeten damit im Log
   ([secure-credentials.md](../secure-credentials.md)).

Nicht-offensichtlicher Nebenbefund, der den Bug-Pfad erklärte: `CompletableFuture.get()` auf einem
**selbst gecancelten** Future wirft `CancellationException` **unwrapped**, nicht verpackt in
`ExecutionException` — der Catch-Block griff deshalb nicht (jetzt in `AGENTS-DEV.md` als API-Trap).

## Decision

- **Cancel im Listen-Pfad ersatzlos entfernt.** Kein globales `pendingRequest` mehr.
- **Single-Flight pro `ConnectionIdentity`** im `ModelListCache`: eine `inFlight`-Map,
  `putIfAbsent` → Gewinner holt inline, Verlierer hängen sich an dasselbe Future;
  Aufräumen im `finally` mit dem **zwei-Argument-`remove`** (nur den eigenen Eintrag entfernen,
  sonst löscht ein Nachzügler den Fetch eines anderen).
- **`toString()` maskiert Secrets** in `ConnectionIdentity` und `EffectiveConnection`.

## Consequences

- Parallele Agenten mit gleicher Identität lösen **einen** HTTP-Fetch aus und bekommen alle das
  Ergebnis — Verhalten ist unabhängig von der Zahl gleichzeitig offener Config-Seiten.
- Der Cache bleibt „cache on success" (ADR-0034); ein Fehlschlag wird an alle Wartenden
  durchgereicht und nicht gecacht.
- Regeln + BDD: [model-loading.md](../model-loading.md) (Single-Flight pro Identität, inc-24).
  Der zugehörige Nebenbefund zum flakigen `concurrentFailure_…`-Test steht in
  [resolved-points.md](../resolved-points.md).

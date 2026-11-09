# Messergebnisse API-Fehler-Inkremente (Übernahme-Vorschläge für `docs/**`)

Doku-Inhalt entscheidet der Doku-Besitzer. Hier stehen nur die gemessenen Zahlen (Plan §9) und die
Formulierungen, die `docs/open-points.md` bzw. `docs/api-errors.md` brauchen.

## Inc 1 — C-8 und „Spring default error view" (gemessen 2026-11-09)

**C-8 — SSE-Kante verliert ihren Status (✅ behoben).**
`GET /api/projects/<unbekannt>/events` mit `Accept: text/event-stream` antwortete mit **500**, weil die
Ablehnung (404) einen JSON-Body bekam und die Aushandlung gegen `text/event-stream` fehlschlug — der
ursprüngliche Status ging verloren. Nach dem Fix antwortet dieselbe Anfrage mit **404 und leerem Body**:
unsere Fehler-Antworten prüfen den `Accept`-Header und bleiben bodylos, wenn er kein JSON zulässt; ohne
Body gibt es keine Aushandlung, die den Status ersetzen könnte. Körperlos heißt für den Client „kein
Feld, kein Wortlaut" — der SSE-Client wertet dort den Status.
Beleg: `ApiErrorContractTest.anSseEdgeKeepsItsRejectedStatus`.

**⏳ „Spring default error view" (R-WP-55) — Messergebnis.**
Eine nicht gemappte `/api/`-Kante (`GET /api/nothing-is-mapped-here`, `Accept: application/json`) liefert
**404** mit:

```json
{"timestamp":"2026-11-09T14:31:13.212Z","status":404,"error":"Not Found","path":"/api/nothing-is-mapped-here"}
```

Kein `message`, kein `fieldErrors`, kein `problem+json`. Am Feld ist diese Antwort unbrauchbar — unser
Fehler-Advice bleibt nötig und ist jetzt gemessen, nicht behauptet.
Beleg: `ApiErrorContractTest.withoutOurAdviceSpringAnswersItsOwnErrorBody`.

**Kontext-Disziplin (M5).** Ganzer `clean test`-Build: `missCount = 1` (hitCount 1462), 267 Tests grün,
43.3 s (Vorher-Messung 44 s). Kein neuer Kontext, kein Eintrag in der Ausnahmliste des Kontext-Guards.

## Noch offen (gehören zu Inc 4)

- **M1 (UC-WP-91 G1)** und **M2 (UC-WP-91 G2)** werden mit dem Generator gemessen — vorher gibt es weder
  `server-api.ts` noch den Generator-Aufruf.

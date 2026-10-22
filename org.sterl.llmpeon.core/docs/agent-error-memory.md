---
idPrefix: AEM
---

# API-Fehler ins Agent-Memory

> **Status:** ✅ done (2026-09-30, Paul; `53e54c5e` + Delta `f77cbef0` nach Da-Dok-REJECTED, core
> Surefire 1085/0/0). **Anlass:** Turn stirbt still an API-Fehlern
> (400 `exceed_context_size_error`, Thinka 974k vs n_ctx 170240) — die History enthält den Abbruch
> nicht; der Agent (und Paul) wissen beim nächsten Turn nicht, warum Schluss war. Verwandt:
> [agents-retry.md](agents-retry.md) (ApiRetry — erst nach erschöpftem Retry),
> [compact.md](compact.md) R-CC-7 (Fehlerklassen-Tabelle, ein Bestand zwei Verbraucher) und
> R-CC-17 (Compact-only, 🔒 Paul: 400-Pfad wird hier NICHT zusätzlich instrumentiert).
> **AiChatView-Wiring = manuelle Verifikation, Paul-Smoke steht aus.**

## Business Rules

### R-AEM-1 — Terminaler Fehler wird zur Memory-Message ✅ done (`53e54c5e`; AiChatView-Wiring manuell)

Fliegt ein Turn mit einer **echten Exception** raus (der `catch`-Pfad in `AiChatView` — nicht der
stille Cancel-Pfad), wird die Fehlermeldung als **kompakte Message ins Agent-Memory** eingefügt:

- **Wann:** nur **terminal** — ApiRetry erschöpft, oder nicht-retryable (Fehlerklassen-Tabelle
  R-CC-7/ApiRetry; `exceed_context_size_error` ist nicht-retryable → sofort). **Nie** bei
  Cancel/Stop (User-Abbruch ist kein Fehlerzustand).
- **Inhalt:** `e.getMessage()` (Provider-Ursache, kompakt — kein Stacktrace, keine Doppelt-Emission
  neben dem bestehenden UI-Problem-Report).
- **Wirkung:** der Agent **sieht** den Fehler im nächsten Turn und kann reagieren (z. B. selbst
  `compactSession` rufen). Grenze (dokumentiert, nicht gelöst): im harten Kontext-Überlauf kann
  trotzdem kein Turn mehr fahren — das Feature macht den Abbruch recoverbar, er heilt ihn nicht.

#### UC-AEM-1 — terminalErrorLandsInMemory ✅

- GIVEN ApiRetry erschöpft (oder nicht-retryable Fehler) WHEN der Turn fehlschlägt THEN das
  Agent-Memory enthält eine kompakte Message mit `e.getMessage()`.

#### UC-AEM-2 — cancelNeverInserts ✅

- GIVEN der User drückt Stop (Cancellation) WHEN der Turn abbricht THEN **keine** Memory-Message.

### R-AEM-2 — Dedup: genau einmal pro Memory ✅ done (`53e54c5e`)

Dieselbe Fehler-Message wird **nur einmal** ins Memory eingefügt (`containsMessage`-Muster wie
COMPACT_HINT/Spam-Guard `8fb7baf`) — aufeinanderfolgende identische Fehler füttern den Kontext
nicht.

#### UC-AEM-3 — duplicateErrorInsertedOnce ✅

- GIVEN der Fehler ist bereits im Memory WHEN ein identischer terminaler Fehler auftritt THEN
  keine zweite Kopie.

## Verifikation

`AiChatView` = SWT-UI — Automatisierung nur soweit die Memory-Logik im Core testbar ist (Dedup,
Message-Bau); der AiChatView-Wiring wird manuell verifiziert (User-Smoke), wie in UI-Stories
geübtes Muster.

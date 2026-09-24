# Header-State-Leak — Evidenzsammelstelle

> **Status:** 🚧 in design (2026-09-24, Paul) — Bug-Fix-Zyklus-Backlog.
> Verwandt: [open-to-discuss.md](open-to-discuss.md) (Context-Overflow-Triage) ·
> [chat-job-lifecycle.md](chat-job-lifecycle.md) (R-ST1 Clobber-Race, gleiche Familie) ·
> [compact-context-counter.md](compact-context-counter.md) (R-CC-Zähler).

## Ziel

Der Chat-Header (Roster, Token-Zähler, Working-Status) hält nach Fehler-/Cancel-Pfaden einen
**veralteten Zustand** — er rendert neu bei onTool/onChatResponse/onTokenUsage, aber ein
**onProblem rendert den State oben nicht neu**. Diese Datei sammelt alle Evidenzen an einer
Stelle, bis der Bug gebaut ist.

## SOLL-Kern (Paul, 2026-09-24)

Ein `onProblem` (Fehler/Cancel im Turn) muss den Header-State ebenfalls re-rendern — sonst
bleiben 🟢/Zähler/Working-Hint auf dem Stand vor dem Fehler stehen („Da Thinka hat sich
festgefressen — 75k oben in der Anzeige").

## Evidenz

### Fall 1 — Da-Dok-Review-Call (2026-09-24, ~13:02)

- `reviewPlanAgent` starb an 400 `exceed_context_size_error`: Request **172 205** > n_ctx
  **170 240**; Header zeigte **81k** (ohne `~… (estimate)`-Marker).
- 4 ApiRetry-Versuche (10s/20s/30s/40s) auf deterministisch totem Payload, dann „canceled
  while waiting to retry" (Memory #21), danach erst Auto-Compact.
- Error-Log enthält nur den finalen Stack — Retry-Versuche/Ursache nicht sichtbar
  (Logging-Lücke, Paul: „schlecht, weil wir da kein Logging haben").
- Divergenz 81k ↔ 172k: zunächst als Counter-Bug triagiert (open-to-discuss 2026-09-24);
  **Neubewertung s. Fall 2.**

### Fall 2 — Da-Thinka-planWithPlanAgent (2026-09-24, ~15:0x)

- Header: **75k**; Request: **232 440 > 170 240**, **8** ApiRetry-Versuche (Backoff bis 80s),
  dann „canceled while waiting to retry".
- Danach Compact: **„Compressing conversation 48 messages, 169 592 tokens"**.
- **Pauls Bewertung (2026-09-24): reiner Anzeige-Fehler, Positiv-Fall für den Compact** —
  „unser Compact kürzt und die Zahlen passen sehr gut diesmal". Der Counter selbst ist
  plausibel; das Problem ist der **nicht neu gerenderte Header-State** während/hinter dem
  Fehler (75k blieb stehen, obwohl der Agent längst im Fehler-/Retry-Zyklus war).
- Rückwirkung auf Fall 1: die 81k-↔-172k-Divergenz ist vermutlich **derselbe Display-Leak**
  (stale Anzeige), kein eigener Zähler-Bug — Re-Triage vor dem Fix, nicht doppelt bauen.

## Triage-Kandidaten (Bug-Fix-Zyklus)

1. **onProblem rendert Header neu** (Pauls Forderung) — Roster/Token/Working-State nach
   Problem-Events aktualisieren; vermutlich identisch mit der „stille Cancellation"-Lücke
   (AIChatView.java:624-Familie, ball aus ohne State-Update).
2. **ApiRetry non-retryable** für `exceed_context_size_error` — 4–8 tote Backoff-Runden sind
   reine Wartezeit; Klassifikation wie in open-to-discuss 2026-09-24.
3. **Retry-Logging** — Versuche + Ursache sichtbar machen (nicht nur finaler Stack).
4. **Review-Guard vor Call** — Überschlag Prompt-Größe vs. n_ctx VOR teuren Agenten-Calls
   (Review/Plan), Fehler statt halber Retry-Zyklus.

## Offen

- ❓ Wie oft tritt der Leak auf (nur Problem-Pfade? auch nach normalem Turn-Ende)?
- ❓ Ist der Zähler-Wert zum Fehlerzeitpunkt der letzte gültige (und bleibt einfach stehen) oder
  wird er beim Problem-Event aktiv falsch gesetzt? → Messung im Fix-Zyklus (IST-Evidenz vor
  SOLL-Härtung).

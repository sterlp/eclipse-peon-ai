# ADR-0055: Context-Counter misst Input, nicht Kosten

**Status:** Accepted · 2026-09-23 · Hotfix „Ehrlicher Compact" · Feature:
[compact-context-counter.md](../compact-context-counter.md)

## Context

`ThreadSafeMemory.totalTokenUsed` wurde von zwei Schreibstellen mit inkompatiblen Werten
überschrieben: `addResult` schrieb Provider `totalTokenCount()` (Kosten = Prompt + Completion +
Reasoning), `reevaluateTokens()` schrieb den chars×2/7-Estimate (Kontextgröße). Beobachtet:
Hint „289k of 240k used" gegen Compact-Dialog „35k" — desselben Feldes. Jedes Gate, das das Feld
liest (Auto-Compact-Schwelle, Hint-Schwelle, Prozentanzeige), bekam je nach letztem Schreiber
entweder Kosten oder Kontext.

## Decision

Der Kontext-Zähler trägt ausschließlich die **Kontextgröße**: `TokenUsage.inputTokenCount()` wenn
vom Provider geliefert, sonst Estimate (chars×2/7). `totalTokenCount()` (Kosten) fließt nie hinein.
Der kumulative Header ↑↓ bleibt Kosten (ADR-0004, unverändert) — zwei Metriken, zwei Anzeigen,
klar getrennt.

## Why not the alternative

- **totalTokenCount für das Gate:** überschätzt den Kontext massiv (Completion/Reasoning können
  das Mehrfache des Prompts sein — Da Dok-Fall: 289k „Kosten" gegen ~260k echten Kontext ist
  Zufall; bei langen Antworten wäre das Gate viel zu früh scharf bzw. nach Estimate-Rückfall
  völlig blind). Kosten sagen nichts darüber, wie groß der *nächste* Prompt wird — das Gate
  entscheidet aber genau darüber.
- **Estimate überall:** der chars×2/7-Estimate liegt bei code-lastigem Inhalt (~2,4× zu niedrig
  gegen den echten Tokenizer — Hex-IDs, Grep-Results) und unterschlägt System-/Tool-Overhead.
  Wo der Provider echte Input-Zahlen liefert, sind sie der bessere Messwert.

## Pitfalls

- **Anthropic-Undercount:** anthropics `input_tokens` ist bei Prompt-Caching exkl. `cache_read` —
  der Input-Wert kann dann zu niedrig sein. Fallback-Semantik bleibt trotzdem richtig; falls das
  praxisrelevant wird: `cache_read_input_tokens` addieren (separater Punkt, nicht Teil des Hotfix).
- Provider ohne TokenUsage → Estimate weiter nötig (Fallback, R-CC-1).
- Der Header ↑↓ (Kosten) und der Kontext-Zähler stehen adjazenz in der UI — R-CC-6
  (`~N (estimate)`) verhindert, dass sie wie derselbe Wert gelesen werden.

## Consequences

- `addResult`/`reevaluateTokens` schreiben dieselbe Metrik — das Feld ist nicht mehr zweideutig.
- Compact-Gate und Hint-Schwelle sind nach einem fehlgeschlagenen Compact nicht mehr blind
  (R-CC-2) — der Death Spiral (Gate sieht Estimate, kompaktiert nie wieder) ist strukturell
  geschlossen.
- Die Header-Kosten bleiben Team-kumulativ (Jon + Slaven + Compressor + SearchAgent) — bewusst
  unverändert; Verwirrung nur durch fehlende Beschriftung, nicht durch falsche Zahl.
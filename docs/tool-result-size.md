---
idPrefix: RS
---

# Tool-Result-Size im onTool — Ergebnisgröße sichtbar machen

> **Status:** ❌ specified (2026-09-30, Paul). **Anlass:** Thinka-Context-Overflow `974105 tokens`
> vs `n_ctx 170240` — der Status zeigte 23k; welche Message/Tool-Result den Kontext sprengt, ist im
> Log unsichtbar. Verwandt: [compact.md](compact.md) R-CC-17 (Biggest-Message-Dump),
> [tool-output-disclosure.md](tool-output-disclosure.md) R-OD-6 (Grep-Zeilen-Cap).

## Problem

Paul sieht in Chat/Log nur die Tool-Zeile (z. B. `Grep 'replace' type '*' found 850 matched lines`)
und das Diff — nicht, **wie groß** das Ergebnis war, das in den Kontext floss, und (historisch)
nicht welches Tool es erzeugte. Ohne Größe ist die Overflow-Diagnose Raten.

## Business Rules

### R-RS-1 — Tool-Zeile nennt die Ergebnisgröße ❌

Jede Tool-Completion-Zeile (onTool-Report in Chat/Log) endet mit **`(N chars)`** — N = Größe des
Ergebnisses, das in den LLM-Kontext geht (der Return-String des Tools, nicht UI-Rendering).

- GIVEN Tool-Result mit 12345 chars WHEN das Tool abschließt THEN die Zeile endet mit
  `(12345 chars)`. *(Test: `onToolLineDisclosesResultChars`)*
- GIVEN leerer Result WHEN done THEN `(0 chars)` (ehrlich, kein Weglassen). *(Test:
  `onToolEmptyResultDisclosesZeroChars`)*
- GIVEN Sub-Agent-Tool mit bestehender Dauer-Angabe (`(3s)`, [sub-agent-timing.md](sub-agent-timing.md))
  WHEN done THEN beide Angaben nebeneinander: `(3s) (N chars)`. *(Test:
  `subAgentTimingAndCharsCoexist`)*

### R-RS-2 — chars, kein Token-Estimate ❌

Die Zeile nennt **Zeichen** (deterministisch, gemessen), kein `chars×2/7`-Estimate — Estimates
rauschen und gehören mit Tilde markiert (R-CC-6-Muster); hier gar kein Estimate.

- GIVEN dieselbe Tool-Zeile WHEN gemessen THEN der `(N chars)`-Wert ist exakt die Stringlänge,
  kein gerundeter/geschätzter Wert. *(Test: `onToolCharsAreExactLength`)*

## Umsetzung

Emissionspunkt ist die gemeinsame Tool-Report-Stelle (dort, wo auch die Dauer-Angabe hängt —
`CallStats`-Muster, ADR-0053), nicht je Tool dupliziert.

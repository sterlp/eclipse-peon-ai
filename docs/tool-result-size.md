---
idPrefix: RS
---

# Tool-Result-Size im onTool — Ergebnisgröße sichtbar machen

> **Status:** ✅ done (2026-09-30, Paul specified; `0be9a4c5` + Delta `f77cbef0` nach Da-Dok-REJECTED,
> core Surefire 1085/0/0). **Anlass:** Thinka-Context-Overflow `974105 tokens`
> vs `n_ctx 170240` — der Status zeigte 23k; welche Message/Tool-Result den Kontext sprengt, ist im
> Log unsichtbar. Verwandt: [compact.md](compact.md) R-CC-17 (Biggest-Message-Dump),
> [tool-output-disclosure.md](tool-output-disclosure.md) R-OD-6 (Grep-Zeilen-Cap).

## Problem

Paul sieht in Chat/Log nur die Tool-Zeile (z. B. `Grep 'replace' type '*' found 850 matched lines`)
und das Diff — nicht, **wie groß** das Ergebnis war, das in den Kontext floss, und (historisch)
nicht welches Tool es erzeugte. Ohne Größe ist die Overflow-Diagnose Raten.

## Business Rules

### R-RS-1 — Tool-Zeile nennt die Ergebnisgröße ✅ done (`0be9a4c5`/`f77cbef0`)

Jede Tool-Completion-Zeile (onTool-Report in Chat/Log) endet mit **`(N chars)`** — N = Größe des
Ergebnisses, das in den LLM-Kontext geht (der Return-String des Tools, nicht UI-Rendering).

- GIVEN Tool-Result mit 12345 chars WHEN das Tool abschließt THEN die Zeile endet mit
  `(12345 chars)`. *(Test: `onToolLineDisclosesResultChars`)*
- GIVEN leerer Result WHEN done THEN `(0 chars)` (ehrlich, kein Weglassen). *(Test:
  `onToolEmptyResultDisclosesZeroChars`)*
- GIVEN Sub-Agent-Tool mit bestehender Dauer-Angabe (`(3s)`, [sub-agent-timing.md](sub-agent-timing.md))
  WHEN done THEN beide Angaben nebeneinander: `(3s) (N chars)`. *(Test:
  `subAgentTimingAndCharsCoexist`)*

### R-RS-2 — chars, kein Token-Estimate ✅ done (`0be9a4c5`)

Die Zeile nennt **Zeichen** (deterministisch, gemessen), kein `chars×2/7`-Estimate — Estimates
rauschen und gehören mit Tilde markiert (R-CC-6-Muster); hier gar kein Estimate.

- GIVEN dieselbe Tool-Zeile WHEN gemessen THEN der `(N chars)`-Wert ist exakt die Stringlänge,
  kein gerundeter/geschätzter Wert. *(Test: `onToolCharsAreExactLength`)*



## Umsetzung ✅ (IST nach Bau)

**Keine zentrale Emissionsstelle im IST** — Done-Zeilen sind tool-lokal, das Result entsteht danach.
Gelöst wie der SAT3/CallStats-Präzedenz (ADR-0053): **eine Format-Implementierung**
(`StringUtil.charsSuffix(String)` → `"(N chars)"`, null → `(0 chars)`), angestoßen von den
Call-Sites (~30 fertige Done-Zeilen String-returnierender Tools, Chars = exakte Länge des
Return-Strings; der Return geht unverändert an den LLM). **void-Tools ohne Suffix** (Result =
wörtliches `"Success"` — Rauschen).

**GAP-Liste (bewusst kein Suffix):** `diskEditFile`/`diskReplaceLines` (keine onTool-Zeile im IST),
`eclipseReadOpenFile` (async UI-Thread-Read), `eclipseRunJavaTests`-Timeout-Pfad.
**Geschlossen (Inc 1a, PO-Entscheidung):** `reloadConfig` (`ReloadConfigTool.java:60`, Reorder),
`webFetch` (Option A — finale Zeile `Fetched <url> (N chars)` nach der Result-Konstruktion, nun auch
für Cache-Hits; Overflow-Verdächtigen Nr. 1). `eclipseFindImplementations` = toter Code
(Kommentar-Block) — kein Suffix, Entfernung separat.
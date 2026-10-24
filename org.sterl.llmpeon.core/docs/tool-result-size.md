---
idPrefix: RS
---

# Tool-Result-Size im onTool — Ergebnisgröße sichtbar machen

> **Status:** ✅ done (2026-09-30, Paul specified; Delta nach Da-Dok-REJECTED). **Anlass:** Context-Overflow — welche Message/Tool-Result den Kontext sprengt, ist im
> Log unsichtbar. Verwandt: [compact.md](compact.md) R-CC-17 (Biggest-Message-Dump),
> [tool-output-disclosure.md](tool-output-disclosure.md) R-OD-6 (Grep-Zeilen-Cap).

## Problem

Paul sieht in Chat/Log nur die Tool-Zeile (z. B. `Grep 'replace' type '*' found 850 matched lines`)
und das Diff — nicht, **wie groß** das Ergebnis war, das in den Kontext floss, und (historisch)
nicht welches Tool es erzeugte. Ohne Größe ist die Overflow-Diagnose Raten.

## Business Rules

### R-RS-1 — Tool-Zeile nennt die Ergebnisgröße ✅ done

Jede Tool-Completion-Zeile (onTool-Report in Chat/Log) endet mit **`(N chars)`** — N = Größe des
Ergebnisses, das in den LLM-Kontext geht (der Return-String des Tools, nicht UI-Rendering).

- GIVEN Tool-Result mit 12345 chars WHEN das Tool abschließt THEN die Zeile endet mit
  `(12345 chars)`. *(Test: `onToolLineDisclosesResultChars`)*
- GIVEN leerer Result WHEN done THEN `(0 chars)` (ehrlich, kein Weglassen). *(Test:
  `onToolEmptyResultDisclosesZeroChars`)*
- GIVEN Sub-Agent-Tool mit bestehender Dauer-Angabe (`(3s)`, [sub-agent-timing.md](sub-agent-timing.md))
  WHEN done THEN beide Angaben nebeneinander: `(3s) (N chars)`. *(Tests: die verbliebenen
  onTool-Zeilen-Tests der Sub-Agent-Tools; für den Compact deckt R-CT-8 die Coexistenz über die
  uniforme End-Zeile ab — der Alt-Test `subAgentTimingAndCharsCoexist` pinnte die entfallene
  „Da Scribe done"-Zeile und ist mit R-CT-8 entfallen.)*

### R-RS-2 — chars, kein Token-Estimate ✅ done

Die Zeile nennt **Zeichen** (deterministisch, gemessen), kein `chars/3`-Estimate — Estimates
rauschen und gehören mit Tilde markiert (R-CC-6-Muster); hier gar kein Estimate.

- GIVEN dieselbe Tool-Zeile WHEN gemessen THEN der `(N chars)`-Wert ist exakt die Stringlänge,
  kein gerundeter/geschätzter Wert. *(Test: `onToolCharsAreExactLength`)*

### R-RS-3 — Auch dynamische Tools (MCP) disclosen ihre Ergebnisgröße ✅ done (2026-10-18)

MCP-Tools laufen **nicht** durch die tool-lokalen Done-Zeilen (kein `SmartToolExecutor`, kein
Tool-eigener Code) — sie hatten nur die Start-Zeile `Running MCP: <name> - <args>`. Damit fehlte
für genau die Werkzeuge, die fremde, unbegrenzte Inhalte in den Kontext ziehen (Codegraph, Web-
Search …), jede Größenangabe. Der Choke-Point ist `ToolService.execute` (MCP-Zweig): nach der
Ausführung kommt eine **Completion-Zeile** mit dem Ergebnis-Suffix.

- GIVEN ein MCP-Tool-Call WHEN das Tool zurückkehrt THEN erscheint zusätzlich zur Start-Zeile eine
  Completion-Zeile `MCP <name> done. (N chars)` mit N = exakte Länge des Ergebnis-Strings. *(Test:
  `givenMcpGroupAdopted_whenExecutingTool_thenCompletionLineDisclosesResultChars`)*
- GIVEN ein MCP-Ergebnis ist leer/null WHEN done THEN `(0 chars)` (kein Weglassen). *(Test:
  `givenMcpResultNullOrEmpty_whenExecutingTool_thenCompletionLineDisclosesZeroChars` — serverseitig
  null → `""` als Message-Text, weil langchain4j keinen null-Text annimmt; die Disclosure zählt
  vorher und nennt ehrlich 0.)*
- GIVEN ein nicht-MCP-Tool WHEN done THEN unverändert dessen eigene Done-Zeile (kein doppelter
  Suffix). *(Tests: R-RS-1 `onToolLineDisclosesResultChars`)*

**Fehlerpfad (verifiziert 2026-10-18):** MCP-Fehler gehen nicht über den null-Pfad — langchain4j
wirft bei `isError=true` `McpApplicationErrorException`/`ToolExecutionException`, `McpService.executeTool`
(`McpService.java:163-168`) fängt das und gibt `"MCP tool error (<name>): <message>"` zurück. Die
Fehlerinfo bleibt also im Kontext; die Completion-Zeile nennt dann die Länge dieses Textes.



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

**MCP (R-RS-3):** einzige Ausnahme mit echter zentraler Stelle —
`ToolService.execute` MCP-Zweig (`ToolService.java:298-304`) meldet
`Running MCP: <name> - <args>` … `MCP <name> done. (N chars)`. Grund: der Codegraph-/MCP-Aufruf
geht an keinem Tool-Code vorbei, der den Return kennt.
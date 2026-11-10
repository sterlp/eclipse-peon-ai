# Tool-Loop — nicht-produktive Modell-Antworten recovern

**Technische Heimat:** core `org.sterl.llmpeon.tool` (`ToolService.executeLoop`,
`RescueToolComponent`), Tests `ToolServiceTest`.

## Purpose

`executeLoop` erwartet je Iteration entweder echte `ToolExecutionRequest`s oder eine Antwort mit
Text. Antworten, die beides nicht sind (nur Thinking, Roh-Tool-Call als Text), brechen den Turn
sonst — der Loop recovernt sie mit einem gebundenen `stuck`-Counter
(`MAX_STUCK_ITERATIONS` = 10, je Pfad gezählt, Produktivität setzt ihn zurück).

## R-TL-1 — Rescue: Tool-Call als Text ✅ (Paul, 2026-11-07 — issue #163)

Das Modell antwortet mit einem Roh-Tool-Call als **Text** (z. B. Claude-Syntax
`function=Read <parameter=file_path> …` mit „open bracket"-Präfix und „close bracket"-Suffix —
die beiden Marker leben als Konstanten am Typ, keine Zeichen im Doc). Das ist korrekt verarbeiteter
Text, kein langchain4j-Bug: alle Provider mappen ausschließlich strukturierte Tool-Call-Felder
(`OpenAiUtils`, `AnthropicMapper`, `InternalOllamaHelper`) — es gibt nichts zu retten durch
Re-Parsing in echte Requests. Statt den Text einfach in die Memory zu schreiben und den Loop still
laufen zu lassen, meldet die Heuristik den Protocol-Bruch:

* **Detection:** `RescueToolComponent.isToolCall` — getrimmter, lower-cased Text beginnt mit
  dem Claude-Tool-Call-Präfix und endet auf dessen Suffix (beide Konstanten am Typ).
* **Handling:** +1 `stuck`, `onProblem("AI returned a tool call as response!")`, die AI-Message
  UND die `AI_INFO`-UserMessage („Invalid tool call. Use the supported tool-call protocol …")
  wandern in die Memory, der Loop fragt erneut — das Modell sieht seinen Fehlversuch und die
  Korrektur.
* **Bound:** übersteigt `stuck` die Grenze, bricht der Loop ab (dasselbe Limit wie R-TL-2).

Kein Re-Parsing des Textes zu `ToolExecutionRequest` — Fix-Idee 2026-10-23 bewusst verworfen
([resolved-points.md](../../docs/resolved-points.md), issue #163).

### BDD (Test: `ToolServiceTest.testRescueToolCalls`)

```
GIVEN das Modell antwortet mit Roh-Tool-Call-Text (Claude-Präfix/Suffix-Marker, siehe oben) und danach mit „Ok“
WHEN executeLoop läuft
THEN die Memory enthält die AI-Message (Roh-Tool-Call) gefolgt von der AI_INFO-UserMessage
AND das Modell wird erneut gefragt (2 Calls) und der „Ok“-Text beendet den Loop regulär
```

## R-TL-2 — Think-only Stuck ✅ (Bestand)

Nur Thinking kam zurück (langchain4j issue #4786 — die Original-Message kann an Provider mit
Tool-Nutzung nicht angehängt werden): Thinking landet als AiMessage in der Memory, die
`STUCK_MESSAGE` („Stop thinking and take action now …“) als UserMessage, +1 `stuck`. Ab
`stuck > MAX_STUCK_ITERATIONS - 2` zwingt der Forced-Hint den Compact-Hint (R-CC-4,
[compact.md](compact.md) — Dedup, Guard), über der Grenze bricht der Loop ab.

## Abgrenzung

* Fehler statt nicht-produktiver Antwort (API/Retry): [agents-retry.md](agents-retry.md),
  API-Fehler ins Memory: [agent-error-memory.md](agent-error-memory.md).
* Compact-Entscheidung im Loop: [compact-architektur.md](compact-architektur.md) — `executeLoop`
  selbst entscheidet nichts über Compact, es ruft nur `HandleToolLoopCompactComponent`.

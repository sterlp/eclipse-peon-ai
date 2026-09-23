---
idPrefix: CC
---

# Compact-Context-Counter — ehrlicher Token-Zähler & ehrlicher Compact

> **Status:** ✅ done (2026-09-23, Hotfix für Paul — „das Thema brennt weil es mich blockiert").
> Surefire llmpeon-core 930/0/0/0 (+10 Tests), Plugin-Suite 279/0/0, Mutations-Nachweis
> (`isCompactedThisTurn` → immer true → `keepsRealCounterOnFailedCompact` rot 260000 vs 59).
> Branch `analysis/tool-evolution`, ein Commit (Docs + Code + Plan).
> Ein Commit, Test-First („erst isoliert ein Test, dann der Fix"), Branch `analysis/tool-evolution`.
> Abgrenzung: [compact-input-budget.md](compact-input-budget.md) (Compressor-**Input**-Budget, R1–R5,
> separat — hier geht es um den **Kontext-Zähler** und die Ehrlichkeit des Compact-Ergebnisses).
> Verwandt: [token-usage.md](token-usage.md) (Header ↑↓ = Kosten, unverändert, ADR-0004),
> [context-message-concept.md](context-message-concept.md), [chat-markdown-links.md] irrelevant.

## Problem (IST, 2026-09-23)

Drei beobachtete Fälle, eine gemeinsame Wurzel:

1. **Da Dok 289k/122k:** Hint meldete „289493/260k of 240000/250k used" — der Compact-Dialog
   zeigte 35k, der Roster 34k. Der Zähler trägt zwei inkompatible Werte im selben Feld.
2. **Da Mek 260k/35k:** Auto-Compact-Gate sah nach einem fehlgeschlagenen Compact plötzlich
   35k statt 260k → kompaktiert nie wieder → Death Spiral.
3. **Hint-Spiral:** der Compact-Hint feuerte nach jeder Tool-Runde neu — jede Runde ein
   verbrannter Compressor-Call.

Wurzeln (alle code-verifiziert):

- `ThreadSafeMemory.totalTokenUsed` wird von **zwei Metriken** überschrieben: `addResult` schreibt
  Provider `totalTokenCount()` (**Kosten** = Prompt+Completion+Reasoning), `reevaluateTokens()`/`add()`
  schreiben den chars×2/7-**Estimate** (Kontextgröße). Gleicher Wert, zwei Bedeutungen.
- `ToolService` ruft nach **jedem** `compactSession`-Tool-Call — auch bei Fehlschlag —
  `reevaluateTokens()` + Static-Rebuild → Zähler fällt auf den Estimate → Gate blind.
- `AbstractAgent.compact()` liefert für zwei grundverschiedene Fälle dasselbe `false`:
  „zu klein zum Compacten" (legitim) und „Compressor lieferte leeren Text" (**Fehler**) —
  beides landet als „Not needed only N message in context" beim LLM bzw. still im UI.
  AGENTS.md: *„A tool must never lie about a limit."*
- Der Compact-Hint wird nicht dedupliziert — er steht einmal pro Tool-Runde neu in der Memory.
- Der Workspace-Memory-Snapshot-Key ist der entries-Hash (ADR-0032): jede `memoryAdd` → neuer
  Hash → neuer Key → **frische Vollkopie des Snapshots je Mutation, bis zum Compact**. Das ist
  by design („a change reinjects a fresh snapshot") und treibt die Aufsummierung zwischen zwei
  Compacts — separat zu klären (siehe „Offen"), **nicht Teil dieses Hotfix**.
  (Verifiziert 2026-09-23: UserMessage-Text wird in `ChatMessageUtil.toString` **nie** trunciert
  — nur Ai-Tool-Args und ToolResults; `containsUserMessage` prüft pro Item volltextlich über die
  gesamte Historie. Die früher hier vermutete 6000/90000-Dedup-Blindstelle existiert nicht und
  wurde ersatzlos gestrichen.)

## Regeln

### R-CC-1 — Context-Counter misst Input, nie Kosten

Der Kontext-Zähler (`totalTokenUsed`) trägt ausschließlich die **Kontextgröße** (Prompt-Input).
Wenn der Provider `TokenUsage.inputTokenCount()` liefert, wird sie verwendet (Fallback: Estimate
chars×2/7). `totalTokenCount()` (Kosten inkl. Completion/Reasoning) fließt **nie** in den
Kontext-Zähler — der Header ↑↓ bleibt Kosten (ADR-0004, unverändert).

- GIVEN eine Agent-Response mit `TokenUsage(inputTokenCount=1000, outputTokenCount=5000,
  totalTokenCount=6000)` WHEN `addResult` verarbeitet sie THEN `totalTokenUsed` wächst um **1000**,
  nicht um 6000. *(Test: `ThreadSafeMemoryInputTokenCountTest#countsInputNotTotal`)*
- GIVEN eine Response **ohne** TokenUsage WHEN `addResult` verarbeitet sie THEN der Estimate
  (chars×2/7) wird verwendet. *(Test: `ThreadSafeMemoryInputTokenCountTest#fallsBackToEstimate`)*

### R-CC-2 — Reevaluate nur nach erfolgreichem Compact

`reevaluateTokens()` + Static-Rebuild nach `compactSession` laufen **nur**, wenn der Compact
tatsächlich kompaktiert hat (COMPACTED). Bei Skip/Failure bleibt der echte Zählerwert stehen.

- GIVEN der Zähler steht auf 260000 (echter Wert) WHEN ein `compactSession` mit leerer
  Compressor-Response fehlschlägt THEN `totalTokenUsed` bleibt 260000 — kein Fall auf den Estimate,
  das Auto-Compact-Gate bleibt scharf. *(Test: `ToolServiceCompactResultTest#keepsRealCounterOnFailedCompact`)*

### R-CC-3 — Ehrliches Compact-Ergebnis

`compact()` liefert ein `CompactResult` (**COMPACTED / SKIPPED_SMALL / FAILED_EMPTY**), kein
Boolean. `SKIPPED_SMALL` = Kontext zu klein zum Compacten (legitim, „not needed"-Zeile bleibt
ehrlich). `FAILED_EMPTY` = Compressor lieferte nichts → das ist ein **Fehler**: `monitor.onProblem`
(„Compact failed: compressor returned no summary for `<agent>`") beim Tool-Call, beim UI-Compact-Button
und beim stillen Auto-Compact vor dem Turn — kein stiller Abbruch.

- GIVEN Memory mit 2 Messages WHEN `compact()` THEN `SKIPPED_SMALL`, keine onProblem-Meldung.
  *(Test: `AbstractAgentCompactResultTest#skipsSmallContextHonestly`)*
- GIVEN Memory ≥ 3 Messages, Compressor-Mock liefert leeren Text WHEN `compact()` THEN
  `FAILED_EMPTY` **und** `monitor.onProblem` feuert mit Agentennamen. *(Test:
  `AbstractAgentCompactResultTest#emptyCompressorIsFailedNotEmptyNeeded`)*
- GIVEN Compact scheitert WHEN der nächste Turn startet THEN das Auto-Compact-Gate versucht es
  erneut (Returnwert wird nicht mehr ignoriert). *(Test: `AbstractAgentCompactResultTest#autoCompactRetriesAfterFailure`)*

### R-CC-4 — Compact-Hint nur einmal

`addCompactHintIfNeeded` fügt den `COMPACT_HINT` nur hinzu, wenn er nicht bereits in der Memory
steht (`containsMessage`). Nach erfolgreichem Compact ist die Memory geleert → der Hint wird
automatisch wieder scharf. Auch der forced Hint (Stuck-Pfad) unterliegt dem Dedup.

- GIVEN der Hint steht bereits in der Memory WHEN `addCompactHintIfNeeded` erneut feuert THEN kein
  zweiter Hint, die Memory enthält ihn genau einmal. *(Test:
  `ToolServiceCompactHintTest#hintIsAddedOnce`)*
- GIVEN Compact war erfolgreich (Memory gecleart, „Session compacted"-Marker neu) WHEN die
  Schwelle wieder überschritten wird THEN der Hint erscheint erneut. *(Test:
  `ToolServiceCompactHintTest#hintReappearsAfterSuccessfulCompact`)*

### R-CC-6 — Estimate als Estimate gekennzeichnet

Wird der Zähler als Estimate (chars×2/7) bestimmt, zeigt die Anzeige `~N (estimate)` — im
PoDelegateTool-Kontext und im Roster. Provider-Werte stehen ohne Tilde.

- GIVEN der letzte Wert stammt vom Estimate WHEN der Kontext angezeigt wird THEN
  `~34k (estimate)`. *(Test: `ContextCounterDisplayTest#estimateIsDisclosed`)*

## Umsetzung

- Ein Commit (Docs + Code), Core-Tests isoliert (JUnit 5 + AssertJ, GIVEN/WHEN/THEN); UI-Button-
  Feedback ist Plugin-Teil. Test-First: rote Tests zuerst zeigen, dann der Fix (Paul).
- Reihenfolge: R-CC-2 + R-CC-3 zuerst (entblockt), dann R-CC-1, R-CC-4, R-CC-6.
- Alle `compact()`-Caller (PoDelegateTool, CompactSessionTool, UI-Button, Tests) per Grep
  verifizieren — Interface-Änderung `AiAgent.compact()`.

## Offen (nicht Teil dieses Hotfix)

- **WARUM liefert der Compressor leeren Text?** (Think-only-Response? Cancel?) — braucht Pauls
  Error-Log (`log.warn „Empty compact message received for <agent>"`) + Compact-Model-Config.
- **Workspace-Memory-Snapshot: Vollkopie je `memoryAdd`** (entries-Hash-Key, ADR-0032) — wächst
  zwischen zwei Compacts um eine Snapshot-Kopie pro Mutation (35 Entries × N Mutationen).
  By design, aber die Kosten skalieren schlecht. ❓ Separate Entscheidung: inkrementeller Snapshot,
  Dedup je Eintrag oder Compact-frequenter? — [open-points.md](open-points.md).
- **AGENTS-`<agent>.md`-Kopien:** im IST-Code nur nach Memory-Reset/Clear/Compact erzeugbar
  (je genau eine Kopie, keine Aufsummierung). Hat Paul mehrere Kopien **in einem** Slave-Memory
  gesehen, braucht es die Evidenz (Chat-View, welcher Agent) — der Code-Pfad liefert dafür keine
  Erzeugungsmöglichkeit.
- Compressor-Input-Budget ([compact-input-budget.md](compact-input-budget.md), R1–R5) bleibt
  eigenständig.
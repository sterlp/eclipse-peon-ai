# 0056: Compact als eigene core-Komponente + Render-Modi in ChatMessageUtil

**Status:** Partially superseded (Decision 2 „Render-Modi als Options-Record") · **Datum:** 2026-09-24 · **Zyklus:** Compact-Redesign (docs/compact.md)

> **Superseded-Teil (2026-10-18):** Decision 2 („Render-Modi in `ChatMessageUtil` als Options-Record")
> ist gefallen — [Tagged Results](../../org.sterl.llmpeon.core/docs/tagged-results.md) R-TR-8 hat **jedes Cap aus dem Renderer**
> entfernt: `ChatMessageUtil.toString(msg, RenderOptions)` und `RenderOptions` sind gelöscht, es gibt
> genau einen Renderpfad (`toString(msg)` = `extractMessages` + Join), und das Kürzen passiert
> ausschließlich im Compact (`ContextTrimComponent`). Decisions 1, 3, 4 und alle Consequences gelten
> unverändert.

## Context

Der Compact lebt verteilt: `AbstractAgent.compact()` (Orchestrierung/Skip-Guards),
`AiCompressorAgent` (Input-Bau + LLM-Call), `CompactSessionTool` (User-Trigger),
`ToolService.addCompactHintIfNeeded` (Hint), `ThreadSafeMemory` (clear/copy). Der
AiCompressorAgent ist kein Konversations-Agent — er ist ein Funktionsaufruf mit dem LLM als
Engine. Das neue SOLL (R-CIB-1…6) braucht gestufte, pure Kürzungslogik, und der
Da-Dok-Review-Befund F8 zeigte, dass das Front-Cap (`$`-Anker, Ende bleibt) mit dem
bestehenden `ChatMessageUtil.toString` nicht abbildbar ist — das ADR-0030-Freeze
(„ChatMessageUtil ist frozen") blockiert den natürlichen Ort.

Außerdem: `join/4` (alter Budget-Entwurf) schätzt niedriger als die bestehende
`chars×2/7`-Konvention — zwei Schätzkonventionen im selben Modul wären ein Widerspruch zu
„one behaviour, one implementation".

## Decision

1. **Eigene core-Komponente `compact`** (~3 Klassen, kein Interface-Zoo):
   `CompactStager` (pure: Dedup + Stufen 1/2/Endstufe + Disclosure —
   `(Memory-Snapshot, Budget) → (Input-String, Disclosure, Stats)`), `CompactEngine`
   (Stager + LLM-Call + Logging), `CompactResult` (Enum → Record mit Stats). Abhängigkeit wird
   `AbstractAgent → CompactEngine` (inward, Funktionsaufruf) statt `AbstractAgent →
   AiCompressorAgent` (Pseudo-Agent); Orchestrierung/Skip-Guards (`size() < 3`, Trigger)
   bleiben im Agent. Die öffentliche API `AiAgent.compact(monitor)` bleibt — Plugin-Blast-Radius klein.
2. **Render-Modi in `ChatMessageUtil` als Options-Record** statt wachsender Parameterliste
   `(msg, includeThink, toolMessageSize)`; Front-Cap mit `$`-Anker ist ein Modus, kein neuer
   Renderer. Damit ist ADR-0030 („Core frozen") **verworfen** — ADRs halten kein Frozen; der
   SystemMessage-Drop (0030-Landmine) wird im selben Zyklus **gefixt und getestet** statt
   konserviert. `ADR-0030` bleibt bis zum Landmine-Fix stehen (Status: superseded), danach
   löschen.
3. **Schätzung an einer Stelle:** `ChatMessageUtil.estimateTokens` (chars×2/7, bewusst
   über-schätzend) ist der einzige Estimator — auch für den Compact-Input; `join/4` verworfen.
4. **Hint/Marker-Konstanten:** `COMPACT_HINT`, Queued-Marker u. ä. werden über Konstanten
   referenziert (nie verstreute Literale) — der Stufe-1-Ausschluss („letzte User-Message mit
   echtem User-Text", erster TextContent) hängt an den Konstanten, nicht an Text-Matching.

## Consequences

- Die Stufen-Logik ist eine pure Funktion `(Memory-Snapshot, Budget) → (Input, Disclosure,
  Stats)` — testbar ohne Agent-Mock; Mutation-Checks (Endstufen-Terminierung, Dedup-Regression)
  laufen gegen die pure Funktion.
- `CompactResult` wird vom Enum zum Record mit Stats (Zahlen für Log + Tool-Result) — alle
  `compact()`-Caller (PoDelegateTool, CompactSessionTool, UI-Button, Tests) per Grep verifizieren.
- Bonus-Fix: `AiCompressorAgent.toText`-Doppelpräfix („AI:\nAI:…") löst sich mit dem neuen Pfad.
- `staticText()`-Helper in `PeonAiService` kann nach dem Landmine-Fix auf den gefixten Pfad
  migrieren (Plan-Detail, eigener Aufräumschritt oder nächster Kontakt).
- Risiko: ChatMessageUtil ist breit genutzt — Render-Modus muss default-kompatibel bleiben
  (alte Aufrufe unverändert), Tests decken die Bestandsformate ab.
**Update 2026-10-16:** Estimate-Faktor `chars×2/7` → `chars/3`, Umkehr `capChars = restTokens×3/n` synchron (Terminationsgarantie R-CIB-4.4 bleibt, `fbe2866f`) — „eine Schätzkonvention, eine Stelle" bleibt unverändert.
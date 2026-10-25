# ADR-0076 — Compact-Reseed: eine UserMessage, Summary nicht als AiMessage

**Status:** Accepted (Paul, 2026-10-25) · **Betrifft:** `AbstractAgent.doCompact`,
`CompactConstants.REINSERT_MARKER`, `ContextTrimComponent.hasRealUserText` ·
**Fachregel:** R-CC-9 ([compact.md](../../org.sterl.llmpeon.core/docs/compact.md))

## Context

Der Compact-Reseed bestand aus zwei Messages: einer `UserMessage` mit den Turn-Context-Blöcken, deren
letzter `TextContent` `Session compacted:` + Summary war, und (seit Pauls Änderung vom 2026-10-25) einer
`AiMessage` mit der Summary. Die AiMessage-Form las sich semantisch sauberer — die Summary steht beim
Absender, der sie produziert hat.

Im ersten Smoke (compact-e2e) fiel sie auf: eine History, die auf einer **AiMessage** endet, macht den
nächsten Request der Tool-Schleife zu einem **Prefill** („schreibe deine eigene Antwort weiter"), weil
`ToolService.executeLoop` je Iteration nur `static + memory` sendet und **keine** neue User-Message
anhängt. Bei Providern mit Tool-Nutzung ist das kein gültiger Turn mehr; gesucht ist aber „antworte auf
den offenen User-Turn".

## Decision

Der Reseed ist **eine** Message: eine `UserMessage` mit den Turn-Context-Blöcken, deren **letzter**
`TextContent` der Marker `Session compacted:` + Leerzeile (`lineSeparator` ×2) + Summary ist. Die
AiMessage-Form ist verworfen. Das Tool-Result trägt weiterhin **keinen** Resume-Text.

## Consequences

- **Ein Marker-Träger** — die 1-Treffer-Invariante (`contains("Session compacted")` über alle Messages
  = 1) ist trivial wahr, ohne Cross-Message-Inferenz; `hasRealUserText` bleibt die **eine**
  Klassifikationsstelle (Marker als letzter TextContent = State, nie User-Text).
- **Eine Form für die Stufenkürzung** — `renderStateOnly` greift wie bei jeder früheren UserMessage.
  Sobald gekürzt werden muss (Stufe 1+), fällt der Marker-Block mit der alten Summary aus dem
  Compact-Input (unter dem Budget bleibt er drin). Bewusst: ein Compact dampft die Konversation
  zusammen, nicht seine eigene Summary — State für den Folge-Compact ist das, was **nach** dem Reseed
  dazukam.
- **`size()` 1** liegt unter `MIN_COMPACT_MESSAGES` (3): ein Direkt-Re-Compact ist automatisch ein
  Skip, ohne eigenen Guard (R16-WEIL in [po-agent-jon.md](../../org.sterl.llmpeon.core/docs/po-agent-jon.md)).
- **Persistierte Alt-Historien** (vor 2026-10-25) tragen den Marker schon in einer UserMessage — sie
  sind mit der neuen Form identisch geformt; der jsonl-Round-Trip ändert nichts.
- Ein Marker-Duplikat als zusätzliche Message (z. B. vor dem Tool-Result) bleibt verboten — es würde
  die 1-Treffer-Invariante und die Zustandsklassifikation der Kürzung brechen.
- **`ThreadSafeMemory.add` stützt die Entscheidung:** zwei aufeinanderfolgende UserMessages werden
  gejoint — eine „UserMessage + AiMessage"-Form ist die einzige Alternative, die den Join nicht
  auslöst; sie kostet aber den gültigen Turn-Abschluss (s. o.).

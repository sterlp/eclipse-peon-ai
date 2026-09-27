# ADR-0058: ToolLoopRequest.toBuilder — Behalten, aber Nested-Requests müssen agent/memory explizit setzen

**Status:** Accepted · **Datum:** 2026-09-27 · **Anlass:** R-CC-15 (docs/compact.md) —
Search-Agent-Loop erbt den Parent-Agenten, Sniffas compactSession clearte Jons Memory.

## Context

`ToolLoopRequest` ist das Request-Objekt des Agent-Loops (chatModel, bridge, retry, monitor,
memory, agent, staticMessages, toolFilter, writeValidator, …). `toBuilder()` wird für Nested-Loops
(Sub-Agenten) genutzt — implizite Vererbung aller Felder ist der Vorteil: 7 Felder ohne
Boilerplate übernehmen.

Der Bug: `SearchAgentTool.java:47-51` überschrieb `staticMessages`, `toolFilter`, `memory`,
`agentConfig` — **nicht** `agent`. Der Nested-Loop trug damit den Parent-Agenten; der Sub-Loop-Compact
(`request.getAgent()` → Parent) hat das **Parent-Memory** gecleart/re-geseed.

## Decision

1. **`toBuilder()` bleibt.** Genau 1 Prod-Call-Site (`SearchAgentTool:47`); alle Agent-Pfade
   (Sklaven via `PoDelegateTool:239` → `AbstractAgent:286-297`, Custom Agents) bauen fresh und
   sind korrekt isoliert. Eine explizite Builder-Factory müsste dieselben 7 Felder listen — das
   Risiko „neues Feld wird still geerbt" verschwindet nicht, es zieht sich in den Factory-Body.
   Bei einer Call-Site lohnt der Umbau nicht (Pauls Lean bestätigt, Da-Mek-Analyse 2026-09-27).
2. **Pitfall dokumentiert:** `ToolLoopRequest` trägt am Klassenkopf eine Warnung — `toBuilder()`
   erbt `agent` und `memory`; Nested-Requests MÜSSEN beides explizit setzen (Verweis R-CC-15).
3. **Sicherheit erbt keine Felder blind:** Alle `getAgent()`-Reader sind null-safe
   (`CompactSessionTool:23-26` ehrlicher Fehler; `ToolService:166-169`/`:221` guarded) —
   `.agent(null)` ist der saubere Default für agentlose Nested-Loops.

## Consequences

- Kein Umbau, keine Migration; neue Nested-Loop-Builder müssen `agent`/`memory` explizit setzen —
  die Warnung ist die Verteidigungslinie (kein Compiler-Schutz).
- **Bekannter Rest:** `StreamingBridge` (stateful) und `ApiRetry` werden via toBuilder geteilt —
  sequenziell harmlos; **parallele** Nested-Calls würden um latch/responseRef konkurrieren.
  Follow-up-Kandidat (open-points).

# ADR-0071 — Compact-Policy: der Faktor gehört zur Delegation, die Schleife rechnet roh

> **Status:** accepted (2026-10-19, Paul) · **Fachdoc:** [compact.md](../compact.md) R-CC-18 ·
> **Architektur:** [compact-architektur.md](../compact-architektur.md) ·
> **Supersedes:** Teile von [ADR-0021](0021-po-slave-lifecycle-jit-compaction.md) (Faktor + Pre-Turn-Gate)
> und [ADR-0070](0070-compact-decision-in-one-component.md) (Budget-Quelle, Pre-Turn-Gate)

## Context

`AbstractAgent` trug ein `compactFactor` (Sklaven `0.7`) und leitete daraus
`compactAfterTokens() = autoCompactAfter × compactFactor` ab. Dieser Wert war an **zwei** Stellen die
Schwelle:

1. Pre-Turn-Gate in `AbstractAgent.doCall` — vor dem ersten Call eines Turns,
2. `HandleToolLoopCompactComponent` — nach jeder Tool-Runde (Direkt-Compact und 95-%-Band).

`tokenContextUsedInPercent()` rechnete dagegen gegen die **rohe** Config. Bei `autoCompactAfter = 250 000`
ergab das drei verschiedene Aussagen über denselben Zustand: Anzeige 70 %, Hint bei 66,5 % des Budgets,
Direkt-Compact bei 70 %. Der Faktor beschreibt aber gar keine Eigenschaft des Agenten — er beschreibt eine
**Beziehung**: dieser Agent ist ein Wegwerf-Sklave von jemandem, der ihn gleich mit einer neuen Aufgabe
beliefert. Dieses Wissen hat der Agent nicht, sein Auftraggeber hat es.

## Decision

- **Der Faktor lebt bei der Delegation.** `PoDelegateTool` compactet einen Sklaven **vor** jeder
  Delegation, wenn sein Zähler über `0.7 × autoCompactAfter` liegt (`compactSlaveBeforeDelegation`).
  Die Konstante `SLAVE_COMPACT_FACTOR` liegt dort, nicht in `BuildPoAgentComponent`, nicht im Agenten.
- **In der Tool-Schleife gilt genau eine Schwelle: die rohe Config.** `HandleToolLoopCompactComponent`
  liest ausschließlich `req.getConfig().getAutoCompactAfter()` — Direkt-Compact `used > budget`,
  Hint `used > 95 % von budget`.
- **Kein Pre-Turn-Gate mehr.** `AbstractAgent` compactet nicht mehr heimlich vor einem Turn; damit
  entfallen `compactFactor`, `compactAfterTokens()` und die 3-Arg-Ctor-Kette der Sklaven. Die Anzeige
  (`tokenContextUsedInPercent`) und beide Entscheidungen rechnen jetzt auf derselben Skala.
- **Der Delegations-Compact ist keine zweite Implementierung:** er ruft `agent.compact(monitor)` und
  wertet den `CompactResult` — `MIN_COMPACT_MESSAGES` (R-CC-8) bleibt allein in `compact()` scharf.

## Consequences

- **Gut:** Ein Zustand, eine Zahl. Der Nutzer sieht 70 %, wenn 70 % gemeint ist, und der Hint kommt nicht
  mehr vor dem Budget.
- **Gut:** Der Agent weiß nichts über seine Rolle; Sklaven-Wissen bleibt beim PO-Tool.
- **Bewusst:** Ein Pre-Turn-Compact existiert nicht mehr. Für die Sklaven fällt das nicht auf — sie
  werden ausschließlich über `PoDelegateTool.dispatch()` erreicht, also greift die 0.7-Regel bei jeder
  neuen Nachricht. Nur **direkte** Agent-Calls (Headless, Tests) verlassen sich auf den In-Loop-Hint
  (> 95 %). Wer einen Sklaven zwischendurch leeren will, ruft wie bisher
  `compactPlan`/`compactDev`/`compactReview`.
- **Bewusst:** Die 0.7-Regel feuert jetzt **pro Delegation**, nicht pro Turn (ADR-0021 hatte einen
  tool-seitigen Trigger ausdrücklich verworfen). Das ist der Punkt: jede neue Aufgabe startet mit einem
  leanen Wegwerf-Context, auch mitten in einem langen Zyklus. Ein fehlgeschlagener Compact blockiert die
  Delegation nicht (`SKIPPED_SMALL`/`FAILED_EMPTY` werden still übergangen).
- **Grenze bleibt:** verglichen wird gegen das *konfigurierte* Budget, nie gegen das echte Modellfenster —
  Peon kennt es nicht.

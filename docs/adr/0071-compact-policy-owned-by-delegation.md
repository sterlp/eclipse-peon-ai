# ADR-0071 — Compact-Policy: der Faktor gehört zur Delegation, die Schleife rechnet roh

> **Status:** accepted (2026-10-19, Paul) · **Fachdoc:** [compact.md](../../org.sterl.llmpeon.core/docs/compact.md) R-CC-18 ·
> **Architektur:** [compact-architektur.md](../../org.sterl.llmpeon.core/docs/compact-architektur.md) ·
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
- **Kein Agent-Faktor, aber ein Pre-Turn-Check.** `AbstractAgent` compactet **nicht** nach einer
  Agent-Eigenschaft; geprüft wird am Anfang von `doCall` aber sehr wohl — **bevor** die frische
  User-Message ins Memory geht, gegen das **rohe** Budget, ein Versuch pro Turn, kein Hint
  (R-CC-20 in [compact.md](../../org.sterl.llmpeon.core/docs/compact.md)). Der Unterschied ist der Punkt: die Schwelle gehört
  niemandem persönlich, der Zeitpunkt „vor einer neuen Meldung" ist dagegen richtig.
  (Nachtrag 2026-10-19 nach externem Review: ohne diesen Check blieb jeder Turn ohne Tool-Runde
  ungeprüft — R-CC-18 greift nur im `isToolrequest`-Zweig.)
  Damit entfallen `compactFactor`, `compactAfterTokens()` und die 3-Arg-Ctor-Kette der Sklaven. Die
  Anzeige (`tokenContextUsedInPercent`) und alle Entscheidungen rechnen auf derselben Skala.
- **Der Delegations-Compact ist keine zweite Implementierung:** er ruft `agent.compact(monitor)` und
  wertet den `CompactResult` — `MIN_COMPACT_MESSAGES` (R-CC-8) bleibt allein in `compact()` scharf.

## Consequences

- **Gut:** Ein Zustand, eine Zahl. Der Nutzer sieht 70 %, wenn 70 % gemeint ist, und der Hint kommt nicht
  mehr vor dem Budget.
- **Gut:** Der Agent weiß nichts über seine Rolle; Sklaven-Wissen bleibt beim PO-Tool.
- **Bewusst:** Der Pre-Turn-Check prüft **vor** der neuen Meldung, nie vor einem Tool-Result. Ein
  stark überschießendes Tool-Result soll man nach dem Einfügen sehen und dann compacten — davor zu
  compacten gewinnt nichts, weil es danach trotzdem in den Context läuft (Paul 2026-10-19).
- **Bewusst:** Die 0.7-Regel feuert jetzt **pro Delegation**, nicht pro Turn (ADR-0021 hatte einen
  tool-seitigen Trigger ausdrücklich verworfen). Das ist der Punkt: jede neue Aufgabe startet mit einem
  leanen Wegwerf-Context, auch mitten in einem langen Zyklus. Ein fehlgeschlagener Compact blockiert die
  Delegation nicht (`SKIPPED_SMALL`/`FAILED_EMPTY` werden still übergangen).
- **Grenze bleibt:** verglichen wird gegen das *konfigurierte* Budget, nie gegen das echte Modellfenster —
  Peon kennt es nicht.

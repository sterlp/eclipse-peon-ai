# ADR-0070 — Compact-Entscheidung in einer Komponente, Folgen beim Ausführenden

> **Status:** accepted (2026-10-18) · **Fachdoc:** [compact.md](../compact.md) R-CC-18 ·
> **Architektur:** [compact-architektur.md](../compact-architektur.md) ·
> **Supersedes:** Teil von [ADR-0056](0056-compact-component-and-render-modes.md) (Trigger-Funnel)

## Context

Innerhalb der Tool-Schleife entschied `ToolService.executeLoop` selbst über Compact: ein äußeres Gate
`ranTool(response, CompactSessionTool.NAME)` und ein Turn-Merker `isCompactedThisTurn` steuerten, ob
nach einer Runde der Zähler re-derived und ob ein Hint angehängt wurde. Zwei Beobachtungen:

1. **Der Hint ist eine Bitte, kein Schutz.** Er wurde *nach* dem Provider-Call angehängt und ist per
   `containsMessage` auf **einen pro Memory** begrenzt. Ignoriert das Modell ihn, eskaliert nichts —
   der nächste Call geht unverändert raus und stirbt am Modell-Limit (beobachtet: 974k vs. n_ctx 170k).
   Ein harter Auslöser existierte nur einmal pro Turn (`AbstractAgent.doCall`, vor dem ersten Call).
2. **Ein Ereignis-Merker beantwortete eine Zustandsfrage.** `isCompactedThisTurn` ist sticky über den
   Turn; gebraucht wird aber „hat der Compact *dieser* Runde den Speicher verkleinert?". Folge: ein
   späteres `SKIPPED_SMALL` re-derived erneut (idempotent, aber ein überflüssiger Static-Rebuild), und
   der Loop weiß Dinge, die ihn nichts angehen.

## Decision

**Die Entscheidung ist zustandsbasiert und liegt in einer Komponente.**

- Neue core-Komponente `HandleToolLoopCompactComponent` (`org.sterl.llmpeon.compact`), aufgerufen am
  Ende jeder Tool-Runde vor dem nächsten Call. Drei Ausgänge über **einem** Zustand
  (`memory.getTotalTokenUsed()` gegen `agent.compactAfterTokens()`):
  1. `used > Budget` und `size > MIN_COMPACT_MESSAGES` → `agent.compact(monitor)` **direkt**
     (ein Versuch pro Turn; `SKIPPED_SMALL`/`FAILED_EMPTY` wird nicht wiederholt).
  2. `used > 95 %` und ≤ Budget → `COMPACT_HINT` (R-CC-4-Dedup unverändert).
  3. sonst nichts.
- **Die Folgen eines Compacts hängen am Ausführenden:** `reevaluateTokens()` + Static-Rebuild macht,
  wer den Compact ausgelöst hat — die Komponente im Direkt-Pfad, `CompactSessionTool` im Tool-Pfad.
- **`ranTool(...)`, `compactedThisTurn`, `markCompacted()`, `isCompactedThisTurn()` entfallen
  ersatzlos.** `executeLoop` ruft nur noch die Komponente.
- Das Budget ist das **Agent-eigene** (`autoCompactAfter × compactFactor`), nicht die rohe Config —
  sonst verlieren die PO-Sklaven ihr bewusst früheres Compacten.
- Der **Pre-Turn-Gate in `AbstractAgent.doCall` bleibt**: dort ist die frische User-Message noch nicht
  im Memory und darf nicht in die Zusammenfassung wandern. Gleiche Regel, anderer Zeitpunkt.

## Consequences

- **Gut:** Ein Fehler hat einen Ort. Die drei Regeln sind ohne Loop testbar (Memory + Agent-Stub),
  die bestehenden Loop-Tests bleiben grün. Kein Sonderfall mehr für „der Agent hat gerade compacted".
- **Gut:** Der Loop ist frei von Compact-Wissen — er führt einen Loop, sonst nichts.
- **Preis:** `AiAgent` muss sein Budget ausweisen (`compactAfterTokens()`); heute kennt das Interface
  nur `tokenContextUsedInPercent()` (das gegen die rohe Config rechnet).
- **Grenze, ehrlich benannt:** Rule 1 vergleicht gegen das *konfigurierte* Budget, nicht gegen das
  echte Modellfenster — das kennt Peon nicht. Ein falsch gesetztes `autoCompactAfter` verschiebt den
  Schutz, ersetzt ihn aber nicht. Ebenso bleibt bei `size ≤ MIN_COMPACT_MESSAGES` R-CC-8 scharf: dann
  ist der Provider-Fehler der ehrliche Ausgang.
- **Nicht betroffen:** R-CC-2 (Reevaluate nur bei COMPACTED), R-CC-3 (Result-Status), R-CC-4 (Dedup),
  R-CC-8 (Mindest-History), R-CC-14 (monitor-freier Service).

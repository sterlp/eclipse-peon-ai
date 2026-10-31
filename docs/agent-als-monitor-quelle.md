# Agent als Monitor-Quelle — geparktes Proposal

> **Status: ⏳ geparkt** (2026-10-31, Paul — IDE-Wechsel zur Web Factory; Wiederaufnahme mit
> Proposal-Überarbeitung durch Paul). Ausgangsdokumente (Paul-WIP, **untracked**, liegen in
> `peon-plan/`): `cleaner-agent-pass-through.md` (Proposal v2) +
> `cleaner-agent-pass-through-review.md` (Review v1 + Nachspann mit Pauls Entscheidung).
> Dieses Doc ist self-contained — die peon-plan-Dateien können verschwinden.

## Das Proposal (v2, Paul 2026-10-31)

`AiAgent` statt `origin`-String als Quelle aller `AiMonitor`-Callbacks: Jede Methode bekommt
`AiAgent source`, der Agent ruft `monitor.onXxx(this, msg)` — kein `OwnerMonitor`, kein
fill-if-null, `source == null` erlaubt („kein Agenten-Bezug"). `LoopOwner` entfällt, alle Loops
haben eine Quelle (Compact über einen pro Aufruf erzeugten `CompactAgent`-Stub, Search über
`SearchAgent extends AbstractAgent` mit Rolling-Block im Agent-Memory). Bridges/Retry bekommen den
Bezug aus dem `ToolLoopRequest`. `origin`-Felder auf `SimpleMessage`/`OnPartialAiResponse` fallen;
History bleibt unverändert („Quelle ist vergangenheitslos"). Sechs Inkremente (Vertrag →
Bridge/Retry → Stempel raus → CompactAgent → SearchAgent → aifactory).

Pauls Ziele dahinter: **Async-Agenten** teilen sich einen Root-Monitor — die Quelle im Callback
soll Streams pro Agent trennen; **AgentsTool** (WIP) hält eine Map/Liste von Agenten statt
Einzel-Tool-Calls; langfristig ersetzt `AgentsTool` das `PoDelegateTool` (erst synchron wie heute);
Ziel: **Custom Agents können auch „Tools" sein** — man könnte sich seinen eigenen Jon bauen.

## Review v2 — REJECTED (Da Dok 2026-10-31, ohne ADRs; vom Jon am IST verifiziert)

V2 ist deutlich besser als v1 (null erlaubt löst den R5↔R6-Widerspruch, IST korrigiert, Blast
Radius ehrlich: ~15 Core-Dateien + 171 Overrides in Tests) — aber drei Blocker bleiben, alle am
Code verifiziert:

1. **Der Hauptgewinn Async wird von keinem Inkrement geliefert.** `AsyncMonitor.onChatResponse`
   (`AsyncMonitor.java:22-26`) cached nur und leitet **nie** an den Root weiter;
   `AgentsTool.dispatch` (`AgentsTool.java:152`) gibt dem Sklaven genau diesen Wrapper. Auch mit
   `source`-Parameter sterben die Streams im Wrapper. Die Async-Lücke ist der schluckende Wrapper —
   kein Tabellen-Eintrag, kein Inkrement.
2. **R7/R8 brechen dokumentiertes Verhalten.** `origin` ist Browser-Vertrag (`chat.html` liest
   `data-agent` als Merge-Key, A7), **persistierter History-Bestandteil** (Restore zeigt Namen aus
   der History — ✅-BDD in [agenten-namen-im-chat.md](../org.sterl.llmpeon/docs/agenten-namen-im-chat.md),
   `DelegatedAnswerRestartTest`) und Autorschafts-Mechanismus (`ProjectRuntime.authorTable:111-121`,
   `finalTeamAnswerOf:197-203` attributiert History-Zeilen per Tool-Name). „Beim Restore keinen
   Namen zeigen" ist eine Verhaltensänderung gegen dokumentiertes ✅ — entscheidet Paul, nicht das
   Proposal.
3. **Inc-Reihenfolge nicht grün-haltbar.** Inc 3 löscht LoopOwner/Stempel, Inc 5 baut erst
   SearchAgent → Such-Loop zwischenzeitlich ohne Herkunft (rot: `SearchAgentToolTest`,
   `ToolServiceTest`); die origin-Entfernung bricht den aifactory-Build, der erst Inc 6 ist.

**Doc-Konflikte (neu belastbar):** [streaming-display.md](../org.sterl.llmpeon/docs/streaming-display.md)
**R20 ✅** hat dieselbe Wertklasse (Turn-Zeit) bewusst **fürs Payload** entschieden, weil der
Monitor bei Delegation geteilt wird — R2 entscheidet die Gegrichtung, ohne R20 zu erwähnen.
[compact-lock.md](../org.sterl.llmpeon.core/docs/compact-lock.md) (R-CT-7, UC-CT-14): das
Compact-Ende-Event wird ausschließlich in `AbstractAgent.announce` emittiert — R5s Verlagerung
kollidiert damit.

## Richter-Zusätze (Jon, am IST verifiziert)

- **Der Haken für „Custom Agents als Tools" existiert schon:** `AiAgent.isTool()`
  („If it is an agent just to be used as tool", `AiAgent.java:148-153`, Default false). Das
  Proposal nutzt ihn nicht.
- **Der echte Blocker für „Agents als Tools"** (und für PoDelegateTool→AgentsTool, egal ob sync
  oder async) liegt nicht im Monitor, sondern in der **Autorschaft**: sie läuft über den
  Tool-Namen (`authorTable` mappt `talkPlan`/`askDev`/… → Member, UC-WP-39). Ein generisches
  `askAgent(agentId, prompt)` kann nicht attributiert werden, weil
  `ToolExecutionResultMessage` keine Argumente trägt. Diese Design-Frage ist VOR der
  Quelle-vs-Stempel-Frage zu klären — sie entscheidet, was für Identität überhaupt reisen muss.
- **Positiv:** R4 ist billiger als angenommen — `ToolLoopRequest.owner.agent()` (@Nullable,
  `ToolLoopRequest.java:122-124`) existiert bereits.

## Split-Empfehlung (Jon)

1. **AsyncMonitor-Forward** — der Wrapper muss `onChatResponse` an den Root weiterleiten; kleiner
   Fix, liefert Async-Streams unabhängig von der Signaturfrage.
2. **„Agenten als Tools"-Story** (`async-agents.md`): AgentsTool mit Agenten-Liste, sync-first;
   Kern ist die Autorschafts-Lösung für generische Delegation (Attribuierung per Tool-Args oder
   pro-Call-Origin). Zielbild „eigener Jon". `isTool()` als existierender Anker prüfen.
3. **Quelle-vs-Stempel** erst danach als abwägbarer Umbau — mit R20- und Restore-Entscheidung von
   Paul (darf R8 das persistierte Namens-Verhalten brechen?).

## Offene Fragen (aus dem Proposal)

| # | Frage | Status |
|---|---|---|
| Q1 | Rolling-Block-Halter bei stateless-SearchAgent: Tool (View-scoped) oder Registry pro Agent-Id? | ❓ |
| Q2 | AgentsTool-Map-Key: Agent-Name oder stabile ID (= Ordnername Custom, NAME-Konstante Built-in — Pauls Entscheidung 2026-10-31)? | ❓ |
| Q3 | PoDelegateTool: bleibt bestehen oder wird auf AgentsTool abgebildet? (Paul-Ziel: ersetzt.) | ❓ |
| Q4 | (Jon) Autorschaft bei generischem `askAgent`: wie bleibt UC-WP-39/40 (Team-Antwort, Restart) attributierbar? | ❓ |

## Verwandt

- [Async Agents](../org.sterl.llmpeon.core/docs/async-agents.md) — AgentsTool-Entwurf + Pauls
  Entscheidungen 2026-10-31 (Agenten-Liste, Search-Agent als Mitglied → R-LSR-7)
- [Search Agent Tool](../org.sterl.llmpeon.core/docs/search-agent-tool.md) — R-LSR-7 🚧
- [Agenten-Namen im Chat](../org.sterl.llmpeon/docs/agenten-namen-im-chat.md) — origin-Vertrag,
  Identität vs. Anzeigename 🚧
- [Streaming Display](../org.sterl.llmpeon/docs/streaming-display.md) — R20 (Payload vs. Monitor)
- [Compact-Lock](../org.sterl.llmpeon.core/docs/compact-lock.md) — UC-CT-14
- aifactory: [chat.md](../aifactory/docs/chat.md), `SseTurnMonitor`, `ProjectRuntime.authorTable`

# ADR-0025: PO-Status im Header via gezogener `NamedAgent`-Liste; Ork-Sklaven sind eigene Identitäten

**Status:** Accepted

## Context

Das Header-Feature „Agenten-Status" zeigte Agenten wiederholt **doppelt** (aktiver Plan/Dev zweimal;
„Search läuft → Dev doppelt"). Die Wurzel war nicht ein Render-Bug, sondern **Responsibility-Bleed**:

- **Kollidierende Namen.** Der Sub-Agent-Chip-Kanal (`AiMonitor.onSubAgent`) trug Anzeige-Namen, die
  **identisch** zu echten Roster-Zeilen waren (`"Peon-Plan"`/`"Peon-Dev"`). Ein Chip kollidierte mit
  der gleichnamigen Zeile.
- **Observer-State in der View.** `AIChatView` hielt ein `workingSubAgents`-Set und implementierte
  `onSubAgent` — Orchestrierungs-Zustand in einer reinen UI-Klasse, der über Agenten-Wechsel **leakte**
  (nie geleert).
- **Service greift in Tool-Interna.** `PeonAiService.getRoster()` peekte die Sklaven-Felder von
  `JonDelegateTool` (`peekPlanSlave/peekDevSlave`) und baute UI-Render-Records (`RosterSnapshot`,
  `AgentStatus`) — Zuständigkeit über View, Service und Tool verstreut.

Das widersprach [ADR-0005](0005-widget-owns-state-view-routes.md) (Widgets besitzen ihren State, die
View routet nur).

## Decision

**Der Status wird vom Widget *gezogen* (Pull/MVC), nicht per Observer gepusht — und Jons Sklaven
bekommen eigene Ork-Identitäten.**

- **Ork-Identität.** Jons zwei RAM-Sklaven heißen nach außen **Da Thinka** (Plan) und **Da Mek** (Dev)
  — bewusst **verschieden** von den wählbaren Agenten `Peon-Plan`/`Peon-Dev`. Der Ork-Name ist die
  öffentliche UI-Identität des Sklaven; die Namenskollision verschwindet **by construction**.
- **Der Agent besitzt sein Team.** `AiPoAgent` hält sein Team als
  `List<NamedAgent(String uiName, AiAgent agent)>` in fester Reihenfolge — **Da Boss** (Jon selbst),
  **Da Thinka** (Plan), **Da Mek** (Dev) — und gibt es über `getTeam()` heraus. Die zwei Sklaven werden
  **einmal erzeugt** und von `AiPoAgent` **und** `JonDelegateTool` geteilt — die Slave-**Factory-
  Supplier** in `JonDelegateTool` fallen weg (eine Instanz, ein Besitzer). Die Ork-/Boss-Namen sind hier
  die **einzige Quelle**; `JonDelegateTool` zieht seinen Log-`uiName` daraus statt Literale zu doppeln.
- **Blatt-Regel für den Boss.** Da Boss ist der Orchestrator und darf nicht gleichzeitig mit einem
  Sklaven glühen: `working` = `isWorking() && kein Sklave arbeitet`. So glüht Jon beim eigenen Denken,
  aber während der Delegation trägt der Sklave den Ball (kein „🟢 immer auf Jon"). Die Regel lebt im
  Pure-Model, nicht im Widget.
- **Pull statt Observer.** `AiAgentStatusWidget` bekommt `Supplier<List<NamedAgent>>` und **rendert
  immer die Liste**, wobei es je Eintrag `isWorking()`/`getTotalTokenUsed()` **live** liest. Refresh
  reitet auf dem vorhandenen Monitor-Lebenszyklus mit (`onChatMessage` + einmal am Ende). `onSubAgent`,
  `workingSubAgents`, `getRoster` und die Render-Records entfallen aus dem aktiven Pfad.
- **Ein einziger Choke-Point für „ist der Aktive ein Orchestrator".** Die eine `instanceof`-Prüfung
  lebt in `PeonAiService.getStatusAgents()`
  (`getActiveAgent() instanceof AiPoAgent po ? po.getTeam() : List.of()`) — **nicht** im Widget und
  **nicht** auf dem `AiAgent`-Interface. Das Widget bleibt typ-agnostisch, das Interface bleibt schlank.
- **Namens-Paare.** `AiPoAgent` ↔ `getTeam()` ↔ `NamedAgent` ↔ `AiAgentStatusWidget` — konsistent.

## Consequences

- Die **Doppel-Anzeige-Bug-Klasse ist konstruktiv erledigt**: keine Namenskollision, kein leakendes
  Observer-Set, kein Zustand über Agenten-Wechsel.
- Deckt sich wieder mit [ADR-0005](0005-widget-owns-state-view-routes.md); der Responsibility-Bleed ist
  aufgelöst (der Agent besitzt sein Team, das Widget zieht, der Service hat den einen Choke-Point).
- **MVP-Scope:** Nur Jon zeigt eine Status-Liste (Da Boss + seine zwei Orks); andere Agenten zeigen
  **nichts**. Die frühere „aktiver-Agent-Kontextzeile" und der **Search-Chip** entfallen. `AiMonitor.onSubAgent` bleibt als
  ruhende No-op-Default stehen (kein Konsument mehr), damit Search **später** als eigenes transientes
  Konzept nachrüstbar ist — ohne es erneut auf Agenten-Namen zu legen.
- Die Sklaven werden **eager** erzeugt (leer/0k bis zur ersten Delegation) statt lazy — billig, RAM-only
  (ändert nichts an [ADR-0024](0024-po-slaves-ram-only.md): weiterhin kein History-File).
- Ein zweiter Orchestrator wäre ein 2-Zeilen-Upgrade: Rollen-Interface `AgentTeamProvider` extrahieren
  und den einen `instanceof` tauschen — **YAGNI** bis dahin.

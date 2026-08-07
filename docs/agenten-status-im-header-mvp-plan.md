# Agenten-Status im Header — MVP-Neubau (Bugfix-Plan)

> **Status: PLAN, noch nicht gebaut.** Ersetzt die bisherige „active-scoped Roster + onSubAgent-Chips"-
> Umsetzung ([agenten-status-im-header.md](agenten-status-im-header.md)) durch einen **einfachen
> Pull/MVC-Ansatz** ([ADR-0025](adr/0025-po-status-widget-named-agents.md)). Ziel: die Doppel-Anzeige-
> Bugs **konstruktiv** killen und den Responsibility-Bleed auflösen. Inkremente einzeln baubar + grün,
> damit dieser Plan über `/compact` erhalten bleibt.

## Warum Neubau statt Weiterflicken

Die alte Umsetzung zeigte Agenten doppelt (aktiver Plan/Dev zweimal; „Search läuft → Dev doppelt").
Grund war **kein** Render-Bug, sondern verstreute Zuständigkeit (Bleed):

| Bleed | Wo | Neu |
| --- | --- | --- |
| Chip-Name == echter Agenten-Name (`Peon-Plan`/`Peon-Dev`) → Kollision | `onSubAgent`-Kanal | **Ork-Namen** (Da Thinka/Da Mek), eigene Identität — keine Kollision möglich |
| Orchestrierungs-State in der UI, leakt über Wechsel | `AIChatView.workingSubAgents` + `onSubAgent` | **entfällt** — Widget zieht live, hält keinen State |
| Service baut UI-Records & peekt Tool-Interna | `PeonAiService.getRoster()` → `JonDelegateTool.peek*Slave()` | **Agent besitzt sein Team** (`AiPoAgent.getTeam()`); Service hat **einen** Choke-Point |

Deckt sich wieder mit [ADR-0005](adr/0005-widget-owns-state-view-routes.md) (Widget besitzt State, View
routet nur).

## Zielbild (BDD — Paul)

```
1. GIVEN ich bin im Dev-Mode        WHEN ich auf den PO-Agenten wechsle
   THEN sehe ich oben drei Einträge: "Da Boss (0k)" · "Da Thinka (0k)" · "Da Mek (0k)"  (kein Ball)
2. GIVEN irgendein anderer Agent ist aktiv
   THEN zeigt das Status-Widget NICHTS
3. GIVEN PO-Mode                    WHEN Jon denkt selbst (delegiert gerade nicht)
   THEN glüht "🟢 Da Boss" (nur der Chef)
4. GIVEN PO-Mode                    WHEN Jon startet Da Mek
   THEN glüht "🟢 Da Mek" — Da Boss bleibt ruhig (Blatt-Regel)
5. WHEN Da Mek zurückkommt
   THEN glüht Da Boss weiter, solange Jon abschließt; am Ende alle drei ohne Ball
```

## Design (beschlossen)

- **`NamedAgent(String uiName, AiAgent agent)`** — schlichter Record (Kern).
- **`AiPoAgent`** hält `List<NamedAgent>` in fester Reihenfolge (**Da Boss** → Jon selbst (`this`),
  **Da Thinka** → Plan-Sklave, **Da Mek** → Dev-Sklave) und gibt sie über **`getTeam()`** heraus. Die
  zwei Sklaven werden **einmal** erzeugt und von `AiPoAgent` **und** `JonDelegateTool` **geteilt** — die
  Slave-**Factory-Supplier fallen weg**. Die Ork-/Boss-Namen leben **an einer Stelle** (die `NamedAgent`-
  Liste ist die Quelle); `JonDelegateTool` zieht seinen Log-`uiName` aus derselben Quelle statt Literale
  zu duplizieren (sonst driftet „Da Mek" im Log gegen die Anzeige).
- **`PeonAiService.getStatusAgents()`** — der **eine** `instanceof`-Choke-Point:
  `getActiveAgent() instanceof AiPoAgent po ? po.getTeam() : List.of()`. Nicht im Widget, nicht auf
  dem `AiAgent`-Interface.
- **`AiAgentStatusWidget`** (MVC-View, SWT `Label`) bekommt `Supplier<List<NamedAgent>>`, **rendert
  immer die Liste**: `uiName` aus `NamedAgent`, `🟢`/Kontextgröße live aus dem `AiAgent`
  (`isWorking()`/`getMemory().getTotalTokenUsed()`). Kennt weder PO noch `instanceof`.
- **Refresh:** `AiMonitor.onChatMessage` (jede Iteration) + **einmal am Ende**
  (`lockWhileWorking(false)`/`onCallCompleted`). Kein Observer, kein Set. **Wichtig:** der Ball auf einem
  Sklaven aktualisiert sich nur, weil der Sklave über **Jons** Monitor streamt
  (`slave.call(prompt, this.monitor)` in `JonDelegateTool`) → dessen `onChatMessage` triggert den
  Widget-Pull. Dieses Monitor-Durchreichen ist der Mechanismus — nicht „aufräumen".

### Die Blatt-Regel (trivial, pure)
Da Boss ist der Orchestrator, also darf er **nicht** gleichzeitig mit einem Sklaven glühen (sonst „🟢
immer auf Jon"). Regel im Pure-Model:
- **Sklave** (Da Thinka/Da Mek): `working = agent.isWorking()`.
- **Da Boss** (erste Zeile): `working = agent.isWorking() && kein Sklave arbeitet`.

Ergebnis: Jon komponiert allein → Da Boss glüht; Jon delegiert an Da Mek → nur Da Mek glüht, Da Boss
ruhig; Da Mek kommt zurück, Jon schließt ab → Da Boss glüht kurz weiter; fertig → alle drei ruhig.

## Encapsulation — was wir sauber rausziehen & testen

- **Pure Render-Model** `AiAgentStatusModel.rows(List<NamedAgent>) -> List<Entry(text, working)>` —
  SWT-frei, **headless testbar** (wie das heutige `AgentRosterModel`), inkl. **Blatt-Regel** (Boss
  glüht nur, wenn kein Sklave arbeitet). Das ist der eigentlich testbare Kern; die SWT-`setText`-Zeile
  im Widget wird **nicht** getestet (wie `TokenHeaderWidget`).
- **`AiPoAgent.getTeam()`** — testbar: liefert genau [Da Boss(=this), Da Thinka, Da Mek] auf die
  **geteilten** Sklaven-Instanzen; vor der ersten Delegation 0k/leer.
- **`PeonAiService.getStatusAgents()`** — testbar: PO aktiv → 2 Einträge; Dev/andere aktiv → leer.
  (Wie alle `PeonAiServiceTest` `assumeTrue`-geskippt headless; die Kern-Logik ist aber der Pure-Mapper
  oben, der headless läuft.)

## Inkremente (einzeln baubar + grün)

**Inc 0 — Denken (erledigt):** [ADR-0025](adr/0025-po-status-widget-named-agents.md) + dieser Plan.

**Inc 1 — Team im Agenten (nur *hinzufügen*, nichts löschen). ✅ gebaut, grün.**
`NamedAgent`-Record; `AiPoAgent` bekommt `List<NamedAgent> getTeam()` = [Da Boss(=`this`), Da Thinka,
Da Mek]. Die zwei Sklaven werden **einmal** erzeugt und in `PeonAiService` an `AiPoAgent` **und**
`JonDelegateTool` durchgereicht (statt lazy per Factory). `JonDelegateTool` nimmt jetzt die konkreten
Sklaven; die Factory-Supplier werden **nur intern** überflüssig, bleiben aber samt `peek*Slave()`
**vorerst stehen** (`peek*Slave()` gibt jetzt einfach das geteilte Feld zurück) — damit das noch
lebende `PeonAiService.getRoster()`/`HeaderBar` **weiter kompiliert und grün bleibt**. Löschen erst in
Inc 5. *Test:* `getTeam()` liefert 3 Einträge (Da Boss + 2 Orks) auf die geteilten Instanzen, 0k idle.

**Inc 2 — Choke-Point. ✅ gebaut, grün.**
`PeonAiService.getStatusAgents()` (das eine `instanceof`). *Test:* PO → 3, Dev → 0 (leer).

**Inc 3 — Pure Model (inkl. Blatt-Regel). ✅ gebaut, grün.**
`AiAgentStatusModel.rows(...)`: Zeilentext `uiName (Xk)`; `working` = Sklave→`isWorking()`, Boss→
`isWorking() && kein Sklave arbeitet`. *Tests (headless):* alle-idle→kein-Ball · Boss-arbeitet-allein→
Ball-nur-Boss · Da-Mek-arbeitet→Ball-nur-Da-Mek-Boss-ruhig · leere Liste→keine Zeilen.

**Inc 4 — Widget. ✅ gebaut, grün.**
`AiAgentStatusWidget` (`Label`, `Supplier<List<NamedAgent>>`, nutzt das Pure-Model) ersetzt
`AgentRosterWidget` in `HeaderBarWidget` (Divider/Token-Readout bleiben). `HeaderBarWidget`-Konstruktor
nimmt jetzt `Supplier<List<NamedAgent>>` statt `Supplier<RosterSnapshot>` + `workingSubAgents`; in
`AIChatView` auf `aiService::getStatusAgents` verdrahtet. `AgentRosterWidget` + `workingSubAgents`/
`onSubAgent` bleiben ungenutzt stehen (Löschen in Inc 5). *Baut.* — **ab hier sichtbar**, Paul smoke-tested
das 5-Schritt-BDD.

**Inc 5 — Alt-Pfad rausreißen. ✅ gebaut, grün.**
Gelöscht: `AIChatView.onSubAgent`-Override + `workingSubAgents`(+`.clear()`); `PeonAiService.getRoster()`/
`RosterSnapshot`/`AgentStatus`/`statusOf`/`slaveStatus`; `AgentRosterModel`+`AgentRosterModelTest`+
`AgentRosterWidget` (Dateien); `JonDelegateTool.peek*Slave()`. Der Agent-Switch pullt jetzt aktiv
(`headerBar.refreshRoster()` statt Chip-`clear()`). Alte `PeonAiServiceTest`-Roster-Tests ersetzt durch
`test_status_agents_follow_switch_no_stale_state` (PO→Dev = Team→leer, Live-Pull ohne Cache).
`AiMonitor.onSubAgent` bleibt **ruhender No-op-Default** (kein Konsument), `SearchAgentTool`-Aufrufe
laufen ins Leere — **Search-Chip = Post-MVP**. Refresh-Trigger: `onChatMessage` (🟢 an) +
`lockWhileWorking` (🟢 aus) + Switch/Token/Response (idempotente Live-Pulls). *Baut, alle Tests grün.*

**Inc 6 — Doku + Memory.**
`agenten-status-im-header.md` auf das neue Design umschreiben (alte Bug-Tabelle/Chips raus);
`docs/index.md` + Memory nachziehen.

## Offen
Keine Architektur-Frage offen — Design beschlossen. Search-Sichtbarkeit ist bewusst **Post-MVP**.

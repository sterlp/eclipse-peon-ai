# Agenten-Status im Header

> **Status:** Roster **✅ gebaut** (MVP-Neubau 2026-09, [ADR-0025](adr/0025-po-status-widget-named-agents.md); Bau-Details im
> archivierten [MVP-Plan](agenten-status-im-header-mvp-plan.md)). Per-Agent **Compact-Buttons**:
> **✅ done** (2026-09-14, Zyklus story/po-compact-2026-09-13; Decision 2026-09-13, User;
> UI-Verifikation = User-Smoke nach BDD unten).

## Ziel

Im Header — neben dem Session-Token-Readout (`↑ sent ↓ received`) — steht im **Jon-Modus** das Team
mit Namen, Kontextgröße und 🟢 auf dem arbeitenden Blatt-Worker:

```
Jon (Peon-PO) aktiv, idle:
│ ↑12k ↓8k  ·  Da Boss (12k) · Da Thinka (8k) · Da Mek (45k) · Da Dok (3k)      🔨 │

Jon delegiert an Da Mek → der Sklave glüht, Da Boss bleibt ruhig (Blatt-Regel):
│ ↑12k ↓8k  ·  Da Boss (12k) · Da Thinka (8k) · 🟢 Da Mek (45k) · Da Dok (3k)   🔨 │

Nicht-PO-Agent aktiv:
│ ↑12k ↓8k  ·                                                                     🔨 │
```

**Gebautes Design (MVP, Pull/MVC statt Observer):**

- **`NamedAgent(String uiName, AiAgent agent)`** — schlichter Record. `AiPoAgent.getTeam()` hält die
  feste Reihenfolge **Da Boss (=`this`) → Da Thinka → Da Mek → Da Dok** (BuildPoAgentComponent.java:135);
  die Sklaven-Instanzen werden einmal erzeugt und an `AiPoAgent` **und** `PoDelegateTool` geteilt.
- **`PeonAiService.getStatusAgents()`** — der eine `instanceof`-Choke-Point:
  `getActiveAgent() instanceof AiPoAgent po ? po.getTeam() : List.of()`. Kein Orchestrierungs-State
  in der UI, kein Roster-Leak über Agenten-Wechsel (die Doppel-Anzeige-Bugs des alten
  Roster/Chip-Designs sind damit konstruktiv tot; `onSubAgent` ist ein ruhender No-op-Default).
- **`AiAgentStatusWidget`** (`Composite` mit einem `Label`, kein SWT-Test): rendert die Zeilen mit
  `   ·   ` getrennt, Präfix `🟢 ` bei `working`; Text `uiName (Xk)` mit
  `getMemory().getTotalTokenUsed()`. State-los — jeder `refresh()` zieht live.
- **Blatt-Regel** (in `AiAgentStatusModel`, headless getestet): Sklave glüht bei eigenem
  `isWorking()`; **Da Boss** nur, wenn er arbeitet **und kein Sklave arbeitet**.
- **Refresh-Trigger:** `onChatMessage` (🟢 an), `lockWhileWorking(false)`/Turn-Ende (🟢 aus),
  Agenten-Wechsel, Token/Response — idempotente Live-Pulls.

## Per-Agent Compact-Buttons — ✅ done (2026-09-14; Decision 2026-09-13, User)

Jede **Sklaven-Zeile** (Da Thinka, Da Mek, Da Dok) bekommt einen kleinen **Icon-Button** (Compact-Icon,
keine Beschriftung, Tooltip `Compact Da X`). Klick komprimiert **genau diesen Agenten**.

**Warum:** Beim Compact von Jon lief bisher ein impliziter Cascade über alle drei Sklaven
(3 sequenzielle LLM-Calls, unsichtbar im UI → „Hänger"-Gefühl). Der Cascade ist gestrichen
([po-agent-jon.md](po-agent-jon.md) R18 — Compact = nur der Agent selbst); damit der User trotzdem
gezielt einen Sklaven-Kontext freigeben kann, gibt es den **expliziten** Button.

**Regeln:**

1. **Nur die drei Sklaven-Zeilen** bekommen den Button. **Da Boss nicht** — als aktiver Agent hat er
   den Compact-Button in der Action Bar (der nach R18 nur noch Jon selbst komprimiert).
2. Klick → Eclipse `Job` (gleiche Mechanik wie `AIChatView.doCompressContext`:
   `inFlightTurns`/`monitorRef`/`lockWhileWorking`): `agent.compact(viewMonitor)` für **diesen einen**
   Agenten. Die Zusammenfassung streamt über den View-Monitor sichtbar in den Chat (wie heute).
3. **Disabled**, während der Agent `isWorking()` oder ein Turn/Compact in-flight ist
   (`inFlightTurns > 0`) — kein Concurrent-Compact auf einem Agenten, der gerade arbeitet.
4. **Feedback:** Job-Ergebnis in der Statuszeile (`Compacted Da Mek`); Skip (< 3 Messages, R16 —
   nach jedem Compact bleiben exakt 2, Re-Compact = Noop) →
   `Nothing to compact` statt Stillstand. Danach Roster-Refresh (Kontextgröße fällt sichtbar).
5. Reuse: `SwtUtil.createIconButton` (SwtUtil.java:30, Flat-Icon-Pattern wie der Header-Hammer).
   Dafür wird die Widget-Struktur von einem Label pro Roster auf **Zeilen-Composites** (Label +
   Button je Sklave) umgestellt; Nicht-PO-Modus bleibt ohne Roster.
   **UI-Detail (2026-09-14, `e46eab2`):** beide RowLayouts mit `wrap=false` (in der aktuellen
   SWT-Generation ist `wrap` per Default `true` — deshalb umbrauchte das Roster bei schmalem
   Fenster; jetzt wird rechts **geclippt**, Da Dok fällt zuerst weg) und `center=true` (Label und
   Button sitzen mittig — das Icon liegt exakt auf der Hammer-Mittelachse, auch in der 🟢-Zeile).
   Compact-Icon **theme-aware** (`compact.svg`/`compact_dark.svg`, Selektion wie beim Hammer).
6. Kein Auto-Refresh-Zwang über neue Observer — die bestehenden Pull-Trigger reichen.

**BDD:**
```
GIVEN Jon ist aktiv und Da Mek idle
WHEN der User drückt Da Meks Compact-Button
THEN wird AUSSCHLIESSLICH Da Mek komprimiert (ein LLM-Call), Da Boss/Da Thinka/Da Dok unverändert
AND die Statuszeile meldet "Compacted Da Mek" und die Kontextgröße fällt im Roster

GIVEN Da Mek hat < 3 Messages (nach jedem Compact exakt 2)
WHEN der User drückt Da Meks Compact-Button
THEN kein LLM-Call, Statuszeile meldet "Nothing to compact" (R16-Skip, Noop-Guard)

GIVEN Da Mek arbeitet gerade (🟢) oder ein Turn ist in-flight
THEN ist Da Meks Compact-Button disabled

GIVEN ein Nicht-PO-Agent ist aktiv
THEN gibt es keine Sklaven-Zeilen und damit keine Compact-Buttons
```

## Abgrenzung zum Token-Readout

| Anzeige | Quelle | Bedeutung |
| --- | --- | --- |
| `↑ sent ↓ received` ([token-usage.md](token-usage.md)) | `TokenStats`, `addTokenUsage` | **Session-kumulativ**, cross-agent, monoton |
| `Da Mek (45k)` | `agent.getMemory().getTotalTokenUsed()` | **Momentaner Kontext** des Agenten — fällt beim Compact |

## Tests (gebaut)

- `AiAgentStatusModelTest` (headless): Blatt-Regel — alle-idle → kein Ball · Boss arbeitet allein →
  nur Boss · Da Mek arbeitet → nur Da Mek, Boss ruhig · leere Liste → keine Zeilen.
- `AiPoAgentTest.getTeam()` — genau 4 Einträge (Da Boss + 3 Sklaven) auf die geteilten Instanzen.
- `PeonAiServiceTest.getStatusAgents()` — PO aktiv → 4, Dev aktiv → leer (`assumeTrue`-geskippt
  headless, läuft in der IDE).
- SWT-Darstellung selbst nicht getestet (wie `TokenHeaderWidget`).

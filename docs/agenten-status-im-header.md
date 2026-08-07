# Agenten-Status im Header

> **Status: GEBAUT ✅ (nicht committet).** Roster ist auf den **aktiven Agenten** gescopet: seine
> Zeile (Name + Kontextgröße) + 🟢 auf dem **Blatt-Worker**. Im **Jon-Modus** reiten seine zwei
> Sklaven (Plan, Dev) als feste Zeilen mit **ihrer eigenen** Kontextgröße mit. Search erscheint als
> transienter Chip. Ein `·` trennt Token-Readout und Roster optisch.

## Ziel

Im Header — neben dem Session-Token-Readout (`↑ sent  ↓ received`) — soll **immer der Roster des
aktiven Agenten** stehen: sein **Name** + **Kontextgröße**, und der **gerade arbeitende** Worker
bekommt einen **🟢 grünen Ball**. So sieht man jederzeit, *mit wem* man spricht, *wie voll* dessen
Kontext ist und *wer gerade werkelt* — auch wenn keiner arbeitet.

```
Peon-Dev aktiv, idle:
│ ↑12k ↓8k  ·  Peon-Dev (45k)                                        🔨 │

Peon-Dev aktiv, arbeitet direkt (kein Sub-Agent):
│ ↑12k ↓8k  ·  🟢 Peon-Dev (45k)                                     🔨 │

Jon (Peon-PO) aktiv, idle — seine Sklaven immer sichtbar, Plan vor Dev:
│ ↑12k ↓8k  ·  Peon-PO (12k) · Peon-Plan (8k) · Peon-Dev (45k)       🔨 │

Jon delegiert an Da Mek → der Sklave glüht, Jon bleibt ruhig:
│ ↑12k ↓8k  ·  Peon-PO (12k) · Peon-Plan (8k) · 🟢 Peon-Dev (45k)    🔨 │

Jon lässt suchen (transienter Chip, verschwindet bei „done"):
│ ↑12k ↓8k  ·  Peon-PO (12k) · Peon-Plan (8k) · Peon-Dev (45k) · 🟢 Search  🔨 │
```

**Warum auf den aktiven Agenten gescopet:** Die anderen registrierten Agenten (persistente Dev/Plan,
Scaffold, Custom-Agents) sind irrelevant, solange man nicht mit ihnen spricht — sie würden nur
rauschen (dieselbe Klasse wie Scaffold). Der Roster zeigt darum **den aktiven Agenten** und das, was
er real spawnt.

**Warum Jons Sklaven eine Ausnahme sind:** Wenn Jon aktiv ist, *ist* sein Team (Plan/Dev) die
relevante Arbeitsumgebung — man will sie **immer** sehen, nicht nur während er delegiert. Wichtig:
Das sind **Jons eigene RAM-only-Sklaven** (`JonDelegateTool.peekPlanSlave()/peekDevSlave()`), **nicht**
die persistenten Peon-Plan/Peon-Dev-Agenten — sie haben **eigene** Kontextgrößen. Beim Wechsel auf
Jon zeigt der Roster also die Größen *seiner* Sklaven (0k, solange er noch nie delegiert hat).

**Kein WARTET-Rauschen:** Es kann immer nur **einer** arbeiten (siehe unten). Statt „WARTET" an jeden
idle Worker steht der Roster ruhig da; nur der Blatt-Worker glüht.

## Die Blatt-Worker-Regel (wo sitzt das 🟢)

Das 🟢 sitzt immer auf dem **Blatt** der Aufruf-Kette:

* Eine **Sklaven-Zeile** glüht über ihr live gepeektes `isWorking()`; ein **Chip** glüht, solange sein
  `onSubAgent`-Signal aktiv ist. (Der Merge könnte ein `onSubAgent`-Signal auch einer namensgleichen
  Sklaven-Zeile zuordnen — das bleibt als Absicherung, wird aber nicht mehr ausgelöst, seit
  `JonDelegateTool` keine Chips mehr sendet.)
* Die **aktive Zeile** (der Orchestrator) glüht **nur**, wenn sie arbeitet **und nichts darunter**
  läuft. Jon glüht also, während er **selbst** denkt/formuliert — sobald er delegiert, trägt der
  arbeitende Sklave (oder der Search-Chip) das 🟢 und Jons Zeile bleibt ruhig.

Das war der eigentliche Bug-Fix: Jons `isWorking` ist während der ganzen Delegation korrekt true —
würde man ihn stumpf highlighten, verdeckt das, *welches Tool* gerade rechnet.

## Wichtige Randbedingung — nur einer arbeitet zugleich

`AbstractAgent.working` (AtomicBoolean) wird bei Call-Start gesetzt (Z.145) und im `finally`
zurückgesetzt (Z.173); `isWorking()` liest es. **Slaves und der Search-Agent laufen synchron im
selben Turn auf demselben Thread** — echte Parallelität gibt es nicht.

* Jons **Sklaven** sind persistente Lazy-Singletons auf dem `JonDelegateTool` (Felder
  `planSlave`/`devSlave`, erzeugt bei erster Delegation, danach am Leben → ihr RAM-Kontext trägt über
  Calls). Der Roster **peekt** sie (`peekPlanSlave/peekDevSlave` — erzeugt sie **nicht**, damit ein
  Peek keinen Sklaven eager hochfährt) und liest `getTotalTokenUsed()`/`isWorking()` live.
* Der **Search-Agent** ist kein `AiAgent` (kein `isWorking`) — sein Signal ist die
  `onSubAgent("Search", …)`-Klammer im `SearchAgentTool` → transienter Chip.

## Abgrenzung zum bestehenden Token-Readout

Zwei verschiedene Zahlen — der `·` trennt sie im Header optisch:

| Anzeige | Quelle | Bedeutung |
| --- | --- | --- |
| `↑ sent  ↓ received` (heute, [token-usage.md](token-usage.md)) | `TokenStats` im `TokenHeaderWidget`, via `addTokenUsage(TokenUsage)` | **Session-kumulativ**, cross-agent, wächst monoton bis View-Close |
| `Peon-Dev (45k)` (neu) | `agent.getMemory().getTotalTokenUsed()` pro Agent | **Momentaner Kontext** eines Agenten — fällt beim Compact |

## Architektur — Merge im Model, nicht im Widget

Reine, UI-freie Merge-Funktion, headless testbar:

```java
AgentRosterModel.build(AgentStatus active, List<AgentStatus> slaves, Collection<String> workingSubAgents)
    -> List<Entry(text, working)>
```

* **`active`** = `PeonAiService.getRoster().active()` — der aktive Agent (Name, `contextTokens`,
  `working`); `null`, wenn keiner aktiv ist.
* **`slaves`** = `getRoster().slaves()` — Jons feste Sklaven-Zeilen (Plan, Dev), leer für jeden
  anderen aktiven Agenten.
* **`workingSubAgents`** = die gerade laufenden Sub-Agent-`displayName`s aus `onSubAgent`.
* **Merge-Regel:** Ein Chip, dessen Name eine **echte Zeile** trifft (aktive Zeile **oder** eine
  Sklaven-Zeile), ist **Rauschen** und wird verworfen — dieser Agent ist bereits durch seine Zeile
  vertreten und glüht über sein eigenes `isWorking()`. Konkret: zuerst wird der Name der **aktiven
  Zeile** aus den Chips entfernt, dann glüht jede Sklaven-Zeile bei eigenem `working` **oder** wenn ihr
  Name in den Chips steht (dann dort **entfernt**). Danach glüht die aktive Zeile per Blatt-Regel
  (`working && !subBusy`). Was **übrig** bleibt (nur noch **zeilenlose** Sub-Agenten wie `Search`)
  wird als **transienter Chip** angehängt. Der Chip-Kanal ist damit **ausschließlich** für
  zeilenlose transiente Sub-Agenten — kein Chip kann je einen Agenten doppelt rendern.

**Kein Stale-State — immer das IST:** Weder Widget noch Model halten Zustand. Jeder `refresh()` liest
`getRoster()` (live `getActiveAgent()`, Sklaven-Peek, `getTotalTokenUsed()`, `isWorking()`) + die
`workingSubAgents`-Menge der View frisch. Ein Agenten-Wechsel oder `onSubAgent`-Flip schlägt beim
nächsten Refresh sofort durch. **Beim Agenten-Wechsel** (`AIChatView.onAgentChange`) wird
`workingSubAgents` **geleert** — laufende Chips gehörten zum Turn des vorherigen Agenten; ohne das
Leeren würde ein Chip aus einer Jon-Delegation (z. B. `Search`) am neu aktivierten Agenten hängen
bleiben.

## Das `onSubAgent`-Signal

Explizites Start/Ende-Signal aus den delegierenden Tools statt `onTool`-UI-Text zu parsen — eine
**Default-Methode** auf `AiMonitor` (bricht keine bestehende Lambda; SAM bleibt `onChatResponse`):

```java
default void onSubAgent(String displayName, boolean active) { /* no-op */ }
```

* **Nur `SearchAgentTool`** benutzt den Chip-Kanal: es klammert `executeLoop(...)` mit
  `onSubAgent("Search", true/false)` (`try/finally`, damit das 🟢 auch bei Exception ausgeht) → Chip.
  `Search` ist **zeilenlos** (kein `AiAgent`), darum ist der Chip die einzig richtige Darstellung.
* **`JonDelegateTool` sendet KEIN `onSubAgent` mehr.** Jons Sklaven haben feste Roster-Zeilen und
  glühen über ihr live gepeektes `isWorking()`. Ein Chip mit demselben **Namen** wie eine Roster-Zeile
  (`Peon-Plan`/`Peon-Dev`) kollidierte mit dieser Zeile und rendert den Agenten **doppelt** — genau
  der Bug. Der Chip-Kanal ist ausschließlich für **zeilenlose** transiente Sub-Agenten (Search).
  Der prompte Refresh bei Delegations-Start/-Ende kommt ohnehin über die `onChatMessage`/
  `onChatResponse`/`onTokenUsage`-Callbacks des Sklaven (die durch Jons Monitor laufen).

Die View hält die laufenden Sub-Agenten in `ConcurrentHashMap.newKeySet()`; `onSubAgent` add/remove +
`refreshRoster()` auf dem UI-Thread.

## Rendering

* **Plain `Label`** auf nativem Weiß (wie `TokenHeaderWidget`) — **kein `StyledText`**, das auf macOS
  read-only einen grauen Kasten rendert.
* **🟢 ist der einzige Highlight** (kein Fett), Präfix nur bei `working`.
* Header = `GridLayout(4)`: Tokens · `·`-Divider · Roster (FILL) · Hammer. Der Divider ist ein
  Schatten-graues `Label("·")`. Trenner im Roster `   ·   `; `requestReflow()` wie im
  `TokenHeaderWidget`, weil wachsende Zahlen/Chips die Breite ändern.

## Gebaute Dateien

* **`PeonAiService`** — `record AgentStatus(name, contextTokens, working)` +
  `record RosterSnapshot(active, slaves)` + `getRoster()` (aktiver Agent; im Jon-Modus zusätzlich die
  gepeekten Sklaven Plan→Dev). Hält jetzt das `JonDelegateTool` als Feld, um zu peeken.
* **`JonDelegateTool`** — `peekPlanSlave()`/`peekDevSlave()` (non-creating, nullable).
* **`AgentRosterModel`** (rein) — `build(active, slaves, workingSubAgents)` mit Blatt-Regel + Chip-Merge;
  Chips werden gegen die **aktive** Zeile **und** die Sklaven-Zeilen de-dupt (kein Doppel-Rendern).
* **`AgentRosterWidget`** — `Label`-basiert, 🟢-only; `Supplier<RosterSnapshot>` + `Supplier<List<String>>`.
* **`HeaderBarWidget`** — `GridLayout(4)` mit `·`-Divider; Ctor nimmt `Supplier<RosterSnapshot>`.
* **`AiMonitor`** — Default-Methode `onSubAgent(displayName, active)` (no-op).
* **`SearchAgentTool`** — `onSubAgent("Search", …)`-Klammer (try/finally). **`JonDelegateTool` sendet
  bewusst KEIN `onSubAgent`** (Sklaven glühen über `isWorking()`, Name-Kollision vermieden).
* **`AIChatView`** — `workingSubAgents`-Set + `onSubAgent`-Override; **`onAgentChange` leert das Set**;
  `aiService::getRoster` durchgereicht;
  `refreshRoster()` an `onChatMessage`/`onChatResponse`/`onTokenUsage`/`lockWhileWorking`/`onSubAgent`.

## Regressionstests

* **`AgentRosterModelTest`** (rein/SWT-frei → läuft **headless unter Maven**, 9 Tests): idle-Zeile ohne
  Highlight · aktiver Worker glüht allein · **Jon-Modus zeigt Plan-dann-Dev** · delegierender Jon
  ruhig, arbeitender Sklave glüht · `onSubAgent` glüht die Sklaven-Zeile **ohne Doppel-Chip** · Search
  als transienter Chip nach den Zeilen · kein aktiver Agent → nur Chips · **Chip mit aktivem Namen wird
  absorbiert (kein Doppel)** · **Search neben aktivem Agenten dupliziert ihn nie** (die zwei
  Doppel-Anzeige-Regressionen).
* **`PeonAiServiceTest`** — `test_roster_is_scoped_to_the_active_agent` · `..._active_follows_switch_no_stale_state`
  · `..._shows_jons_slaves_plan_then_dev_when_jon_is_active`. Wie alle `PeonAiServiceTest`
  `assumeTrue`-geskippt unter headless Maven/Tycho → laufen in der Eclipse-IDE.
* Die SWT-Darstellung selbst wird nicht getestet (wie beim `TokenHeaderWidget`).

## Gefixte Bugs (aus dem Bau)

| Symptom | Ursache | Fix |
| --- | --- | --- |
| Grauer Kasten hinter dem Roster | `StyledText` read-only rendert auf macOS grau | `Label` statt `StyledText` |
| „Peon-Dev fett obwohl nix aktiv" | Default-Aktiver ist `devAgent` (AgentService Z.95); Fett am Selektierten | Fett ganz raus; Highlight = 🟢 nur bei `working` |
| Scaffold/andere Agenten im Roster | `getAgents()` global gelistet | Roster auf den **aktiven** Agenten gescopet |
| 🟢 immer auf Peon-PO | Jons `isWorking` bei Delegation korrekt true — Orchestrator gehighlightet | Blatt-Regel: aktive Zeile glüht nur ohne laufenden Sub-Worker; `onSubAgent`/`isWorking` glühen den echten Worker |
| Plan/Dev auch außerhalb Jon sichtbar | globale Liste | nur im Jon-Modus, als **seine** Sklaven |
| Sklaven zeigten Größe der persistenten Dev/Plan | falsche Instanzen | Jons eigene Sklaven peeken (eigener Kontext) |
| „·" fehlte zwischen Tokens und Roster | kein Divider im Header | `·`-Label als 2. Grid-Spalte |
| Aktiver Plan/Dev **doppelt** angezeigt | `JonDelegateTool.onSubAgent("Peon-Plan"/"Peon-Dev")` erzeugt Chip mit **gleichem Namen** wie die Roster-Zeile; Merge de-dupte Chips nur gegen Sklaven-, nicht gegen die **aktive** Zeile | `JonDelegateTool`-Emission **entfernt** (Sklaven glühen über `isWorking`); Merge de-dupt Chips auch gegen die aktive Zeile |
| Such-Agent läuft → **Dev doppelt** (statt „Dev · Search") | ein aus einer Jon-Delegation **geleakter** `Peon-Dev`-Chip überlebte den Agenten-Wechsel (`workingSubAgents` nie geleert) und stand neben dem frischen `Search`-Chip | `onAgentChange` **leert** `workingSubAgents`; zusätzlich Emission entfernt + aktive-Zeile-De-dup |

## BDD

```
GIVEN Peon-Dev ist aktiv, niemand arbeitet
THEN zeigt der Roster nur "Peon-Dev (45k)" — keine Sklaven-Zeilen, kein 🟢

GIVEN Peon-Dev ist aktiv und arbeitet direkt (kein Sub-Agent)
THEN glüht "🟢 Peon-Dev (45k)"

GIVEN Jon (Peon-PO) ist aktiv
THEN zeigt der Roster "Peon-PO (…)" + seine festen Sklaven "Peon-Plan (…)" · "Peon-Dev (…)" (Plan vor Dev)
AND die Sklaven-Größen sind die SEINER RAM-Sklaven (0k, bevor er je delegiert hat)

GIVEN Jon delegiert an seinen Dev-Sklaven
WHEN der Sklave arbeitet (isWorking bzw. onSubAgent("Peon-Dev", true))
THEN glüht die feste "Peon-Dev"-Zeile — kein Doppel-Chip, Jons Zeile bleibt ruhig
WHEN die Delegation endet
THEN geht das 🟢 der Zeile wieder aus

GIVEN irgendein Agent startet den Search-Agenten
WHEN onSubAgent("Search", true) kommt
THEN erscheint hinter den Zeilen ein transienter Chip "🟢 Search"
WHEN onSubAgent("Search", false) kommt
THEN verschwindet der Chip
```

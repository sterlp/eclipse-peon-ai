# Sklaven-Kontext — was Da Thinka & Da Mek mitbekommen (Plan)

> **Status: PLAN, teils gebaut.** Jons RAM-Sklaven (Da Thinka = Peon-Plan, Da Mek = Peon-Dev,
> [ADR-0024](adr/0024-*)/[ADR-0025](adr/0025-po-status-widget-named-agents.md)) bekamen bisher deutlich
> weniger Kontext als der **aktive** Agent. Dieser Plan schließt die Lücken inkrementell, jedes
> Inkrement baubar + grün ([phasenweise arbeiten](../phasen-weise-arbeiten.txt)).

## IST / SOLL / WEIL

- **IST:** Der aktive Agent erhält seine Standing-Orders über den `StandingOrdersBuilder`
  (`WorkspaceMemoryTool`, `AgentsMdService`, `UserContext`, `PeonAiService`) **plus** einen
  Static-Context (`setStaticContext(dateInfo)` über `agentService.getAgents()`). Die Sklaven bekommen
  davon nur, was `JonDelegateTool.delegate()` in ihre `setUserContextInformations(...)` injiziert:
  geteiltes Memory, das gewählte Projekt (neu, s.u.) und — nur Da Mek — den `planPath`.
- **SOLL:** Die Sklaven arbeiten mit demselben *relevanten* Kontext wie der aktive Agent, damit sie
  nach den Projekt-Regeln planen/coden.
- **WEIL:** Da Mek codet und Da Thinka plant **ohne** die Ground-Rules (`AGENTS.md`) und **ohne** die
  operativen Regeln (eclipse\*-vor-disk\*, Refresh/Build nach disk-Writes, Datum/OS) — Fehlerquelle,
  die der aktive Agent nicht hat.

## Was der aktive Agent hat vs. die Sklaven

| Kontext | Quelle | Sklaven | Entscheidung |
| --- | --- | --- | --- |
| Geteiltes Memory | `WorkspaceMemoryTool` | ✅ hat | — |
| Gewähltes Projekt (Pfad/Info) | `UserContext` → `EclipseUtil.projectInfo` | ✅ **gebaut** | s. „Gebaut" |
| `AGENTS.md` (Basis-Ground-Rules) | `AgentsMdService.get()` | ❌ fehlt | **Inc 1** |
| Datum / OS / File-Access-Regeln | `setStaticContext(dateInfo)` | ❌ fehlt | **Inc 2** |
| `AGENTS-<agent>.md` (sklaven-spezifisch) | `AgentsMdService` (auf **aktiven** Namen gekeyt → bei Jon `AGENTS-PO.md`) | ❌ fehlt | **Backlog** |
| Editor-Selektion (Datei/Textstelle) | `UserContext` | ❌ fehlt | **bewusst NICHT** — Jon kuratiert die Aufgabe im Prompt |

## Design

Zwei getrennte Kanäle, sauber nach Lebensdauer getrennt:
- **Dynamisch (pro Delegation neu gelesen)** → in den Provider falten, den `PeonAiService` an
  `JonDelegateTool` gibt (der schon Memory + Projekt liefert). `AGENTS.md` ändert sich pro Projekt
  (`agentsMdService.load(project)` in `setProject`) → gehört hierher, **nicht** in den Static-Context.
- **Statisch (pro Session konstant)** → `setStaticContext(...)` auch auf die zwei Sklaven anwenden.
  Datum/OS/File-Regeln ändern sich innerhalb der Session nicht → KV-Cache-freundlich als System-Message.

`JonDelegateTool` bleibt **agnostisch** — es injiziert, was der Provider liefert; keine Signatur-
Änderung nötig. Die Ork-Sklaven bleiben read-only auf all das (wie beim Memory).

## Inkremente (einzeln baubar + grün)

**Gebaut (Kontext dieses Plans) ✅** — *Gewähltes Projekt in die Sklaven.*
Der Provider in `PeonAiService` (die Lambda, die `JonDelegateTool` bekommt) hängt neben dem Memory eine
`"Selected project:\n" + EclipseUtil.projectInfo(currentProject)`-Zeile an (lazy, read-only). Regression
`JonDelegateToolTest.slaves_getSelectedProjectInjected` (Core-Seam headless). BE grün. NICHT committed.

**Inc 1 — `AGENTS.md` (Basis) in die Sklaven. ✅ GEBAUT, grün, NICHT committed.**
`AgentsMdService.getBaseAgentsMd()` gibt **nur** die Basis-`AGENTS.md` zurück (offensichtlich korrekt:
wiederverwendeter Basis-Zweig aus `get()`, **kein** Anfassen der auf den aktiven Namen gekeyten
`AGENTS-<name>.md`); der `PeonAiService`-Provider hängt sie neben Memory + Projekt an. Regression
`JonDelegateToolTest.slaves_getBaseAgentsMdInjected` (Core-Seam, 9 Tests grün).

**Inc 2 — Static-Context (Datum/OS/File-Regeln) an die Sklaven. ✅ GEBAUT, grün, NICHT committed.**
`PeonAiService.setStaticContext(...)` wendet den Kontext zusätzlich auf `jonDelegateTool.getPlanSlave()`/
`getDevSlave()` an (die Sklaven sind **nicht** in `agentService`). Damit erhält v.a. Da Mek die Regel
„prefer eclipse\* over disk\*; nach disk-Writes `eclipseRefreshProject`/`eclipseBuildProject`".
Getter-Frage geklärt = **ja**: `AiAgent.getStaticContext()` als default (leer), Override in `AbstractAgent`
(spiegelt `getUserContextInformations()`). Regression `PeonAiServiceTest.test_static_context_reaches_jons_slaves`
(real Eclipse, headless-skip wie die anderen Status-Tests).

**Inc 3 — Backlog: `AGENTS-DEV.md` / `AGENTS-PLAN.md` per Sklave.**
Die sklaven-spezifische AGENTS-Datei korrekt **pro Sklave** keyen (Da Mek→`DEV`, Da Thinka→`PLAN`),
statt auf den aktiven Sklaven (Jon→`PO`). Mehr als ein Einzeiler (eigener Resolver-Pfad) → separat.

## Offen
- Nur noch **Inc 3** (Backlog, s.o.): `AGENTS-DEV.md`/`AGENTS-PLAN.md` per Sklave keyen.
- Inc 1 + Inc 2 sind gebaut (grün, NICHT committed). `getStaticContext()`-Getter wurde als default am
  `AiAgent`-Interface + Override in `AbstractAgent` ergänzt.


## Agenten-Namen im System-Prompt + Agent Mode (2026-09-16, Paul, `29a341b`)

**IST/SOLL identisch gebaut.** Jon gibt seinen Sklaven ihren Namen (so wie das Header-Widget sie
zeigt) in den System-Prompt — zentral, nicht pro Prompt-Datei.

- **R-N1 — Name per Fan-out (Choke-Point):** `AiPoAgent.setStaticContext` hängt für jeden Slave
  `ContextItem.newList(context, () -> "Your name is " + s.uiName())` + `AGENT_MODE` an. Replace-
  Semantik: der Bake (`AgentContextComponent.initStaticContext` → Jon → Slaves) ergibt pro Slave
  genau `[Env, Name + AGENT_MODE]`, keine Doppelung.
  - GIVEN ein Slave von Jon WHEN der Static-Context-Bake läuft THEN sein System-Prompt enthält
    `Your name is Da Thinka/Da Mek/Da Dok` + AGENT_MODE, genau einmal.
  - GIVEN ein Slave wurde standalone/headless gebaut (ohne Jon-Bake) WHEN `build()` lief THEN
    trägt er den Namen bereits aus der Fallback-Injektion in `BuildPoAgentComponent` — ohne
    AGENT_MODE (das kommt erst mit dem Fan-out).
- **R-N2 — Agent Mode (Antwort = Chat-Kanal):** `AGENT_MODE` (AiPoAgent.java): „You are in Agent
  Mode and report to Jon. You have someone to ask — use that. Save your state to the plan/task file
  first, then ask him directly, with more questions rather than fewer. Never assume." Slaven
  formulieren offene Fragen **explizit in ihrer Antwort** (das Tool-Result landet bei Jon), statt
  still zu raten; Jon versorgt sie über die Slave-Tools nach. **Sklaven bekommen kein `askUser`**
  (User-Entscheid 2026-09-16: Sklaven fragen nie den User direkt).
  - GIVEN ein Slave kann eine Aufgabe ohne fehlende Information nicht abschließen WHEN er antwortet
    THEN enthält die Antwort die offenen Fragen explizit und zustands­gesichert (state vorher in
    Plan-/Task-File), kein Raten.
- **R-N3 — Prompt-Datei ist nicht die Quelle des Namens:** `dev-build-loop.md` trägt den Namen
  nicht mehr hart im Body (alte Zeile `AGENT-MODE: You name is Da Mek` inkl. Typo entfernt) — der
  Name kommt ausschließlich per Code-Injection (R-N1).
- **Hilfsfunktion:** `ContextItem.newList(...)` (public, Interface) ist die einzige Implementierung;
  die package-private Kopie in `AiPoAgent` ist entfernt.

**Scope (🔒 Paul 2026-09-16):** Nur Jons Team — Top-Level-Peon-Agents (Peon-Dev/Plan/Review
standalone), Custom Agents (Verzeichnisname) und Da Sniffa bleiben bewusst außen vor; Wiederaufnahme
nur auf expliziten Wunsch.

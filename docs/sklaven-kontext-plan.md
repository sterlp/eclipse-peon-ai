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
statt auf den aktiven Agenten (Jon→`PO`). Mehr als ein Einzeiler (eigener Resolver-Pfad) → separat.

## Offen
- Nur noch **Inc 3** (Backlog, s.o.): `AGENTS-DEV.md`/`AGENTS-PLAN.md` per Sklave keyen.
- Inc 1 + Inc 2 sind gebaut (grün, NICHT committed). `getStaticContext()`-Getter wurde als default am
  `AiAgent`-Interface + Override in `AbstractAgent` ergänzt.

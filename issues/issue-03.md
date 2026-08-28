# Neue Custom Agents nach `refresh()`/`reloadAgents()` bekommen keinen Static Context (Env + Memory)

Status: `✅ done (2026-08-21)` — `initStaticContext()` in `updateConfig()` nach `refresh(dir)` + Callback-Wrapper am `ReloadConfigTool`-Pfad; Regression-Test `test_reloadConfig_rebakesStaticContext_forNewCustomAgents` grün; gemeinsam mit issue-04 gefixt (ADR-0031).

## Evidenz

- `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/PeonAiService.java:180-181` —
  `initStaticContext()` wird **einmalig im Konstruktor** aufgerufen.
- `PeonAiService.java:185-195` — `initStaticContext()` setzt `staticContext` (Env +
  `workspaceMemoryTool.get()`) nur auf die Agenten, die `getAgents()` **zu diesem Zeitpunkt**
  liefert.
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/AgentService.java:173-186` —
  `reloadAgentConfig()`: existierende Custom Agents werden wiederverwendet
  (`ca.setPromptFile`, Zeile 182), **neue** werden frisch erzeugt
  (`agent = new CustomAgent(...)`, Zeile 181) — für die wird nirgends
  `setStaticContext(...)` aufgerufen.
- Erreichbar u. a. über `ReloadConfigTool.reloadConfig()`
  (`org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/scaffold/ReloadConfigTool.java:49`
  `agentService.reloadAgents()`) und `PeonAiService.updateConfig()`
  (`PeonAiService.java:219` `agentService.refresh(dir)`).
- Turn-Context ist von dem Problem **nicht** betroffen: `PeonAiService.call()`
  (`PeonAiService.java:585`) setzt den Supplier bei jedem Call neu — nur der
  Static-Context (System-Prompt) fehlt bei neuen Custom Agents.

## Problem

Konkreter Ablauf: Scaffold-Agent legt eine neue `AGENT.md` an und ruft `reloadConfig` auf
(sein eigener Tool-Text: "call after creating/editing artifacts so they become immediately
available") -> `reloadAgents()` erzeugt einen neuen `CustomAgent` -> der Agent ist sofort
auswählbar, sein System-Prompt enthält aber weder die Env-Info noch das Workspace-Memory,
weil `initStaticContext()` nie wieder läuft. Er bleibt so bis zum nächsten Workspace-Start.

## Auswirkung + Schweregrad

**Risiko** — inkonsistente Agenten: alte Custom Agents haben Env+Memory im System-Prompt,
neu geladene nicht; genau die Agenten, die der Scaffold-Agent frisch erstellt und sofort
benutzt werden soll, starten mit dem kargsten Prompt. Fix-Vorschlag für die Review:
Static-Context-Initialisierung an die Agent-Erzeugung koppeln (z. B. Callback in
`AgentService` oder `initStaticContext()` in `updateConfig`/`reloadAgents` nachziehen).

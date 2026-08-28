# `CustomAgent.setPromptFile` invalidiert nicht den `systemMessage`-Cache — nach AGENT.md-Edit + Reload läuft der Agent mit altem System-Prompt weiter

Status: `✅ done (2026-08-21)` — frische Static-Context-Liste bei `updateConfig()`/Reload invalidiert den `systemMessage`-Cache → editierte AGENT.md-Base-Prompts werden beim nächsten Turn live; gemeinsam mit issue-03 gefixt (ADR-0031).

## Evidenz

- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/CustomAgent.java:47-48` —
  `@Getter @Setter private volatile SimplePromptFile promptFile;`: reiner Lombok-Setter,
  keine Cache-Invalidation.
- `CustomAgent.java:107-112` — `getSystemPrompt()` liest `promptFile.getBody()` live —
  würde also den neuen Text liefern, wenn der Cache nicht wäre.
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/agent/AbstractAgent.java:333-349` —
  `buildSystemPrompt()` cached das Ergebnis in `systemMessage`
  (`if (systemMessage != null) return systemMessage;`), Invalidation nur via
  `clear()` (:305), `compressContext()` (:275), `setStaticContext()` (:288).
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/AgentService.java:182` —
  `ca.setPromptFile(agentCfg)` im Reload-Pfad (`reloadAgentConfig`, :173).
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/scaffold/ReloadConfigTool.java:41` —
  Tool-Beschreibung verspricht: "Reload all configuration (agents, skills, commands) — call
  after creating/editing artifacts so they become immediately available."

## Problem

Konkreter Ablauf: User (oder Scaffold-Agent) editiert die `AGENT.md` eines Custom Agents und
ruft `reloadConfig`/`updateConfig` auf -> `reloadAgentConfig` setzt via
`setPromptFile` die neue Prompt-Datei -> der `systemMessage`-Cache des laufenden Agents wird
nicht angerührt -> der Agent antwortet mit dem **alten** Base-System-Prompt, bis jemand
`clear` oder Compact triggert. Vor der Branch war der Cache weniger kritisch (Prompt fast
statisch, ADR-0029); seit Env+Memory im System-Prompt hängen und Reloads explizit
"immediately available" versprechen, ist die Lücke spürbar.

## Auswirkung + Schweregrad

**Risiko** — stiller Behavioral-Drift: Agent verhält sich nach einem Reload weiter nach der
alten Instruktion, ohne Hinweis. Fix-Vorschlag für die Review: `setPromptFile` (oder
`reloadAgentConfig`) ruft eine Invalidation auf — z. B. `setStaticContext(getStaticContext())`
als billiger Reset oder eine dedizierte `invalidateSystemPrompt()`-Methode in `AbstractAgent`.

# ADR-0031: Static Context trägt Env + Workspace-Memory (System-Prompt nicht mehr 100% statisch)

**Status:** Akzeptiert · **Datum:** 2026-08-21 · **Betroffen:** alle Agenten (System-Prompt-Komposition, Rebuild-Triggers)
**Superseded (2026-08-23):** der Workspace-Memory-Anteil wurde durch [ADR-0032](0032-workspace-memory-dynamic-turn-context.md)
abgelöst — Memory lebt nur noch dynamisch im Turn-Context; Env bleibt statisch. Die Rebuild-Triggers
und das File-Context-Format bleiben gültig.

## Kontext

ADR-0029 machte den System-Prompt komplett statisch (nur OS/Date-Regeln); Datei-Kontext wanderte
in die History. Bei der Umarbeitung der Standing Orders (2.6.3-SNAPSHOT, Handover
`/behavior-change-handover.md`) hat der User entschieden: das **Workspace-Memory gehört in den
System-Prompt** — bewusst nicht frisch pro Turn, dafür KV-Cache-stabil. Mid-session-Änderungen
des Memory (`memoryAdd/Replace/Remove`) wirken erst beim nächsten Rebuild — das ist SOLL, kein Bug.

## Entscheidung

> **Ab 2026-08-23 (ADR-0032):** Static Context = nur noch `[Env]`. Der Memory-Snapshot ist aus dem
> System-Prompt geflogen (reine Duplikation neben dem dynamischen Turn-Item). Historischer Stand:
- **Static Context = [Env (`StaticContextItem`), Workspace-Memory-Snapshot]** für alle
  `AgentService`-registrierten Agenten — gebacken in `PeonAiService.initStaticContext()`.
- **Jons Slaven** (nicht in `AgentService` registriert) bekommen dieselbe Liste via
  `AiPoAgent.setStaticContext`-Override (propgt an `slaves`) — env-only Fallback-Content aus
  `BuildPoAgentComponent` wird im vollverdrahteten Betrieb immer überstimmt.
- **Rebuild-Triggers** (System-Prompt neu gebaut): `clear()`, `compressContext()`,
  `setStaticContext()`, jedes `updateConfig()` (nach `agentService.refresh`) und der
  `ReloadConfigTool`-Pfad (Callback-Wrapper im `PeonAiService`-Konstruktor re-baked nach
  `reloadAgents()`).
- **Datei-Kontext bleibt in der Chat History** (ADR-0029 in diesem Punkt unverändert).
- **File-Context-Format:** Render mit Linenumbers (`FileLines.format`); `dedupKey` =
  `<pfad>:` + LineSeparator + ` content with line numbers:` — ersetzt den ADR-0029-Header
  `<pfad>:\n---\n`. Dedup-Prinzip unverändert: der Key ist Präfix der injizierten Message.

## Konsequenzen

> **Ab ADR-0032:** Memory doppelt im Kontext entfällt; Re-Bake betrifft nur noch Env. Historisch:
- System-Prompt ist nicht mehr 100% statisch (Memory-Snapshot) — der "komplett statisch"-Teil von
  ADR-0029 + context-architecture.md ist in diesem Punkt superseded.
- Jeder Config-/Reload-Change (Model, Think, `reloadConfig`) re-baked Env+Memory in alle
  System-Prompts → eine Cache-Invalidation pro Change (akzeptiert, seltener Event). Neben-Effekt:
  editierte AGENT.md-Base-Prompts werden nach Reload live (issue-04).
- Neue Custom Agents nach `reloadAgents()` bekommen Env+Memory (vorher: nie, issue-03).
- Review-Nachweis: `test_staticContext_isEnvPlusMemory`,
  `test_slave_systemMessage_contains_workspaceMemory`,
  `test_reloadConfig_rebakesStaticContext_forNewCustomAgents`.

## Verwandt

- [ADR-0029](0029-file-context-in-history.md) — History-Prinzip bleibt gültig, "100% statisch" superseded
- [Context Architecture](../../org.sterl.llmpeon.core/docs/context-architecture.md) — Static/Dynamic-Kategorisierung
- [Standing Orders Design](../../org.sterl.llmpeon.core/docs/standing-orders-design.md) — historisch (superseded 2026-08-21)

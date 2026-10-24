# Configuration Overview (Landkarte)

Dünne Karte der Konfigurations-Oberfläche — **keine Regeln hier**, nur wo was steht. Details in
den verlinkten Stories.

## Wo steht was

| Was | Wo | Detail-Doc |
|---|---|---|
| LLM-Verbindung, per-Agent-Slots (PO → Plan → Dev → Search → Compact), Temperature, extra body | Eclipse Preferences — Basic- + Advanced-Page | [advanced-configuration.md](../org.sterl.llmpeon/docs/advanced-configuration.md) |
| Modell-Listen (lazy fetch, Cache pro Identität, Refresh-Button) | Eclipse Preferences + RAM-Cache | [model-loading.md](../org.sterl.llmpeon/docs/model-loading.md) |
| Prompt-Caching | per-Agent JSON extra body (auch `AGENT.md`-Frontmatter) | [caching.md](../org.sterl.llmpeon.core/docs/caching.md) |
| Think-Support | per-Agent, Provider-Mapping-Files | [per-agent-think.md](../org.sterl.llmpeon.core/docs/per-agent-think.md) |
| Custom Agents | `~/.peon/agents/<name>/AGENT.md` (Frontmatter + Body) | [custom-agents-design.md](../org.sterl.llmpeon.core/docs/custom-agents-design.md) |
| Skills & Commands | Skills: `~/.agents/skills/**` (Default + Empfehlung), `~/.peon/skills/**` (deprecated, schreibbarer Support), `<project>/.agents/skills` (Override) · Commands: `~/.peon/commands/**` — Details siehe Story | [scaffold-agent.md](../org.sterl.llmpeon.core/docs/scaffold-agent.md) · [project-skills.md](../org.sterl.llmpeon.core/docs/project-skills.md) |
| Shared-Config-Verzeichnis + State-Trennung | `~/.peon` = config-only, History = workspace-scoped | [peon-config-directory.md](../org.sterl.llmpeon.core/docs/peon-config-directory.md) |
| Workspace Memory (memory*-Tools) | Eclipse Instance-Preferences, user-global | [context-architecture.md](../org.sterl.llmpeon.core/docs/context-architecture.md) |
| Agent-History | `.metadata/.plugins/org.sterl.llmpeon/state/<agent>-history.jsonl` (kein State in `~/.peon`; Altbestand wird nicht übernommen) | [peon-config-directory.md](../org.sterl.llmpeon.core/docs/peon-config-directory.md) |

**Basis vs. Override (2026-09-12):** Die URL auf der Basic-Page ist die Basis für **alle** Agenten
ohne eigenen Override — der Dev-Agent trägt standardmäßig **keinen eigenen** URL und erbt die
Base-URL; das leere URL-Feld in der Advanced-View zeigt nur den eigenen Override (leer = erben).
Modell-Listen-**Refresh** nutzt den gespeicherten Stand: erst **Apply**, dann Refresh.
Details: [advanced-configuration.md](../org.sterl.llmpeon/docs/advanced-configuration.md) · [model-loading.md](../org.sterl.llmpeon/docs/model-loading.md).

## Aufnahme-Regel

Neues Config-Feature → eigene Story, hier eine Zeile eintragen. Diese Seite bleibt eine
Landkarte: ein Ziel-Satz je Eintrag, niemals Regel-Duplikate.

# Configuration Overview (Landkarte)

Dünne Karte der Konfigurations-Oberfläche — **keine Regeln hier**, nur wo was steht. Details in
den verlinkten Stories.

## Wo steht was

| Was | Wo | Detail-Doc |
|---|---|---|
| LLM-Verbindung, per-Agent-Slots (PO → Plan → Dev → Search → Compact), Temperature, extra body | Eclipse Preferences — Basic- + Advanced-Page | [advanced-configuration.md](advanced-configuration.md) |
| Modell-Listen (lazy fetch, Cache pro Identität, Refresh-Button) | Eclipse Preferences + RAM-Cache | [model-loading.md](model-loading.md) |
| Prompt-Caching | per-Agent JSON extra body (auch `AGENT.md`-Frontmatter) | [caching.md](caching.md) |
| Think-Support | per-Agent, Provider-Mapping-Files | [per-agent-think.md](per-agent-think.md) |
| Custom Agents | `~/.peon/agents/<name>/AGENT.md` (Frontmatter + Body) | [custom-agents-design.md](custom-agents-design.md) |
| Skills & Commands | `~/.peon/skills/**`, `~/.peon/commands/**` | [scaffold-agent.md](scaffold-agent.md) |
| Shared-Config-Verzeichnis + State-Trennung | `~/.peon` = config-only, History = workspace-scoped | [peon-config-directory.md](peon-config-directory.md) |
| Workspace Memory (memory*-Tools) | Eclipse Instance-Preferences, user-global | [context-architecture.md](context-architecture.md) |
| Agent-History | `.metadata/.plugins/org.sterl.llmpeon/state/<agent>-history.jsonl` (Umzug von `~/.peon/state`, One-Shot-Migration) | [peon-config-directory.md](peon-config-directory.md) |

## Aufnahme-Regel

Neues Config-Feature → eigene Story, hier eine Zeile eintragen. Diese Seite bleibt eine
Landkarte: ein Ziel-Satz je Eintrag, niemals Regel-Duplikate.

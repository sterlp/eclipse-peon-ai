# Advanced Configuration

## Preference Page Split

AI Peon configuration is split into two preference pages:

| Page | Purpose |
|------|--------|
| **AI Peon Configuration** | Basic provider, model, URL settings for everyday use |
| **AI Peon Advanced** | Per-agent models, temperatures, debug mode, query/header parameters |

This separation keeps the default configuration simple while providing power users access to fine-grained controls.

## Per-Agent Model Resolution via ChatRequest

### Architecture Change (Issue #82)

**Previous approach**: All agents shared a single `ConfiguredChatModel`. Changing any model flushed the KV cache, and per-agent settings were ignored.

**Current approach**: Each agent resolves its own model name from `LlmConfig` and sets it on `ChatRequest.modelName()` before calling the tool loop. LangChain4j applies this override when building the request to the provider.

### Data Flow

```
AiPlannerService.resolveAgentModel()
  → returns configuredModel.getConfig().getPlanModel()
  → ToolLoopRequest.builder().modelName(planModel)
  → AbstractChatService.call() passes modelName to ToolLoopRequest
  → ToolService builds ChatRequest with modelName set
  → Provider receives request with agent-specific model
```

### Configuration Keys

| Key | Agent | Purpose |
|-----|-------|---------|
| `PREF_MODEL` | Developer (base) | Code generation — always uses base model |
| `PREF_PLAN_MODEL` | Planner | Task planning and strategy |
| `PREF_SEARCH_MODEL` | Search | Context retrieval and information lookup |
| `PREF_COMPACT_MODEL` | CompactSessionTool | Conversation compression for context management |

### Model Resolution Rules

- **Developer agent**: Always uses the base model (`PREF_MODEL`) — no separate devModel configuration
- **Other agents**: Use their configured per-agent model if set; otherwise provider default applies
- **No fallback chain**: Per-agent models do not fall back to `PREF_MODEL`

### Why ChatRequest.modelName() Instead of Separate ConfiguredChatModel?

1. **Single cache**: One `StreamingChatModel` instance with KV cache preserved across agent switches
2. **No synchronization**: No need to keep multiple model instances in sync on config change
3. **Native support**: LangChain4j supports per-request model override directly
4. **Lower overhead**: Avoids building and maintaining multiple `ConfiguredChatModel` wrappers

## Agent-Specific Config Umbau (SOLL, 2026-08-21) — ✅ Core (2a) + Config-UI (2b) gebaut (2026-08-30); 2c-Teile (Custom-Agent-yml, Homepage) ❌ offen

**SOLL:** Alles konfigurierbar wird **agent-spezifisch** — jeder Agent mit Modell-Slot
(base/plan/search/compact/PO + Custom Agents) trägt seine eigenen Einstellungen: Modell
(besteht, Issue #82), Temperature, Think, **JSON extra body** (→ [Prompt Caching](caching.md) —
Caching läuft als per-agent-JSON, nicht als Provider-Hardcode).

Dazu gehören:
- Advanced Settings-Eingabe **pro Agent** (inkl. Custom Agents) mit UI-Beispielen unter dem
  Input (GPT-/Claude-Cache-Snippets).
- Geltung für jeden Agenten, bei dem ein Modell eingestellt werden kann.
- Zusammen mit dem Caching-SOLL dokumentiert — **Umsetzung Backlog** (nach der Issue-Runde).

**SOLL-Ergänzung (2026-08-28, User) — ✅ gebaut (2b, 2026-08-30: Config-Seite per-agent, Modell-Liste pro Identität, Chat-UI-Räumung); 2c-Teile ❌ offen.
Mechanik: [ADR-0034](adr/0034-connection-cache-by-identity.md).**
- **Model-Config pro Agent:** ein „configured model" = URL, Key, Modell, Think, extra JSON body
  (KV-Cache-ID & weitere Body-Params). Verbindungen (Model-Instanzen) werden per **Hash der
  Verbindungs-Identität** (Provider + URL + Key [+ body, wenn nur Build-time setzbar]) gecacht;
  gleiche Identität → wiederverwendete Verbindung (Default-Verbindung, solange URL/Key
  gleich/nicht gesetzt). Temp/Think/Body laufen per Request.
- **JSON-Widget in der Config-Seite:** pro Agent ein Widget, das die Config editiert;
  Speicherung mit **Agent-Prefix** (Key-Prefix je Agent) — keine zentrale Modell-Registry.
- **Modell-Dropdown + Think: aus der Chat-UI raus** (2026-08-28, User: „nur wenn es einfacher
  wird" — es wird einfacher: Config-Seite = einzige Quelle, kein Dual-Edit, weniger Chat-UI-
  State; Known-Issue-Bug schrumpft auf Validierung beim Öffnen der Config). Die Config-Seite
  trägt pro Agent: Modell-Dropdown, Think, JSON-Body-Widget.
- **Modell-Liste pro Agent (2026-08-28, User):** das Dropdown eines Agenten bezieht seine
  Liste aus dessen **effektiver** Verbindung (eigene Provider+URL+Key, sonst Base-Config) —
  Umschalten auf einen Agenten mit anderer URL zieht also eine andere Liste. Liste **einmalig**
  pro Identität (gleicher Hash wie Connection-Cache): **Cache on success**; Fetch-Fehler →
  konfiguriertes Modell bleibt gesetzt (heutiger Fallback); **kein Refetch** beim
  Zurückwechseln. **Refresh-Button im Dropdown** (2026-08-28, User): manueller Refetch der
  Liste der aktuellen Identität (Fehler → alter Cache bleibt). Identitätswechsel
  (URL/Key/Provider) → neuer Fetch. Konfiguriertes Modell
  nicht in der Liste → **bleibt gesetzt** (kein Auto-Switch auf erstes Modell — bewusste
  Abweichung von B2 in [model-loading.md](model-loading.md)).
- **Core-Fundament ✅ (Zyklus 2a, 2026-08-28):** `AgentConfig`/`LlmConfig` +`extraBody`;
  `EffectiveConnection` (Agent-URL/Key, sonst Base; Provider bleibt Base-Ebene);
  Connection-Cache in `ConfiguredChatModel` pro `ConnectionIdentity` (Provider+URL+Key, +Body
  nur Build-time-Provider); per-request Body-Merge (provider-Entries zuerst, **User-Body
  gewinnt**, Reserved-Keys `model`/`messages`/`tools` gestrichen); invalides JSON → warn +
  ignoriert. Core 483/483 grün, Branch `new-config` (Commits `inc-1`…`inc-8`, NICHT gemerged).
- **Core/UI-Trennung (2026-08-28, User):** die Konfig-Domäne (Model-Config-Record, Speicherung,
  effektive Auflösung, Verbindungs-Identität) lebt in **core**; die Eclipse-Config-Seite ist
  reine View/Editor darüber — „sauber trennen, nah halten“ (das Plugin trägt keine
  Config-Logik).
- **Sauberer Rebuild bei Update (2026-08-28, User):** die effektive Config wird bei jedem Load
  **komplett** aus gespeicherten Werten + Defaults neu aufgebaut; unbekannte/entfernte Keys
  werden ignoriert (keine Migrations-Kette, kein Stale-State).
- **Custom Agents: komplette Config via AGENT.md-Frontmatter (yml) (2026-08-28, User):** alles
  Einstellbare (URL, API Key, Modell, Think, JSON extra body) geht auch pro Custom Agent im
  Frontmatter — gleicher Record, gleiche Auflösung; **Homepage-Doku** dafür ist SOLL (wird mit
  dem Feature umgesetzt).
- **Known Issue (Bug) — ✅ gelöst (2b, 2026-08-30):** war: URL-Wechsel in der Base-Config macht die Modell-Auswahl der **anderen**
  Agenten ungültig/leer (nur der selektierte Agent wird aktualisiert); heute via Wiederauswahl
  zu reparieren.

## First-Launch Directory Resolution

On first launch, AI Peon resolves skills and commands directories:

1. Check if `~/.claude/skills` exists → use it (Claude Desktop compatibility)
2. Otherwise create and use `~/.llmpeon/skills`

Same logic applies to commands directory (`~/.claude/commands` → `~/.llmpeon/commands`).

This one-time resolution ensures deterministic behavior without filesystem I/O on every config load.

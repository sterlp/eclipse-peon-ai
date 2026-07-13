# Advanced Configuration

## Preference Page Split

AI Peon configuration is split into two preference pages:

The preference category is **AI Peon**, split into pages:

| Page | Purpose |
|------|--------|
| **Peon Configuration** | Provider, model, URL, config directory, token/auto-compact settings for everyday use |
| **Peon Advanced Configuration** | Per-agent models, temperatures, debug mode, query/header parameters |
| **Peon MCP Configuration** | MCP server definitions |
| **Peon Voice Configuration** | Speech-to-text endpoint |

This separation keeps the default configuration simple while providing power users access to fine-grained controls.

## Per-Agent Model Resolution via ChatRequest

### Architecture Change (Issue #82)

**Previous approach**: All agents shared a single `ConfiguredChatModel`. Changing any model flushed the KV cache, and per-agent settings were ignored.

**Current approach**: Each agent resolves its own model name from `LlmConfig` and sets it on `ChatRequest.modelName()` before calling the tool loop. LangChain4j applies this override when building the request to the provider.

### Data Flow

```
AiAgent.getAgentModelName()          // e.g. AiPlanAgent → config.getPlanModel()
  → AbstractAgent.call() sets ToolLoopRequest.builder().modelName(...)
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

## Config Directory

There is a single **Config directory** preference (`PREF_CONFIG_DIRECTORY = llm.configDirectory`),
defaulting to `~/.peon` (`LlmPreferenceInitializer.PEON_HOME`). Skills, commands and agents are
loaded from its subfolders:

| Subfolder | Constant | Contents |
|-----------|----------|----------|
| `skill`   | `LlmConfig.SKILL_DIRECTORY`   | `SKILL.md` skill directories |
| `command` | `LlmConfig.COMMAND_DIRECTORY` | `*.md` slash commands |
| `agent`   | `LlmConfig.AGENT_DIRECTORY`   | `AGENT.md` custom-agent directories |

The subfolder names are singular. There is no `~/.claude` fallback and no legacy `~/.llmpeon` /
`~/.aipeon` resolution — those were removed.

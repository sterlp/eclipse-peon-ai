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

## First-Launch Directory Resolution

On first launch, AI Peon resolves skills and commands directories:

1. Check if `~/.claude/skills` exists → use it (Claude Desktop compatibility)
2. Otherwise create and use `~/.llmpeon/skills`

Same logic applies to commands directory (`~/.claude/commands` → `~/.llmpeon/commands`).

This one-time resolution ensures deterministic behavior without filesystem I/O on every config load.

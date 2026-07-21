# ADR 0001 — Think is resolved to a per-agent String, not a global enum

**Status:** Accepted

**Context:** Providers define "think" differently: Ollama/Anthropic are boolean-ish (`think`,
thinking type), OpenAI takes a string effort (`reasoning.effort=high`), LM Studio a custom
`reasoning`. A single global on/off value cannot express "plan with GPT at `high` while dev sends
nothing to a DeepSeek gateway" — the shared value reaches both providers and the gateway rejects it.

**Decision:** Each agent resolves its **own** effective think **String** per request. The String
carries either a boolean-ish token (`true`/`false`/`off`), a concrete level (`high`/`medium`/…), or
`""` (omit). One resolver (`ThinkResolver`) plus one per-provider translation in `AiProvider` serve
every provider; the request parameters are built per agent from its `AgentConfig`.

**Consequences:** No global think enum. Adding a provider means adding one translation branch, not a
new config axis. The value is computed fresh for each request, so mixed-provider setups never share a
think value (no inheritance — see the story's rules in [per-agent-think.md](../per-agent-think.md)).

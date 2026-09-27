# ADR 0001 — Think is resolved to a per-agent String

**Status** · Accepted

**Context:** Providers define thinking/reasoning differently: Ollama has `think`, OpenAI takes `reasoning.effort`, Anthropic uses thinking type, and LM Studio accepts a custom `reasoning` property. A single global on/off value cannot express “plan with GPT at `high` while dev sends nothing to a non-reasoning gateway”.

**Decision:** Each agent has a support boolean plus on/off strings (`think_supported`, `think_on_string`, `think_off_string`). `ThinkResolver` resolves those into one provider-agnostic string per request; `AiProvider` maps that string to provider-specific request parameters.

> **2026-09-27 (Issue #149):** der support-Boolean-Teil dieser Entscheidung ist **superseded** durch [ADR-0059](0059-think-dropdown-empty-unset.md) — es gibt nur noch einen Think-Level-String pro Agent, leer = unset. Der String-Kern (Think resolved to per-agent String, Provider-Mapping) bleibt gültig.

**Consequences:** No global think enum. Adding a provider means adding one translation branch, not a new config axis. The value is computed fresh for each request, so mixed-provider setups never share a think value (no inheritance — see [per-agent-think.md](../per-agent-think.md)).

# ADR 0003 — `think_send` is independent of the think toggle (and stays global)

**Status:** Accepted

**Context:** Some models (Qwen, Mistral, DeepSeek) emit reasoning even when we send no think
attribute, and require that reasoning to be **returned** and **resent** on the next turn or the call
errors (e.g. Gemini's `thought_signature`). This "show + resend" concern is orthogonal to whether we
ask the model to think. Separately, the bundled langchain4j exposes `returnThinking`/`sendThinking`
only on the **model builder** (build time) — there is no per-request setter.

**Decision:**
- `returnThinking` (parse + show) = `think_supported` **OR** `think_send`.
- `sendThinking` (resend prior thinking) = `think_send`.
- Because the switches are build-time, `think_send` is a **single global** preference
  (`PREF_SEND_THINKING_ENABLED`), driven by the Dev/global config.

**TODO:** Remove `think_enabled` backward compat (kept in `CustomAgent.THINK_ENABLED` for reading old AGENT.md files) in a future major version. Auto-migration happens on the first write operation (e.g. model change, think toggle) — the file is not modified on load, only when a write occurs.

**Consequences:** A reasoning model may think and have its thinking shown even when the attribute is
omitted. Per-agent `think_send` is **not** wired per request (langchain4j limit); the custom-agent
`think_send` frontmatter key is parsed but **reserved** for a future per-request wiring — see the
Known gaps in [per-agent-think.md](../per-agent-think.md).

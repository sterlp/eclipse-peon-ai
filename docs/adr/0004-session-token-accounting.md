# ADR 0004 — Session token accounting at the StreamingBridge choke point

**Status:** Accepted

**Context:** [Session Token Usage](../token-usage.md) needs a cumulative, cross-agent, never-reset
token counter that includes the main tool loop, the search sub-agent and every compaction. These are
three different call paths, but they all pass through one method:
`StreamingBridge.onCompleteResponse(ChatResponse)` (the search sub-agent reuses
`ToolService.executeLoop`; compaction goes `AiCompressorAgent → ConfiguredChatModel.callBlocking →
new StreamingBridge()`). The bridge already holds the `AiMonitor`, and `AIChatView` *is* that monitor
and is session-scoped (one per view). The existing per-agent `ThreadSafeMemory.totalTokenUsed` is a
*context-size* value that resets on `clear()`/compaction — unsuitable for a session total.

**Decision:**
- Add `AiMonitor.onTokenUsage(TokenUsage)` (no-op default) and fire it from
  `StreamingBridge.onCompleteResponse` — the single accumulation trigger for the whole app.
- Only fire when the provider actually returned usage (`ChatMessageUtil.tokenUsage(response)`
  returns the response or metadata `TokenUsage`, else `null`). **No `chars/4` estimate** — a
  missing usage leaves the totals unchanged.
- `AIChatView` holds the session total in a `TokenStats` accumulator (`sent`/`received`); because it
  is the shared monitor, the total is inherently cross-agent and is never reset by Clear/Compact/agent
  switch. A new `AIChatView` (view reopen) starts fresh.
- `TokenStats` is a standalone value class so R5 (per-agent stats) can reuse it later.

**Consequences:** `sent` grows every tool-loop round because the full prompt is re-sent each round —
this is intentional (true API spend, matching `ai-schulung`). Providers that never return usage show
no readout. Because accumulation is decoupled from `ThreadSafeMemory`, the session total and the
Compact button's context size can legitimately diverge (one climbs, the other resets) — the header
shows only `↑/↓`, context size stays on the Compact button.

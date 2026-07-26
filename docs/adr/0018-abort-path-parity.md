# 0018 — Abort Path Parity

**Status:** Accepted · **Date:** 2026-07-25

## Context
Auto-chaining logic must distinguish genuine success (`response != null`) from swallowed error paths (rate limits, cancellations returning `null`). Previously the UI handled this distinction in `handleDoneChatResponse`, but errors like `RateLimitException` could be silently swallowed by `handleChatException`, making it impossible to tell if a `null` response meant "aborted" or "failed".

## Decision
1. Distinguish success from abort explicitly: chaining only triggers on true success (`lastResponse != null && !monitor.isCanceled()`).
2. On any abort (cancel, error, rate limit), drain remaining queue into agent memory *inside* the core loop before returning — guaranteeing policy consistency regardless of caller.
3. The UI only handles drain confirmation for exceptions/cancellations that propagate up; internal chaining drains are handled by the agent's `handleAbortAndDrain()`.

## Consequences
- Never burns quota on failure chains (rate limits don't trigger follow-ups).
- Queue survives aborts safely in memory for the user's next request.
- TOOL message confirms drain count to the user.
- Simplified UI: no branching between success/abort paths — only unlock + optional drain confirmation.

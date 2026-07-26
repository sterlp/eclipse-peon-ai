# 0017 — Atomic UI Job Chaining

**Status:** Accepted · **Date:** 2026-07-25

## Context
Chaining background jobs via UI callbacks requires unlocking the previous job and locking the next one. If these are separate operations, a race window exists where user input can interleave between chained jobs — causing visual flicker (Stop button disappears then reappears) and potential cross-agent contamination if the user switches agents mid-chain.

## Decision
Move queue ownership and chaining into the core agent domain:
- The agent owns `UserMessageQueue` internally, not the UI.
- `call()` loops internally over queued messages until empty or abort — no external re-scheduling needed.
- A centralized `isWorking()` flag replaces UI-side state tracking (`actionsBar.isWorking()` → `active.isWorking()`).

This eliminates the need for "unlock previous + lock next" atomicity entirely, because chaining happens within a single job invocation. The UI simply locks on job start and unlocks on job end — no flicker, no race window.

## Consequences
- Stop button remains stable during chained turns (no unlock/re-lock gaps).
- Agent-switching mid-flight is safe: each agent processes only its own queue.
- Simpler UI code: no `submitFollowUpJob()`, no manual lock sequencing, no `isChaining` volatile flag.

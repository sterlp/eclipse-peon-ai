# Session Token Usage

## Goal

Show the **cumulative token spend of the whole session** in the top header, left of the tools
(hammer) icon, as `↑ sent  ↓ received`. It counts real LLM usage from *every* model call — the main
tool loop, the search sub-agent and every compaction — so the number reflects true API spend. It is
cross-agent and **never resets** while the chat view is open. Mirrors the `ai-schulung` project's
`↑/↓` readout.

Cross-component extension of [Interaction Design](interaction-design.md) (the header lives in that
view). Reuses `StringUtil.toK` for the `k` formatting used by the Compact button.

## Business rules

### R1 — Accumulate real usage per response ✅
Every LLM response adds its usage to the session totals: `sent += inputTokenCount`,
`received += outputTokenCount`. Only real provider usage counts — no `chars/4` estimate.

- **GIVEN** a turn with several tool-loop iterations **WHEN** each response returns usage
  **THEN** the totals grow by the summed input/output across iterations
  → `tokenStatsAccumulatesAcrossIterations`.
- **GIVEN** a provider returns no token usage **WHEN** the response completes
  **THEN** the totals are unchanged → `tokenStatsIgnoresMissingUsage`.

### R2 — Include sub-agent and compaction usage ✅
Because accumulation happens at the single `StreamingBridge` choke point (see
[ADR 0004](adr/0004-session-token-accounting.md)), sub-agent and compaction calls are counted too.

- **GIVEN** the search sub-agent runs **WHEN** it calls the model **THEN** the session totals grow.
- **GIVEN** compaction runs (auto / `compactSession` tool / Compact button) **WHEN** the compressor
  calls the model **THEN** the session totals grow.

### R3 — Never reset within a session ✅
The totals persist for the life of the chat view.

- **GIVEN** totals > 0 **WHEN** the user clicks Clear, runs Compact, or switches the agent
  **THEN** the totals are unchanged.
- A fresh chat view (close/reopen) starts at zero.

### R4 — Header display ✅
- **GIVEN** `sent == 0` and `received == 0` (fresh session) **WHEN** the header renders
  **THEN** no token readout is shown.
- **GIVEN** `sent > 0` or `received > 0` **WHEN** a response arrives **THEN** the header shows
  `↑ <sent>  ↓ <received>` left of the hammer icon, updated live. `received` = output/generated
  tokens.

### R5 — Per-agent stats ❌
Each `AiAgent` owns its own `TokenStats` (its own `sent`/`received`), so a future UI can break spend
down per agent. Foundation for [detached agents](#r7--detached-agents-).

### R6 — Search-agent usage line ❌
When the search sub-agent returns, the status shows how much *it* used: `sent / received /
result-size`.

### R7 — Detached agents ❌
The session totals keep updating while the user switches to a plan/review agent during a running dev
agent (agent dropdown no longer locked). Out of scope now; the design stays compatible.

## Non-goals

- **Current context size is not duplicated in the header.** It stays on the **Compact button**
  (`used/max`, colored at 70 %/88 %). Context size is per-agent and resets on Clear/Compact, so it is
  deliberately kept separate from the never-reset session `↑/↓`.

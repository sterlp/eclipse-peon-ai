# Sub-agent tool timing

## Goal

The tools that run a **sub-agent** (a nested LLM loop) can take a while, and the chat only shows a
short progress line. That line should say **how long the sub-agent worked**, so the user sees where the
time went — e.g. `Da Thinka done. (3s)`.

## Scope — the three sub-agent tools

Exactly the tools that dispatch a nested agent get the timing; ordinary tools do not.

| Tool | Sub-agent | UI display name | Done line |
| --- | --- | --- | --- |
| `JonDelegateTool` (`talkPlan` / `planWithPlanAgent` / `askDev` / `buildWithDev`) | Jon's Da Thinka / Da Mek | **Da Thinka** / **Da Mek** | `Da Thinka done. (3s)` / `Da Mek done. (12s)` |
| `SearchAgentTool` (`searchAgent`) | the search sub-agent | **Da Sniffa** | `Da Sniffa done. (12s)` |
| `CompactSessionTool` (`compactSession`) | the `AiCompressorAgent` | **Da Scribe** | `Da Scribe done. (1m 5s)` |

`CompactSessionTool` has **no** progress line today (it only returns the summary). Timing adds a new
**done line** for it, so a compaction is no longer invisible.

### Display names are UI-only

The Ork-flavoured names live **only** in the `onTool` progress lines, for a nicer chat. They are **not**
tool names and **not** part of any LLM-facing text: the `@Tool` names stay functional
(`talkPlan`/`planWithPlanAgent`/`askDev`/`buildWithDev`/`searchAgent`/`compactSession`), and the fallback/error results the model
reads keep the plain role names (`Da Thinka returned no result`, `Search agent failed …`). So the
names can be re-flavoured freely without touching model behaviour — and the timing tests assert the
`done. (Ns)` suffix, not the name.

## Rules

### SAT1: Measure the sub-agent's wall-clock ✅

Each tool measures **wall-clock** time around the nested call only — `slave.call(...)`,
`toolService.executeLoop(...)`, `AiCompressorAgent.call(...)` respectively — not the whole tool method.
Measured with a monotonic clock (`System.nanoTime`), so it is unaffected by wall-clock adjustments.

**BDD:**
```
GIVEN a sub-agent tool runs its nested agent
WHEN the nested call returns
THEN the elapsed wall-clock of that call is measured (monotonic clock)
```

### SAT2: Append the elapsed time to the done line ✅

The elapsed time is appended to the tool's **done** progress line as ` (<elapsed>)`.

- `JonDelegateTool`: `"<display name> done. (3s)"` (display name = `Da Thinka` / `Da Mek`).
- `SearchAgentTool`: `"Da Sniffa done. (12s)"` — timing is reported only on the **success** path; a
  failed search still reports via `onProblem` (unchanged), no timing.
- `CompactSessionTool`: a new `"Da Scribe done. (1m 5s)"` line.

**BDD:**
```
GIVEN Jon's Plan slave finishes after ~3 seconds
THEN the progress line reads "Da Thinka done. (3s)"

GIVEN the search agent finishes after ~12 seconds
THEN the progress line reads "Da Sniffa done. (12s)"

GIVEN a compaction finishes after ~65 seconds
THEN a "Da Scribe done. (1m 5s)" line is emitted (compaction is no longer invisible)

GIVEN the search agent fails
THEN it reports the problem as before, with no timing line
```

### SAT3: Format — whole seconds, minutes when long ✅

One shared formatter turns an elapsed duration into a compact human string; all three tools reuse it
(so the format never drifts) and it is unit-tested directly.

- Under a minute: whole **seconds**, e.g. `(3s)`, `(12s)`.
- Sub-second: `(0s)` (never blank, never `ms`).
- A minute or more: **minutes + seconds**, e.g. `(1m 5s)`, `(2m 0s)`.
- Seconds are **truncated** to whole numbers (not rounded up), so a 3.9 s run shows `(3s)`.

**BDD:**
```
GIVEN an elapsed duration
WHEN it is formatted
THEN  0.4s -> "0s"
AND   3.9s -> "3s"
AND   12s  -> "12s"
AND   65s  -> "1m 5s"
AND   120s -> "2m 0s"
```

## Notes

- The formatter lives with the other shared string helpers and is reused by all three tools — the
  timing rule is one behaviour in one place, three call sites.
- Only the **done** line carries the time; the **start** line is unchanged.

# Sub-agent tool timing

## Goal

The tools that run a **sub-agent** (a nested LLM loop) can take a while, and the chat only shows a
short progress line. That line should say **how long the sub-agent worked**, so the user sees where the
time went — e.g. `Plan done. (3s)`.

## Scope — the three sub-agent tools

Exactly the tools that dispatch a nested agent get the timing; ordinary tools do not.

| Tool | Sub-agent | UI display name | Done line |
| --- | --- | --- | --- |
| `PoDelegateTool` (`talkPlan` / `planWithPlanAgent` / `askDev` / `buildWithDev`) | Jon's Plan / Dev | **Plan** / **Dev** | `Plan done. (3s)` / `Dev done. (12s)` |
| `SearchAgentTool` (`searchAgent`) | the search sub-agent | **Search** | `Search done. (12s)` |
| `CompactSessionTool` (`compactSession`) | the `AiCompressorAgent` | **Compact** | seit R-CT-8 (2026-10-24) entfallen: die alte `🗜 Compact done for <agent>. (1m 5s) (N chars)`-Zeile ist durch die uniforme Compact-End-Zeile `🗜 <Agent> compacted: … (Ns, HH:mm) (N chars)` ersetzt ([compact-lock.md](compact-lock.md) R-CT-8) — das Tool emittiert selbst nichts mehr |

Compaction is no longer invisible since R-CT-8 (2026-10-24): the uniform Compact-End-Zeile
([compact-lock.md](compact-lock.md)) reports start, end and duration — `CompactSessionTool`
emits nothing itself.

### Display names are UI-only

The role names are the agents' **display names** (ADR-0079) and appear in the `onTool` progress
lines for a readable chat. They are **not** tool names: the `@Tool` names stay functional
(`talkPlan`/`planWithPlanAgent`/`askDev`/`buildWithDev`/`searchAgent`/`compactSession`), and the fallback/error results the model
reads keep the plain role names (`Plan returned no result`, `Search agent failed …`). So the
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

- `PoDelegateTool`: `"<display name> done. (3s)"` (display name = `Plan` / `Dev`).
- `SearchAgentTool`: `"Search done. (12s)"` — timing is reported only on the **success** path; a
  failed search still reports via `onProblem` (unchanged), no timing.
- `CompactSessionTool`: **keine eigene Done-Zeile mehr** — die uniforme Compact-End-Zeile
  (R-CT-8, [compact-lock.md](compact-lock.md)) übernimmt Dauer, Uhrzeit, Ergebnisgröße und
  Agent-Namen; 🗜-Präfix bleibt über die Start-/End-Zeilen erhalten ([icons.md](../../org.sterl.llmpeon/docs/icons.md)).

**BDD:**
```
GIVEN Jon's Plan slave finishes after ~3 seconds
THEN the progress line reads "Plan done. (3s)"

GIVEN the search agent finishes after ~12 seconds
THEN the progress line reads "Search done. (12s)"

GIVEN a compaction finishes after ~65 seconds
THEN the uniform compact end line "🗜 <agent> compacted: … (1m 5s, HH:mm) (N chars)" is emitted
     (R-CT-8 — compaction is no longer invisible; no separate "Compact done" line)

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

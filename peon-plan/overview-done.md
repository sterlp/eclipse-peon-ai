# Implementation Plan: Standing Orders Docs Alignment (BDD 4: Plan Handover)

**Linked docs plan:** `peon-plan/overview-done-2026-07-26-21-52.md`

## Context

One BDD use case remains ❌: **Plan Handover** — when plan→dev handoff occurs, the plan file path + handover instruction must be added to standing orders so they survive compaction on the dev agent's first turn. Currently `onHandoff()` adds plan content as memory payload but *not* as standing order context.

## Design Decision

Add transient `_handoffLine` field to `PeonAiService`. Set it in `onHandoff()` before agent switch. Return from `get()` (MessageProvider) so StandingOrdersBuilder picks it up on the next standing orders build. Clear after first use.

**Why this approach:**
- Minimal change — one field, two method modifications
- Leverages existing MessageProvider mechanism (PeonAiService already implements it for scaffold agent context)
- No new interfaces or architectural changes
- Standing order is consumed once (like one-time orders), preventing pollution on subsequent sends

## Architecture

```
onHandoff() → _handoffLine = "Plan: /path + Handover from Peon-Plan"
  → setActiveAgent(devAgent)
    → preloadPlanIfNeeded() (adds plan path hint to memory — harmless)
  → active.call(handoffMsg) [or user sends first message]
    → standingOrders.build() → PeonAiService.get() returns [_handoffLine]
    → _handoffLine cleared
    → ToolLoopRequest.standingOrders includes handoff line
```

## Affected Files

| File | Change |
|------|--------|
| `org.sterl.llmpeon/parts/PeonAiService.java` | Add `_handoffLine` field; modify `get()` and `onHandoff()` |
| `org.sterl.llmpeon.test/test/PeonAiServiceTest.java` | New test: `testHandoffStandingOrder()` |
| `docs/plan-dev-agent-design.md` | Flip BDD 4 status ❌ → ✅ |

## Rules & Constraints

- `_handoffLine` is transient — cleared after first consumption in `get()`
- Standing order format matches existing handoff message: `"Plan: {path} + Handover from Peon-Plan"`
- Thread safety: `_handoffLine` set/read on UI thread only (handoff button → send), no sync needed
- No change to scaffold agent behavior — `get()` returns empty for non-scaffold when `_handoffLine` is null

## BDD Use Case

### 4. Plan Handover ✅

**Test:** `PeonAiServiceTest.testHandoffStandingOrder()` (unit) + `test_plan_handling()` already covers integration

```
GIVEN we completed a planning turn and a plan was created (saved to disk)
WHEN the user triggers handover to the dev agent
THEN the plan file path with handover instruction is added to standing orders
AND the plan content is added as a UserMessage ("chat") to the next handover agent's memory

GIVEN handoff just occurred and _handoffLine was set
WHEN PeonAiService.get() is called (standing orders build)
THEN it returns [_handoffLine] on first call
AND clears _handoffLine so subsequent calls return empty list
```

## Implementation Steps

1. **PeonAiService.java** — add `private volatile String _handoffLine = null;` field
2. **PeonAiService.get()** — prepend `_handoffLine` check before scaffold agent early-return:
   ```java
   if (_handoffLine != null) {
       var line = _handoffLine;
       _handoffLine = null;
       return List.of(line);
   }
   ```
3. **PeonAiService.onHandoff()** — set `_handoffLine` after reading plan:
   ```java
   if (plan != null) {
       String path = this.plan != null ? JdtUtil.pathOf(this.plan) : "(from chat memory)";
       _handoffLine = "Plan: " + path + System.lineSeparator() + "Handover from " + getActiveAgent().getName();
   }
   ```
4. **PeonAiServiceTest.java** — add `testHandoffStandingOrder()` verifying `_handoffLine` lifecycle
5. **docs/plan-dev-agent-design.md** — flip ✅ on BDD 4

## Open Questions

None — implementation is straightforward, scoped to one field + two methods.

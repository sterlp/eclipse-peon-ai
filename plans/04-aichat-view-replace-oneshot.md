# Task 4: AIChatView — Replace setOneShotSystemPrompt with addOneTimeOrder

**Feature Plan**: [commands-as-standing-orders.md](./commands-as-standing-orders.md)
**Depends on**: [Task 3](./03-abstract-chat-service-remove-one-shot.md)

## Goal

In `applySlashCommandIfPresent()`, replace `active.setOneShotSystemPrompt(prompt)` with `standingOrders.addOneTimeOrder(prompt)` for commands. Skills path is already using `addOneTimeOrder` — no change needed.

## Files

| File | Action |
|------|--------|
| `org.sterl.llmpeon/src/org/sterl/llmpeon/parts/AIChatView.java` | Change one line |

## Changes

### AIChatView.java (line ~456 in `applySlashCommandIfPresent`)

Replace:
```java
var prompt = command.get().readBody();
active.setOneShotSystemPrompt(prompt);
```
With:
```java
var prompt = command.get().readBody();
standingOrders.addOneTimeOrder(prompt);
```

Verify the skills path already uses `standingOrders.addOneTimeOrder(...)` — no change needed there.

## Verification

1. Eclipse project builds without errors: `buildEclipseProject` on `org.sterl.llmpeon`
2. `mvn clean verify` — all tests pass (especially plugin tests in `org.sterl.llmpeon.test`)
3. No compilation errors referencing `setOneShotSystemPrompt` or `hasOneShotSystemPrompt` in `org.sterl.llmpeon`
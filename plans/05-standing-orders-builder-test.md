# Task 5: StandingOrdersBuilderTest — Test One-Time Order Flow

**Feature Plan**: [commands-as-standing-orders.md](./commands-as-standing-orders.md)
**Depends on**: [Task 1](./01-tool-loop-request-standing-orders.md)

## Goal

Add a test verifying that command/skill one-time orders flow through `StandingOrdersBuilder.build()` correctly.

## Files

| File | Action |
|------|--------|
| `org.sterl.llmpeon.test/src/org/sterl/llmpeon/test/StandingOrdersBuilderTest.java` | Add tests for UC5 |

## Changes

### StandingOrdersBuilderTest.java

**UC5: Skill one-time order flows through build**
```java
@Test
public void testSkillOneTimeOrderSurvivesBuild() {
    // GIVEN
    var standingOrders = new StandingOrdersBuilder();
    
    // WHEN — add a one-time order (simulating skill body)
    standingOrders.addOneTimeOrder("This is a skill body — do X with Y.");
    
    var messages = standingOrders.build();
    
    // THEN — one-time order appears in output
    assertHasMessageWith(messages, "This is a skill body");
    assertHasMessageWith(messages, "do X with Y");
    
    // AND — one-time orders are consumed (not present in next build)
    var secondBuild = standingOrders.build();
    assertHasNoMessageWith(secondBuild, "This is a skill body");
}
```

**Add: command one-time order with providers**
```java
@Test
public void testCommandOneTimeOrderWithProviders() {
    // GIVEN — standing orders with providers + command one-time order
    PeonAiService aiService = new PeonAiService(null, null, null);
    aiService.setProject(project);
    var standingOrders = new StandingOrdersBuilder()
            .add(aiService)
            .add(aiService.getAgentsMdService());
    
    standingOrders.addOneTimeOrder("/review: Review the code and report any issues.");
    
    // WHEN
    var messages = standingOrders.build();
    
    // THEN — providers present
    assertHasMessageWith(messages, "/AGENTS.md");
    
    // AND — command body is present
    assertHasMessageWith(messages, "Review the code and report any issues");
    
    // AND — no nulls
    assertHasMessageWith(messages, " null");
    
    // AND — one-time order consumed (not in next build)
    var second = standingOrders.build();
    assertHasNoMessageWith(second, "Review the code and report any issues");
}
```

## Verification

1. `mvn clean verify` — all tests pass
2. `StandingOrdersBuilderTest` — all tests pass (2 existing + 2 new)
3. Helper methods compile
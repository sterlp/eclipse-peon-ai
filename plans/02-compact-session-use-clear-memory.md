# Task 2: CompactSessionTool — Use request.clearMemory()

**Feature Plan**: [commands-as-standing-orders.md](./commands-as-standing-orders.md)
**Depends on**: [Task 1](./01-tool-loop-request-standing-orders.md)

## Goal

Replace `request.getMemory().clear()` with `request.clearMemory()` so standing orders are re-injected after compaction.

## Files

| File | Action |
|------|--------|
| `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/tools/CompactSessionTool.java` | Change one line |
| `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/tool/tools/CompactSessionToolTest.java` | Add tests for UC1, UC4 |

## Changes

### CompactSessionTool.java (line ~38)

Replace:
```java
request.getMemory().clear();
```
With:
```java
request.clearMemory();
```

That's it — `clearMemory()` handles clear + re-inject of standing orders.

### CompactSessionToolTest.java

Add tests:

**UC1: Command survives compaction**
```java
@Test
void testStandingOrdersSurviveCompaction() {
    // GIVEN — ToolLoopRequest with standingOrders (simulating command body)
    var config = LlmConfig.builder()
            .model("default-model")
            .build();
    var cm = streamMock.buildMock(r -> ChatResponse.builder()
            .aiMessage(AiMessage.aiMessage("WHAT: Compressed summary"))
            .build());
    var configuredModel = new ConfiguredChatModel(config, cm);
    
    var memory = new ThreadSafeMemory();
    memory.add(UserMessage.from("User: /review refactor this class"));
    memory.add(AiMessage.from("I'll review it."));
    memory.add(UserMessage.from("Also check performance"));
    memory.add(AiMessage.from("Will do."));
    
    var req = ToolLoopRequest.builder()
            .chatModel(configuredModel)
            .memory(memory)
            .standingOrders(List.of("Review the code and report any issues."))
            .build();
    
    var subject = new CompactSessionTool();
    subject.withToolRequest(req);

    // WHEN
    subject.compactSession(null);

    // THEN — memory contains 2 messages: the standing order + compact resume
    var result = memory.getCopy();
    assertThat(result).hasSize(2);
    assertThat(((UserMessage)result.get(0)).singleText())
            .isEqualTo("Review the code and report any issues.");
    assertThat(((UserMessage)result.get(1)).singleText())
            .isEqualTo("Session compacted. Resume the task using the preserved context.");
}
```

**UC4: Multiple standing orders survive compaction**
```java
@Test
void testMultipleStandingOrdersSurviveCompaction() {
    // GIVEN — ToolLoopRequest with 3 standing orders
    var config = LlmConfig.builder().model("default-model").build();
    var cm = streamMock.buildMock(r -> ChatResponse.builder()
            .aiMessage(AiMessage.aiMessage("WHAT: Compressed summary"))
            .build());
    var configuredModel = new ConfiguredChatModel(config, cm);
    
    var memory = new ThreadSafeMemory();
    memory.add(UserMessage.from("Do something"));
    memory.add(AiMessage.from("OK"));
    
    var req = ToolLoopRequest.builder()
            .chatModel(configuredModel)
            .memory(memory)
            .standingOrders(List.of(
                "Project: test-project at /path/to/project",
                "AGENTS.md: Rule 1 — be concise",
                "/review: Review the code and report any issues."
            ))
            .build();
    
    var subject = new CompactSessionTool();
    subject.withToolRequest(req);

    // WHEN
    subject.compactSession(null);

    // THEN — all 3 standing orders re-injected in order + compact resume
    var result = memory.getCopy();
    assertThat(result).hasSize(4);
    assertThat(((UserMessage)result.get(0)).singleText()).contains("test-project");
    assertThat(((UserMessage)result.get(1)).singleText()).contains("AGENTS.md");
    assertThat(((UserMessage)result.get(2)).singleText()).contains("/review");
    assertThat(((UserMessage)result.get(3)).singleText()).contains("Session compacted");
}
```

Also update existing tests: `testCompactSessionUsesConfiguredCompactModel` and `testCompactSessionWithoutCompactModelUsesDefault` — verify the new assertion pattern (2 messages instead of 1 for tests that don't set standingOrders).

For the existing tests without standingOrders: they should have 1 message (only "Session compacted..."). Those tests are fine since `standingOrders` defaults to empty list.

## Verification

1. `mvn clean verify` — all tests pass
2. `CompactSessionToolTest` — all 4 tests pass (2 existing + 2 new)
3. Existing tests that verify memory size after compaction still work (empty standing orders → 1 message)
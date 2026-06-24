# Task 1: ToolLoopRequest — Add standingOrders + clearMemory()

**Feature Plan**: [commands-as-standing-orders.md](./commands-as-standing-orders.md)

## Goal

Add `List<String> standingOrders` field and `clearMemory()` method to `ToolLoopRequest`. This is the foundation — all other tasks depend on it.

## Files

| File | Action |
|------|--------|
| `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/ToolLoopRequest.java` | Add field + method |
| `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/tool/tools/CompactSessionToolTest.java` | Add test |

## Changes

### ToolLoopRequest.java

Add:
```java
@Default @Getter
public List<String> standingOrders = List.of();
```

Builder-style setter (consistent with existing pattern):
```java
public ToolLoopRequest standingOrders(List<String> standingOrders) {
    this.standingOrders = standingOrders == null ? List.of() : List.copyOf(standingOrders);
    return this;
}
```

Add `clearMemory()` method:
```java
public void clearMemory() {
    memory.clear();
    for (var order : standingOrders) {
        memory.add(UserMessage.from(order));
    }
}
```

### CompactSessionToolTest.java

Add test:

```java
@Test
void testClearMemoryWithoutStandingOrders() {
    // GIVEN — ToolLoopRequest with empty/missing standingOrders
    var memory = new ThreadSafeMemory();
    memory.add(UserMessage.from("Some message"));
    memory.add(AiMessage.from("Some response"));

    var config = LlmConfig.builder().model("test").build();
    var cm = streamMock.buildMock(r -> ChatResponse.builder()
            .aiMessage(AiMessage.aiMessage("summary"))
            .build());
    var configuredModel = new ConfiguredChatModel(config, cm);

    var req = ToolLoopRequest.builder()
            .chatModel(configuredModel)
            .memory(memory)
            .standingOrders(List.of())  // empty
            .build();

    // WHEN
    req.clearMemory();

    // THEN — memory is cleared, no extra messages added
    assertThat(memory.getCopy()).isEmpty();
}
```

## Verification

1. `mvn clean verify` in `llmpeon-parent` — all tests pass
2. `CompactSessionToolTest` — new test passes
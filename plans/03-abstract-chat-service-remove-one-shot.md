# Task 3: AbstractChatService — Remove oneShotSystemPrompt, Pass standingOrders

**Feature Plan**: [commands-as-standing-orders.md](./commands-as-standing-orders.md)
**Depends on**: [Task 1](./01-tool-loop-request-standing-orders.md)

## Goal

Remove the `oneShotSystemPrompt` mechanism from `AbstractChatService` and pass `userContextInformations` as `standingOrders` to `ToolLoopRequest.builder()`.

## Files

| File | Action |
|------|--------|
| `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/AbstractChatService.java` | Remove field + methods, update builder |
| `org.sterl.llmpeon.core/src/test/java/org/sterl/llmpeon/AiDeveloperServiceTest.java` | Update test_context, add test for UC2 |

## Changes

### AbstractChatService.java

**Remove:**
- Field: `private String oneShotSystemPrompt;` (line ~37)
- Method: `public void setOneShotSystemPrompt(String)` (lines ~101-103)
- Method: `public boolean hasOneShotSystemPrompt()` (lines ~105-107)
- Method: `private String consumeOneShotSystemPrompt()` (lines ~109-113)

**Simplify `buildStaticMessages()`:**
```java
private List<ChatMessage> buildStaticMessages(AiMonitor monitor) {
    var messages = new ArrayList<ChatMessage>();
    messages.add(SystemMessage.from(getSystemPrompt()));
    messages.addAll(staticContext);
    return messages;
}
```

**Update `call()` — add `.standingOrders()` to builder:**
```java
var response = toolService.executeLoop(
        ToolLoopRequest.builder()
            .memory(memory)
            .chatModel(configuredModel)
            .staticMessages(staticMessages)
            .monitor(monitor)
            .toolFilter(getToolFilter())
            .includeMcpTools(includesMcpTools())
            .temperature(getTemperature())
            .modelName(getAgentModelName())
            .standingOrders(userContextInformations)  // NEW
            .build()
        );
```

### AiDeveloperServiceTest.java

**Add UC2 test:**
```java
@Test
void testCommandAsStandingOrder() {
    // GIVEN — userContextInformations with command body, no compaction
    var requestRef = new AtomicReference<ChatRequest>();
    fn.set(req -> {
        requestRef.set(req);
        return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Review complete — no issues found.")).build();
    });
    
    // WHEN
    subject.setUserContextInformations(Arrays.asList("Review the code and report any issues."));
    subject.call("Refactor this class", null);

    // THEN — command body was prepended to user message
    verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    var mem = subject.getMemory().getCopy();
    var userMsg = ((UserMessage)mem.get(0)).singleText();
    assertThat(userMsg).contains("Review the code and report any issues.");
    assertThat(userMsg).contains("Refactor this class");
}
```

**Update `test_context`** — ensure it still passes with new flow. The method already prepends context info to user message; the new `standingOrders` field on `ToolLoopRequest` is internal and doesn't affect this test's assertions.

## Verification

1. `mvn clean verify` — all tests pass
2. `AiDeveloperServiceTest` — `test_context`, `testCommandAsStandingOrder`, `test_simple_call`, `test_clear_memory` all pass
3. No compilation errors referencing `oneShotSystemPrompt` in any source file
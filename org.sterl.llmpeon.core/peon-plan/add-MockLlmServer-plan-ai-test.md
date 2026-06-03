# Plan: Add MockLlmServer to llmpeon-core

## Context
Tests in `llmpeon-core` currently rely on real LLM providers or lack mock support. We need a lightweight, pure-Java HTTP stub to simulate LLM responses for testing `ConfiguredModel` and services without external dependencies.

## Why I haven't done it yet
I was analyzing the specific JSON structure required by `OpenAiStreamingChatModel` to ensure the SSE delta format was correct and confirming that `LlmConfig` allows URL overrides for local testing.

## Design Decisions
1.  **Implementation**: `MockLlmServer` class in `llmpeon-core/src/test/java/org/sterl/llmpeon/`.
2.  **Dependencies**: None (uses `com.sun.net.httpserver.HttpServer`).
3.  **Streaming**: Defaults to `text/event-stream` (SSE) to match `StreamingChatModel` usage.
4.  **Response Queue**: `addResponse(ChatMessage)` allows tests to queue specific responses.
5.  **Endpoint `/v1/models`**: **Returns HTTP 404 with an empty body by default.**
6.  **Endpoint `/v1/chat/completions`**: Returns SSE stream matching OpenAI format.

## Affected Files
*   `llmpeon-core/src/test/java/org/sterl/llmpeon/MockLlmServer.java` (New)
*   `llmpeon-core/src/test/java/org/sterl/llmpeon/MockLlmServerTest.java` (New)

## Step-by-Step Changes

### 1. Create `MockLlmServer.java`
*   **Structure**:
    *   `start()`: Starts server on port 0 (random), registers handlers.
    *   `stop()`: Stops server.
    *   `getPort()`: Returns bound port.
    *   `addResponse(ChatMessage msg)`: Queues a response for the next request.
*   **Default Response**: "Hello from Mock Ai" if queue is empty.

### 2. Implement `/v1/chat/completions` Handler
*   **Method**: Only accept `POST`.
*   **Request**: Consume body to prevent connection resets.
*   **Response**:
    *   `Content-Type: text/event-stream`.
    *   Construct SSE chunks:
        ```json
        {"id":"chatcmpl-...","object":"chat.completion.chunk","created":...,"model":"mock","choices":[{"index":0,"delta":{"role":"assistant","content":"..."},"finish_reason":null}]}
        ```
    *   Send full text in the first delta.
    *   End with `data: [DONE]`.
*   **Escaping**: Escape `\n`, `\r`, `\t`, `\\` in the content string.

### 3. Implement `/v1/models` Handler
*   **Endpoint**: `GET /v1/models` (or any path starting with `/v1/models`)
*   **Behavior**: Return HTTP 404 with an **empty body**.
*   **Reasoning**: This prevents the LLM client from crashing or hanging if it attempts to list available models, effectively signaling that no models are registered.

### 4. Usage in Tests
*   Create server: `MockLlmServer server = MockLlmServer.of("Default").start();`
*   Configure: `LlmConfig.newConfig(AiProvider.OPEN_AI, "gpt-4", server.getBaseUrl()).build()`
*   Set responses: `server.addResponse(aiMsg);`

### 5. Implement the `MockLlmServerTest` 

### 5.1. Chat Message Test
// GIVEN
server started
server response set to AI - "PONG"
chatmodel configured to the running server localhost and the random port

// WHEN
send with the chatmodel "PING"

// THEN
verify response ai AI message "PONG"
verify `MockLlmServer` received UserMessage `PING`

### 5.2. Model list Test with models

// GIVEN
server started
server model response set to `cool-llm1` and `cool-llm2`


// WHEN
list models

// THEN
verify the result contains both models

### 5.3. Model list with error

// GIVEN
server started
server model response error

// WHEN
list models

// THEN
received error message using list models

### 5.4

The mock server should also be able to mock / support test tool calls - bit like:
    @Test
    public void test_readWorkspaceFiles() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        ToolService service = new ToolService();
        service.addTool(new EclipseWorkspaceReadFileTool());

        var tr = ToolExecutionRequest.builder().arguments("")
            .name("readWorkspaceFile")
            .arguments("{\"filePath\": \"" + this.getClass().getName().replace(".", "/") + ".java\"}")
            .build();
        
        // WHEN
        var content = service.execute(tr, null, null, new ArrayList<ChatMessage>());
        
        // THEN
        assertContains(content.message().text(), 
                "Hallo meine schöne datei wie geht es dir?");
    }
    

package org.sterl.llmpeon.mock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.mock.model.CapturedTool;
import org.sterl.llmpeon.mock.model.ModelListResponse;
import org.sterl.llmpeon.mock.model.SseChunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@Slf4j
public class MockLlmServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private int port = 0;
    private final ConcurrentLinkedQueue<Object> responseQueue = new ConcurrentLinkedQueue<>();
    private final List<ChatMessage> capturedMessages = new ArrayList<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private List<String> modelIds = List.of("gpt-4o", "mock-model");
    private boolean modelsEndpointError = false;
    private boolean forceNonStreaming = false;

    public MockLlmServer(int port) {
        this.port = port;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        if (port == 0) {
            try (var s = new ServerSocket(0)) {
                port = s.getLocalPort();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        try {
        	long time = System.currentTimeMillis();
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/v1/chat/completions", this::handleChatCompletions);
            server.createContext("/v1/models", this::handleModels);
            server.createContext("/v1/messages", this::handleAnthropicMessages);
            server.createContext("/api/chat", this::handleOllamaChat);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            
            while (!isAlive()) {
            	if (System.currentTimeMillis() - time > 5_000) {
            		throw new RuntimeException("Failed to start " + getClass().getSimpleName() + " after " + (System.currentTimeMillis() - time) + "ms");
            	}
            	try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					stop();
					return;
				}
            }
        	time = System.currentTimeMillis() - time;
            log.info("LLM stub started on port={} after {}ms", server.getAddress().getPort(), time);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    public boolean isAlive() {
        try (var s = new Socket("127.0.0.1", getPort())) {
            return true; // server is accepting connections
        } catch (Exception e) {
            return false;
        }
    }

    
    public LlmConfig newConfig(String model) {
    	return LlmConfig.builder()
    		.providerType(AiProvider.OPEN_AI)
        	.model(model)
        	.url(getUrl())
        	.timeout(Duration.ofSeconds(30))
    	    .build();
    }

    public void stop() {
        if (server != null) server.stop(0);
        server = null;
    }


    public void reset() {
        responseQueue.clear();
        capturedMessages.clear();
        lastRequestBody.set(null);
        modelIds = List.of("gpt-4o", "mock-model");
        modelsEndpointError = false;
        forceNonStreaming = false;
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getUrl() {
        return "http://127.0.0.1:" + port + "/v1";
    }

    /** Base URL without the {@code /v1} suffix — the Ollama endpoint root ({@code /api/chat}). */
    public String rootUrl() {
        return "http://127.0.0.1:" + port;
    }

    // -------------------------------------------------------------------------
    // Queue / configuration
    // -------------------------------------------------------------------------

    public void queueResponse(String response) {
        responseQueue.offer(response);
    }

    public void queueResponse(AiMessage aiMessage) {
        responseQueue.offer(aiMessage);
    }

    public void setModelIds(List<String> modelIds) {
        this.modelIds = modelIds;
    }

    public void setNonStreaming(boolean nonStreaming) {
        this.forceNonStreaming = nonStreaming;
    }

    public void enableModelsError() {
        this.modelsEndpointError = true;
    }

    public String getLastRequestBody() {
        return lastRequestBody.get();
    }

    public List<ChatMessage> getCapturedMessages() {
        return capturedMessages;
    }
    
    public List<CapturedTool> getCapturedTools() {
        String body = lastRequestBody.get();
        if (body == null) return List.of();
        try {
            var root = MAPPER.readTree(body);
            var tools = root.path("tools");
            if (tools.isMissingNode()) return List.of();

            List<CapturedTool> result = new ArrayList<>();
            for (var tool : tools) {
                var fn = tool.path("function");
                var props = fn.path("parameters").path("properties");
                var required = fn.path("parameters").path("required");

                List<String> allParams = new ArrayList<>();
                props.fieldNames().forEachRemaining(allParams::add);

                List<String> requiredParams = new ArrayList<>();
                required.forEach(n -> requiredParams.add(n.asText()));

                result.add(new CapturedTool(
                        fn.path("name").asText(),
                        fn.path("description").asText(null),
                        requiredParams,
                        allParams));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse captured tools", e);
        }
    }

    // Convenience lookup by name
    public Optional<CapturedTool> getCapturedTool(String name) {
        return getCapturedTools().stream()
                .filter(t -> name.equals(t.name()))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // Shared response extraction — avoids duplication across streaming/non-streaming
    // -------------------------------------------------------------------------

    private record QueuedResponse(
            String content,
            List<SseChunk.ToolCall> toolCalls,
            String finishReason) {

        static QueuedResponse from(Object queued) {
            if (queued instanceof AiMessage ai) {
                var calls = ai.hasToolExecutionRequests()
                        ? buildToolCalls(ai.toolExecutionRequests())
                        : null;
                return new QueuedResponse(ai.text(), calls, calls != null ? "tool_calls" : "stop");
            }
            return new QueuedResponse(queued.toString(), null, "stop");
        }
    }

    private static List<SseChunk.ToolCall> buildToolCalls(List<ToolExecutionRequest> requests) {
        var idx = new AtomicInteger();
        return requests.stream()
                .map(req -> new SseChunk.ToolCall(
                        req.id() != null ? req.id() : "call_" + idx.getAndIncrement(),
                        req.name(),
                        req.arguments()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Request handlers
    // -------------------------------------------------------------------------

    private void handleChatCompletions(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
    	try {
    		if (!"POST".equals(exchange.getRequestMethod())) {
    			sendJson(exchange, 405, Map.of("error", "Method not allowed"));
    			return;
    		}
    		
    		String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    		lastRequestBody.set(requestBody);
    		captureMessages(requestBody);
    		
    		Object queued = responseQueue.poll();
    		if (queued == null) queued = "No queued response - default mock answer";
    		
    		var response = QueuedResponse.from(queued);
    		boolean stream = !forceNonStreaming && isStreaming(requestBody);
    		
    		if (stream) {
    			handleStreaming(exchange, response);
    		} else {
    			handleNonStreaming(exchange, response);
    		}
    	} finally {
			exchange.close();
		}
    }

    private void handleModels(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
    	try {
    		if (!"GET".equals(exchange.getRequestMethod())) {
    			sendJson(exchange, 405, Map.of("error", "Method not allowed"));
    			return;
    		}
    		if (modelsEndpointError) {
    			sendRaw(exchange, 500, "{\"error\": \"Internal server error\"}");
    			return;
    		}
    		sendJson(exchange, 200, new ModelListResponse(modelIds));
    	} finally {
			exchange.close();
		}
    }

    /**
     * Anthropic {@code POST /v1/messages} (wire format per langchain4j {@code DefaultAnthropicClient}):
     * captures the raw body (no OpenAI-shaped message capture) and always streams a minimal
     * Anthropic SSE event sequence back.
     */
    private void handleAnthropicMessages(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequestBody.set(requestBody);
            sendAnthropicSse(exchange, pollContent());
        } finally {
            exchange.close();
        }
    }

    /**
     * Ollama {@code POST /api/chat} (wire format per langchain4j {@code OllamaClient}):
     * captures the raw body and always streams newline-delimited JSON back (the final line carries
     * {@code done:true}).
     */
    private void handleOllamaChat(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequestBody.set(requestBody);
            sendOllamaNdjson(exchange, pollContent());
        } finally {
            exchange.close();
        }
    }

    private String pollContent() {
        Object queued = responseQueue.poll();
        return queued != null ? queued.toString() : "No queued response - default mock answer";
    }

    // -------------------------------------------------------------------------
    // Anthropic SSE response (text/event-stream, event: <type>\ndata: <json>\n\n)
    // -------------------------------------------------------------------------

    private void sendAnthropicSse(com.sun.net.httpserver.HttpExchange exchange, String content) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");

        var sb = new StringBuilder();
        appendAnthropicEvent(sb, "content_block_start", Map.of(
                "type", "content_block_start",
                "index", 0,
                "content_block", Map.of("type", "text", "text", "")));
        appendAnthropicEvent(sb, "content_block_delta", Map.of(
                "type", "content_block_delta",
                "index", 0,
                "delta", Map.of("type", "text_delta", "text", content)));
        appendAnthropicEvent(sb, "content_block_stop", Map.of(
                "type", "content_block_stop",
                "index", 0));
        appendAnthropicEvent(sb, "message_delta", Map.of(
                "type", "message_delta",
                "delta", Map.of("stop_reason", "end_turn"),
                "usage", Map.of("output_tokens", content.length())));
        appendAnthropicEvent(sb, "message_stop", Map.of("type", "message_stop"));

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void appendAnthropicEvent(StringBuilder sb, String event, Map<String, Object> data) throws IOException {
        sb.append("event: ").append(event).append('\n');
        sb.append("data: ").append(MAPPER.writeValueAsString(data)).append("\n\n");
    }

    // -------------------------------------------------------------------------
    // Ollama NDJSON response (one JSON object per line; final line done:true)
    // -------------------------------------------------------------------------

    private void sendOllamaNdjson(com.sun.net.httpserver.HttpExchange exchange, String content) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");

        var sb = new StringBuilder();
        sb.append(MAPPER.writeValueAsString(Map.of(
                "model", "mock-model",
                "message", Map.of("role", "assistant", "content", content),
                "done", false))).append('\n');
        sb.append(MAPPER.writeValueAsString(Map.of(
                "model", "mock-model",
                "message", Map.of("role", "assistant", "content", ""),
                "done_reason", "stop",
                "done", true,
                "prompt_eval_count", 5,
                "eval_count", content.length()))).append('\n');

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    // -------------------------------------------------------------------------
    // Non-streaming response
    // -------------------------------------------------------------------------

    private void handleNonStreaming(com.sun.net.httpserver.HttpExchange exchange, QueuedResponse r) throws IOException {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        if (r.content() != null) message.put("content", r.content());
        if (r.toolCalls() != null) message.put("tool_calls", r.toolCalls());

        long tokenCount = r.content() != null ? r.content().length() : 0;
        var result = Map.of(
                "id", "chatcmpl-mock",
                "object", "chat.completion",
                "created", System.currentTimeMillis() / 1000,
                "model", "mock-model",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", message,
                        "finish_reason", r.finishReason()
                )),
                "usage", Map.of(
                        "prompt_tokens", 10,
                        "completion_tokens", tokenCount,
                        "total_tokens", 10L + tokenCount
                )
        );
        sendJson(exchange, 200, result);
    }

    // -------------------------------------------------------------------------
    // Streaming response
    // -------------------------------------------------------------------------

    private void handleStreaming(com.sun.net.httpserver.HttpExchange exchange, QueuedResponse r) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");

        var sseData = new StringBuilder();

        // Opening role delta
        appendSse(sseData, sseChunk(new SseChunk.Choice(new SseChunk.Delta(""), null)));

        // Content chunks
        for (String chunk : splitIntoChunks(r.content() != null ? r.content() : "", 10)) {
            appendSse(sseData, sseChunk(new SseChunk.Choice(chunk, null)));
        }

        // Tool call delta — one chunk carries all tool calls in delta.tool_calls
        if (r.toolCalls() != null) {
            SseChunk.Delta toolDelta = new SseChunk.Delta(null);
            toolDelta.setToolCalls(r.toolCalls()); // requires the Delta field added above
            appendSse(sseData, sseChunk(new SseChunk.Choice(toolDelta, null)));
        }

        // Final chunk with finish_reason
        appendSse(sseData, sseChunk(new SseChunk.Choice(new SseChunk.Delta(""), r.finishReason())));
        sseData.append("data: [DONE]\n\n");

        byte[] bytes = sseData.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private SseChunk sseChunk(SseChunk.Choice choice) {
        SseChunk chunk = new SseChunk();
        chunk.setChoices(List.of(choice));
        return chunk;
    }

    private void appendSse(StringBuilder sb, SseChunk chunk) throws IOException {
        sb.append("data: ").append(MAPPER.writeValueAsString(chunk)).append("\n\n");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void captureMessages(String requestBody) {
        try {
            var root = MAPPER.readTree(requestBody);
            for (var msg : root.path("messages")) {
                String role = msg.path("role").asText();
                var contentNode = msg.path("content");
                switch (role) {
                    case "user" -> {
                        if (contentNode.isTextual()) {
                            capturedMessages.add(UserMessage.from(contentNode.asText()));
                        } else {
                            capturedMessages.add(extractUserMessageFromArray(contentNode));
                        }
                    }
                    case "tool" -> {
                        String text = contentNode.isTextual() ? contentNode.asText() : extractTextFromArray(contentNode);
                        capturedMessages.add(new ToolExecutionResultMessage(
                                msg.path("tool_call_id").asText(), "", text));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private UserMessage extractUserMessageFromArray(com.fasterxml.jackson.databind.JsonNode contentNode) {
        if (!contentNode.isArray()) return UserMessage.from((String) null);
        var contents = new java.util.ArrayList<dev.langchain4j.data.message.Content>();
        for (var item : contentNode) {
            if ("text".equals(item.path("type").asText())) {
                contents.add(dev.langchain4j.data.message.TextContent.from(item.path("text").asText()));
            }
        }
        return contents.isEmpty() ? UserMessage.from((String) null) : UserMessage.from(contents);
    }

    private String extractTextFromArray(com.fasterxml.jackson.databind.JsonNode contentNode) {
        if (!contentNode.isArray()) return null;
        var sb = new StringBuilder();
        for (var item : contentNode) {
            if ("text".equals(item.path("type").asText())) {
                sb.append(item.path("text").asText());
            }
        }
        return sb.toString().isEmpty() ? null : sb.toString();
    }

    private boolean isStreaming(String requestBody) {
        try {
            return MAPPER.readTree(requestBody).path("stream").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> splitIntoChunks(String text, int chunkSize) {
        var chunks = new ArrayList<String>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            chunks.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return chunks;
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, Object data) throws IOException {
        sendRaw(exchange, status, MAPPER.writeValueAsString(data));
    }

    private void sendRaw(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
package org.sterl.llmpeon.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.sterl.llmpeon.mock.model.ModelListResponse;
import org.sterl.llmpeon.mock.model.SseChunk;

import com.sun.net.httpserver.HttpServer;

public class MockLlmServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private int port;
    private ConcurrentLinkedQueue<String> responseQueue = new ConcurrentLinkedQueue<>();
    private List<String> modelIds = List.of("gpt-4o", "mock-model");
    private AtomicReference<String> lastRequestBody = new AtomicReference<>(null);
    private boolean modelsEndpointError = false;
    private boolean forceNonStreaming = false;

    public void setNonStreaming(boolean nonStreaming) {
        this.forceNonStreaming = nonStreaming;
    }

    public MockLlmServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/v1/chat/completions", this::handleChatCompletions);
        server.createContext("/v1/models", this::handleModels);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public void queueResponse(String response) {
        responseQueue.offer(response);
    }

    public void setModelIds(List<String> modelIds) {
        this.modelIds = modelIds;
    }

    public void enableModelsError() {
        this.modelsEndpointError = true;
    }

    public String getLastRequestBody() {
        return lastRequestBody.get();
    }

    private void handleChatCompletions(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastRequestBody.set(requestBody);

        String response = responseQueue.poll();
        if (response == null) {
            response = "No queued response - default mock answer";
        }

        boolean stream = !forceNonStreaming && isStreaming(requestBody);

        if (stream) {
            handleStreaming(exchange, response);
        } else {
            handleNonStreaming(exchange, response);
        }
    }

    private boolean isStreaming(String requestBody) {
        try {
            Map<String, Object> request = MAPPER.readValue(requestBody, Map.class);
            return Boolean.TRUE.equals(request.get("stream"));
        } catch (Exception e) {
            return false;
        }
    }

    private void handleStreaming(com.sun.net.httpserver.HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");

        SseChunk firstChunk = new SseChunk();
        firstChunk.setChoices(List.of(new SseChunk.Choice(new SseChunk.Delta(""), null)));

        List<String> chunks = splitIntoChunks(response, 10);
        StringBuilder sseData = new StringBuilder();
        sseData.append("data: ").append(MAPPER.writeValueAsString(firstChunk)).append("\n\n");

        for (String chunk : chunks) {
            SseChunk dataChunk = new SseChunk();
            dataChunk.setChoices(List.of(new SseChunk.Choice(chunk, null)));
            sseData.append("data: ").append(MAPPER.writeValueAsString(dataChunk)).append("\n\n");
        }

        SseChunk finalChunk = new SseChunk();
        finalChunk.setChoices(List.of(new SseChunk.Choice(new SseChunk.Delta(""), "stop")));
        sseData.append("data: ").append(MAPPER.writeValueAsString(finalChunk)).append("\n\n");
        sseData.append("data: [DONE]\n\n");

        byte[] bytes = sseData.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void handleNonStreaming(com.sun.net.httpserver.HttpExchange exchange, String response) throws IOException {
        Map<String, Object> result = Map.of(
                "id", "chatcmpl-mock",
                "object", "chat.completion",
                "created", System.currentTimeMillis() / 1000,
                "model", "mock-model",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", response),
                        "finish_reason", "stop"
                )),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", (long) response.length(), "total_tokens", 10L + response.length())
        );

        sendJson(exchange, 200, result);
    }

    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            chunks.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return chunks;
    }

    private void handleModels(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        if (modelsEndpointError) {
            byte[] errorBytes = "{\"error\": \"Internal server error\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorBytes.length);
            exchange.getResponseBody().write(errorBytes);
            return;
        }

        ModelListResponse response = new ModelListResponse(modelIds);
        sendJson(exchange, 200, response);
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json;
        try {
            if (data instanceof Map) {
                json = MAPPER.writeValueAsString(data);
            } else {
                json = MAPPER.writeValueAsString(data);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response", e);
        }

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}

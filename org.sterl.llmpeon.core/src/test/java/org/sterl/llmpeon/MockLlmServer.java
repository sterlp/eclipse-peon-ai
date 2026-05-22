package org.sterl.llmpeon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.sun.net.httpserver.HttpServer;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

public class MockLlmServer {

    private static final String DEFAULT_RESPONSE = "Hello from Mock Ai";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final Queue<ChatMessage> responseQueue = new LinkedList<>();
    private final AtomicReference<List<String>> modelsRef = new AtomicReference<>(null);
    private final AtomicReference<Boolean> modelsErrorRef = new AtomicReference<>(false);
    private final AtomicReference<UserMessage> lastUserMessage = new AtomicReference<>();

    private MockLlmServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
    }

    public static MockLlmServer of(String name) {
        try {
            return new MockLlmServer(0);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create mock server", e);
        }
    }

    public MockLlmServer start() {
        registerHandlers();
        server.start();
        return this;
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getBaseUrl() {
        return "http://localhost:" + getPort() + "/v1";
    }

    public void addResponse(ChatMessage msg) {
        responseQueue.add(msg);
    }

    public void setModels(List<String> models) {
        modelsRef.set(models != null ? List.copyOf(models) : null);
        modelsErrorRef.set(false);
    }

    public void setErrorOnModels() {
        modelsErrorRef.set(true);
        modelsRef.set(null);
    }

    public UserMessage getLastUserMessage() {
        return lastUserMessage.get();
    }

    private void registerHandlers() {
        server.createContext("/v1/chat/completions", this::handleChatCompletions);
        server.createContext("/v1/models", this::handleModels);
    }

    private void handleChatCompletions(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        // Consume request body properly
        String requestBody;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            requestBody = reader.lines().reduce("", (a, b) -> a + "\n" + b);
        }

        lastUserMessage.set(parseLastUserMessage(requestBody));

        ChatMessage responseMsg = responseQueue.poll();
        String content = responseMsg != null ? extractText(responseMsg) : DEFAULT_RESPONSE;

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");

        String sseChunk = buildSseChunk(content);
        byte[] responseBytes = (sseChunk + "\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
            os.flush();
        }
        exchange.close();
    }

    private void handleModels(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (modelsErrorRef.get()) {
            String errorBody = "{\"error\":{\"message\":\"Internal server error\",\"type\":\"server_error\"}}";
            byte[] bytes = errorBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
            return;
        }

        List<String> models = modelsRef.get();
        if (models == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\":[");
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(escapeJson(models.get(i))).append("\"}");
        }
        sb.append("]}");

        byte[] responseBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
        exchange.close();
    }

    private String buildSseChunk(String content) {
        String escapedContent = escapeJson(content);
        return "data: {\"id\":\"chatcmpl-mock\",\"object\":\"chat.completion.chunk\",\"created\":"
                + System.currentTimeMillis() / 1000
                + ",\"model\":\"mock\",\"choices\":["
                + "{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + escapedContent + "\"},\"finish_reason\":\"stop\"}"
                + "]}\n\n";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private UserMessage parseLastUserMessage(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode messages = root.get("messages");
            if (messages == null || !messages.isArray()) return null;

            String userContent = null;
            for (JsonNode msg : messages) {
                JsonNode role = msg.get("role");
                if (role != null && "user".equals(role.asText())) {
                    JsonNode content = msg.get("content");
                    if (content != null && content.isTextual()) {
                        userContent = content.asText();
                    }
                }
            }
            return userContent != null ? UserMessage.from(userContent) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractText(ChatMessage msg) {
        if (msg instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
            return aiMsg.text();
        }
        if (msg instanceof UserMessage userMsg) {
            return userMsg.singleText();
        }
        return DEFAULT_RESPONSE;
    }
}

package org.sterl.llmpeon.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.LlmConfig;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

public class MockLlmServerTest {

    private static MockLlmServer server = new MockLlmServer();
    private static HttpClient client;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @BeforeAll
    static void setUp() throws Exception {
        server.start();
        client = HttpClient.newBuilder()
        		.connectTimeout(TIMEOUT)
        		.build();
        
        assertTrue(server.isAlive());
    }

    @AfterAll
    static void tearDown() {
        server.stop();
    }

    // 5.1 Chat Message Test - verify PONG response and PING received
    @Test
    void shouldReturnQueuedResponseAndTrackUserMessage() throws Exception {
        // GIVEN
        String expectedResponse = "PONG";
        server.queueResponse(expectedResponse);

        var request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"stream\": false, \"messages\": [{\"role\": \"user\", \"content\": \"PING\"}]}"))
                .build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN - verify AI message "PONG"
        assertThat(response.statusCode()).isEqualTo(200);
        Map<?, ?> body = parseJson(response.body());
        List<?> choices = (List<?>) body.get("choices");
        assertThat(choices).hasSize(1);
        Map<?, ?> choice = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        assertThat(message.get("content")).isEqualTo(expectedResponse);

        // THEN - verify MockLlmServer received UserMessage "PING"
        String requestBody = server.getLastRequestBody();
        assertThat(requestBody).contains("PING");
    }

    @Test
    void shouldReturnQueuedResponseInStreamingMode() throws Exception {
        // GIVEN
        String expectedResponse = "Hello from mock";
        server.queueResponse(expectedResponse);

        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"stream\": true}")).build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).startsWith("data:");
        assertThat(body).contains("Hello from");
        assertThat(body).contains(" mock");
        assertThat(body).endsWith("data: [DONE]\n\n");
    }

    @Test
    void shouldReturnDefaultResponseWhenQueueIsEmpty() throws Exception {
        // GIVEN - no response queued

        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"stream\": false}")).build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN
        assertThat(response.statusCode()).isEqualTo(200);
    }

    // 5.2 Model list Test with models
    @Test
    void shouldReturnModelsList() throws Exception {
        // GIVEN
        List<String> models = List.of("cool-llm1", "cool-llm2");
        server.setModelIds(models);

        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/models"))
                .GET()
                .build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN
        assertThat(response.statusCode()).isEqualTo(200);
        Map<?, ?> body = parseJson(response.body());
        List<?> data = (List<?>) body.get("data");
        assertThat(data).hasSize(2);
    }

    // 5.3 Model list with error
    @Test
    void shouldReturnErrorOnModelsEndpoint() throws Exception {
        // GIVEN
        server.enableModelsError();

        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/models")).GET()
                .build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN - received error message using list models
        assertThat(response.statusCode()).isEqualTo(500);
        String body = response.body();
        assertThat(body).contains("error");
    }

    @Test
    void shouldRejectNonPostOnChatEndpoint() throws Exception {
        // GIVEN
        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/chat/completions")).GET()
                .build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN
        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void shouldRejectNonGetOnModelsEndpoint() throws Exception {
        // GIVEN
        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/models"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN
        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void shouldReturnAiMessageWithToolCalls() throws Exception {
        // GIVEN
        ToolExecutionRequest toolReq = ToolExecutionRequest.builder().id("call_abc").name("get_weather")
                .arguments("{\"city\": \"Berlin\"}").build();
        AiMessage aiMessage = new AiMessage(null, List.of(toolReq));
        server.queueResponse(aiMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"stream\": false, \"messages\": [{\"role\": \"user\", \"content\": \"What's the weather?\"}]}"))
                .build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN - verify tool calls in response
        assertThat(response.statusCode()).isEqualTo(200);
        Map<?, ?> body = parseJson(response.body());
        List<?> choices = (List<?>) body.get("choices");
        assertThat(choices).hasSize(1);
        Map<?, ?> choice = (Map<?, ?>) choices.get(0);
        assertThat(choice.get("finish_reason")).isEqualTo("tool_calls");
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        List<?> toolCalls = (List<?>) message.get("tool_calls");
        assertThat(toolCalls).hasSize(1);
        Map<?, ?> toolCall = (Map<?, ?>) toolCalls.get(0);
        assertThat(toolCall.get("type")).isEqualTo("function");
        Map<?, ?> function = (Map<?, ?>) toolCall.get("function");
        assertThat(function.get("name")).isEqualTo("get_weather");
    }

    @Test
    void shouldReturnAiMessagePlainText() throws Exception {
        // GIVEN
        AiMessage aiMessage = new AiMessage("Hello from AI");
        server.queueResponse(aiMessage);
        var configuredModel = LlmConfig.newOpenAi("foo", server.getUrl()).build();

        // WHEN
        var response = configuredModel.callBlocking(UserMessage.from("Hallo from User"));

        // THEN - verify plain text from AiMessage
        assertThat(response.aiMessage().text()).isEqualTo("Hello from AI");
    }

    @Test
    void shouldCaptureToolMessages() throws Exception {
        // GIVEN
    	server.reset();
        server.queueResponse("OK");

        String requestBody = "{\"stream\": false, \"messages\": ["
                + "{\"role\": \"user\", \"content\": \"What's the weather?\"}, "
                + "{\"role\": \"tool\", \"tool_call_id\": \"call_123\", \"content\": \"25°C\"}"
                + "]}"
        ;

        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // WHEN
        client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN - verify both user and tool messages captured
        assertThat(server.getCapturedMessages()).hasSize(2);
        UserMessage userMsg = (UserMessage) server.getCapturedMessages().get(0);
        assertThat(userMsg.singleText()).isEqualTo("What's the weather?");

        ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) server.getCapturedMessages().get(1);
        assertThat(toolMsg.id()).isEqualTo("call_123");
        assertThat(toolMsg.text()).isEqualTo("25°C");
    }

    // 5.4 Anthropic wire format (langchain4j DefaultAnthropicClient): SSE event stream
    @Test
    void shouldStreamAnthropicSseAndCaptureBody() throws Exception {
        // GIVEN
        server.reset();
        server.queueResponse("Hello Anthropic");

        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.getUrl() + "/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", "test-key")
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"model\":\"claude-mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN — a minimal Anthropic SSE event sequence, body captured
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("event: content_block_start");
        assertThat(body).contains("event: content_block_delta");
        assertThat(body).contains("Hello Anthropic");
        assertThat(body).contains("event: content_block_stop");
        assertThat(body).contains("event: message_delta");
        assertThat(body).contains("end_turn");
        assertThat(body).contains("event: message_stop");
        assertThat(server.getLastRequestBody()).contains("claude-mock");
    }

    // 5.5 Ollama wire format (langchain4j OllamaClient): newline-delimited JSON
    @Test
    void shouldStreamOllamaNdjsonAndCaptureBody() throws Exception {
        // GIVEN
        server.reset();
        server.queueResponse("Hello Ollama");

        HttpRequest request = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(server.rootUrl() + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"model\":\"llama-mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN — NDJSON, final line done:true + done_reason:stop, body captured
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        String[] lines = body.strip().split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).contains("\"done\":false");
        assertThat(lines[1]).contains("\"done\":true");
        assertThat(lines[1]).contains("\"done_reason\":\"stop\"");
        assertThat(body).contains("Hello Ollama");
        assertThat(server.getLastRequestBody()).contains("llama-mock");
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> parseJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

package org.sterl.llmpeon.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MockLlmServerTest {

    private MockLlmServer server;
    private HttpClient client;
    private int port = 18080;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockLlmServer(port);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    // 5.1 Chat Message Test - verify PONG response and PING received
    @Test
    void shouldReturnQueuedResponseAndTrackUserMessage() throws Exception {
        // GIVEN
        String expectedResponse = "PONG";
        server.queueResponse(expectedResponse);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"stream\": false, \"messages\": [{\"role\": \"user\", \"content\": \"PING\"}]}"))
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
                .uri(URI.create("http://localhost:" + port + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"stream\": true}"))
                .build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).startsWith("data:");
        assertThat(body).contains("Hello from");
        assertThat(body).contains(" mock");
        assertThat(body).endsWith("data: [DONE]\n");
    }

    @Test
    void shouldReturnDefaultResponseWhenQueueIsEmpty() throws Exception {
        // GIVEN - no response queued

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"stream\": false}"))
                .build();

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
                .uri(URI.create("http://localhost:" + port + "/v1/models"))
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
                .uri(URI.create("http://localhost:" + port + "/v1/models"))
                .GET()
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
                .uri(URI.create("http://localhost:" + port + "/v1/chat/completions"))
                .GET()
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
                .uri(URI.create("http://localhost:" + port + "/v1/models"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        // WHEN
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // THEN
        assertThat(response.statusCode()).isEqualTo(405);
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

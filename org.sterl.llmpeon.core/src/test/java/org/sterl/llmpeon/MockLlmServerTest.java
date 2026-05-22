package org.sterl.llmpeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

class MockLlmServerTest {

    private MockLlmServer server;

    @BeforeEach
    void setUp() {
        server = MockLlmServer.of("test").start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    @Test
    void chat_returnsQueuedResponse() {
        // GIVEN
        server.addResponse(AiMessage.from("PONG"));
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4").url(server.getBaseUrl()).build();
        var model = config.build().getChatModel();

        // WHEN
        var request = ChatRequest.builder()
                .messages(UserMessage.from("PING"))
                .build();
        var response = new org.sterl.llmpeon.streaming.StreamingBridge().call(model, request, null);

        // THEN
        assertEquals("PONG", response.aiMessage().text());
    }

    @Test
    void chat_receivesUserMessage() {
        // GIVEN
        server.addResponse(AiMessage.from("PONG"));
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4").url(server.getBaseUrl()).build();
        var model = config.build().getChatModel();

        // WHEN
        var request = ChatRequest.builder()
                .messages(UserMessage.from("PING"))
                .build();
        new org.sterl.llmpeon.streaming.StreamingBridge().call(model, request, null);

        // THEN
        UserMessage received = server.getLastUserMessage();
        assertNotNull(received);
        assertEquals("PING", received.singleText());
    }

    @Test
    void chat_returnsDefaultResponse_whenQueueEmpty() {
        // GIVEN
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4").url(server.getBaseUrl()).build();
        var model = config.build().getChatModel();

        // WHEN
        var request = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .build();
        var response = new org.sterl.llmpeon.streaming.StreamingBridge().call(model, request, null);

        // THEN
        assertEquals("Hello from Mock Ai", response.aiMessage().text());
    }

    @Test
    void listModels_returnsConfiguredModels() {
        // GIVEN
        server.setModels(List.of("cool-llm1", "cool-llm2"));
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4").url(server.getBaseUrl()).apiKey("test-key").build();

        // WHEN
        List<org.sterl.llmpeon.ai.model.AiModel> models = config.listAiModels();

        // THEN
        assertEquals(2, models.size());
        assertTrue(models.stream().anyMatch(m -> "cool-llm1".equals(m.getId())));
        assertTrue(models.stream().anyMatch(m -> "cool-llm2".equals(m.getId())));
    }

    @Test
    void listModels_returnsEmptyOnServerError() {
        // GIVEN
        server.setErrorOnModels();
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4").url(server.getBaseUrl()).apiKey("test-key").build();

        // WHEN
        List<org.sterl.llmpeon.ai.model.AiModel> models = config.listAiModels();

        // THEN - parser catches errors and returns empty list
        assertTrue(models.isEmpty());
    }
}

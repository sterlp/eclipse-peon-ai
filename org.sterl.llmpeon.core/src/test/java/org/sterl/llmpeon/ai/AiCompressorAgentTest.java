package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/agents.md
 */
class AiCompressorAgentTest {

    private MockLlmServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockLlmServer(0);
        server.start();
        
        // Wait briefly for server to be ready (HttpServer starts async)
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void test_compressContext() {
        // GIVEN
        var config = LlmConfig.newConfig(AiProvider.OPEN_AI, "mock-model", 
                String.format("http://localhost:%d/v1", server.getPort()));
        server.queueResponse("WHAT: Build a Java Hello world application that displays the current time when executed.");

        var subject = new AiDevAgent(config.build(), new ToolService());

        subject.addMessage(UserMessage.from("Build be a Hello world"));
        subject.addMessage(AiMessage.from("In which language?"));
        subject.addMessage(UserMessage.from("In java"));
        subject.addMessage(AiMessage.from("What should it do?"));
        subject.addMessage(UserMessage.from("It should show a Hello world with the current time"));

        // WHEN
        var result = subject.compact(AiMonitor.NULL_MONITOR);

        // THEN
        System.out.println(result.aiMessage().text());
        System.out.println(result.metadata());

        assertTrue(result.aiMessage().text().length() > 10);
        assertTrue(result.aiMessage().text().contains("WHAT"));

        // AND
        assertTrue(subject.getMemory().size() <= 2, "Chat messages aren't reduced! Still " + subject.getMemory().size());
    }

    @Test
    void call_throws_on_null_response() {
        // GIVEN — ConfiguredChatModel returns null (simulates streaming failure)
        // Mockito can't mock concrete classes on Java 25 (Byte Buddy limitation),
        // so we use an anonymous subclass instead.
        var config = LlmConfig.newOpenAi("test-key");
        var configuredModel = new ConfiguredChatModel(config) {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse callBlocking(
                    ChatRequest req, AiMonitor monitor) {
                return null;
            }
        };
        var subject = new AiCompressorAgent(configuredModel);
        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(UserMessage.from("test message"));

        // WHEN + THEN
        assertThatThrownBy(() -> subject.call(messages, AiMonitor.NULL_MONITOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI call returned null");
    }
}

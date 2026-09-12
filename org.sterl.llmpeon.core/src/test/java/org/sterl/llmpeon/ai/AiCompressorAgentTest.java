package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

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
        var compactMessage = subject.compact(AiMonitor.NULL_MONITOR).aiMessage();

        // THEN
        assertTrue(compactMessage.text().length() > 10);
        assertTrue(compactMessage.text().contains("WHAT"));

        // AND
        assertTrue(subject.getMemory().size() <= 2, "Chat messages aren't reduced! Still " + subject.getMemory().size());
    }

    @Test
    void test_sendsSystemPromptToLlm() {
        // GIVEN — StreamMock returns a compressed briefing; the prompt text comes from
        // compressor.txt, so a plain default LlmConfig is enough
        var streamMock = new StreamMock();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Test summary"))
                .build());
        var config = LlmConfig.builder().model("test").build();
        var subject = new AiCompressorAgent(new ConfiguredChatModel(config, cm));
        var monitor = new CapturingMonitor();

        // WHEN — three messages, the last a duplicate of the first
        subject.call(List.of(UserMessage.from("Foo"), AiMessage.from("Bar"), UserMessage.from("Foo")), monitor);

        // THEN — the request carries the COMPRESS_SYSTEM system message from compressor.txt
        assertThat(streamMock.getLastRequest()).isNotNull();
        var system = streamMock.getLast(SystemMessage.class).orElseThrow();
        assertThat(system.text()).isNotBlank();
        assertThat(system.text()).contains("WHAT:");

        // AND — the compact UserMessage reaching the LLM carries every distinct message,
        // the duplicate only once
        var compacted = streamMock.getLast(UserMessage.class).orElseThrow();
        assertThat(compacted.singleText()).isNotBlank();
        assertThat(compacted.singleText()).contains("Foo").contains("Bar");
        assertThat(count(compacted.singleText(), "Foo")).isEqualTo(1);

        // AND — the monitor (chat view) sees the same non-empty request and the response
        // as a single AI message
        assertThat(monitor.chatRequests).hasSize(1);
        var monitorUser = monitor.chatRequests.getFirst().messages().stream()
                .filter(UserMessage.class::isInstance).map(UserMessage.class::cast).toList();
        assertThat(monitorUser).hasSize(1);
        assertThat(monitorUser.getFirst().singleText()).isNotBlank();
        assertThat(monitor.responses).anySatisfy(m -> {
            assertThat(m.role()).isEqualTo(SimpleMessage.Type.AI);
            assertThat(m.message()).isEqualTo("WHAT: Test summary");
        });
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

    /** Monitor capturing what the chat view would see: the LLM request and the response messages. */
    static final class CapturingMonitor implements AiMonitor {
        final List<ChatRequest> chatRequests = new ArrayList<>();
        final List<SimpleMessage> responses = new ArrayList<>();

        @Override
        public void onChatResponse(SimpleMessage message) {
            responses.add(message);
        }

        @Override
        public void onChatMessage(int iteration, ChatRequest.Builder request) {
            chatRequests.add(request.build());
        }
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}

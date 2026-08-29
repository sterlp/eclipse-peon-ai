package org.sterl.llmpeon.tool.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

class CompactSessionToolTest {

    private StreamMock streamMock;

    @BeforeEach
    void beforeEach() {
        streamMock = new StreamMock();
    }

    @Test
    void testCompactSessionUsesConfiguredCompactModel() {
        // GIVEN — config with compactModel="compact-specific-model", a real owning agent
        var config = LlmConfig.builder()
                .model("default-model")
                .compactModel("compact-specific-model")
                .build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Test context summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        var agent = new AiDevAgent(configuredModel, new ToolService());
        agent.getMemory().add(UserMessage.from("First message"));
        agent.getMemory().add(AiMessage.from("AI response 1"));
        agent.getMemory().add(UserMessage.from("Second message"));
        agent.getMemory().add(AiMessage.from("AI response 2"));

        var subject = new CompactSessionTool();
        subject.withToolRequest(ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(agent.getMemory())
                .agent(agent)
                .build());

        // WHEN
        subject.compactSession(null);

        // THEN — the compressor request should have modelName="compact-specific-model"
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isEqualTo("compact-specific-model");
    }

    @Test
    void testCompactSessionWithoutCompactModelUsesDefault() {
        // GIVEN — config without compactModel (null), a real owning agent
        var config = LlmConfig.builder()
                .model("default-model")
                .build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Test context summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        var agent = new AiDevAgent(configuredModel, new ToolService());
        agent.getMemory().add(UserMessage.from("Test message"));
        agent.getMemory().add(AiMessage.from("AI response"));

        var subject = new CompactSessionTool();
        subject.withToolRequest(ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(agent.getMemory())
                .agent(agent)
                .build());

        // WHEN
        subject.compactSession(null);

        // THEN — the compressor request should have no modelName override (null means provider default)
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isNull();
    }

    @Test
    void testCompactSessionDelegatesToAgent() {
        // GIVEN — a request with an agent set
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.add(AiMessage.from("AI response"));

        var config = LlmConfig.builder().model("test").build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Compressed summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);

        AtomicBoolean compressCalled = new AtomicBoolean(false);
        AiAgent mockAgent = new AiAgent() {
            @Override public String getName() { return "test-agent"; }
            @Override public String getSystemPrompt() { return "system"; }
            @Override public ChatResponse call(String message, AiMonitor monitor) { return null; }
            @Override public ChatResponse compressContext(AiMonitor monitor) {
                compressCalled.set(true);
                return ChatResponse.builder().aiMessage(AiMessage.aiMessage("WHAT: Compressed summary")).build();
            }
            @Override public ThreadSafeMemory getMemory() { return memory; }
            @Override public void clear() {}
            @Override public boolean isToolActive(SmartToolExecutor exec) { return true; }
            @Override public boolean isMcpToolActive(String toolName) { return true; }
            @Override public int tokenContextUsedInPercent() { return 0; }
        };

        var toolRequest = ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(memory)
                .agent(mockAgent)
                .build();

        var subject = new CompactSessionTool();
        subject.withToolRequest(toolRequest);

        // WHEN
        String result = subject.compactSession(null);

        // THEN — agent.compressContext was called
        assertThat(compressCalled).isTrue();
        // AND — tool returns the summary text (no fallback resume message injected by the tool)
        assertThat(result).contains("WHAT: Compressed summary");
    }

    @Test
    void testCompactSessionWithoutAgentThrows() {
        // GIVEN — a request without an agent (mis-wiring)
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.add(AiMessage.from("AI response"));

        var config = LlmConfig.builder().model("test").build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("summary"))
                .build());

        var toolRequest = ToolLoopRequest.builder()
                .chatModel(new ConfiguredChatModel(config, cm))
                .memory(memory)
                .build();

        var subject = new CompactSessionTool();
        subject.withToolRequest(toolRequest);

        // WHEN / THEN — the mis-wiring surfaces loudly, nothing runs
        assertThatThrownBy(() -> subject.compactSession(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owning agent");
        assertThat(memory.getCopy()).hasSize(2);
    }
}

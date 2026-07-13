package org.sterl.llmpeon.tool.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolLoopRequest;

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
        // GIVEN — config with compactModel="compact-specific-model"
        var config = LlmConfig.builder()
                .model("default-model")
                .compactModel("compact-specific-model")
                .build();
        
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Test context summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("First message"));
        memory.add(AiMessage.from("AI response 1"));
        memory.add(UserMessage.from("Second message"));
        memory.add(AiMessage.from("AI response 2"));
        
        var toolRequest = ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(memory)
                .build();
        
        var subject = new CompactSessionTool();
        subject.withToolRequest(toolRequest);

        // WHEN
        subject.compactSession(null);

        // THEN — ChatRequest should have modelName="compact-specific-model"
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isEqualTo("compact-specific-model");
        
        // AND — memory should be replaced with compacted session message
        var compactedMemory = memory.getCopy();
        assertThat(compactedMemory).hasSize(1);
        assertThat(((UserMessage)compactedMemory.get(0)).singleText())
                .isEqualTo("Session compacted. Resume the task using the preserved context.");
    }
    
    @Test
    void testCompactSessionWithoutCompactModelUsesDefault() {
        // GIVEN — config without compactModel (null)
        var config = LlmConfig.builder()
                .model("default-model")
                .build();
        
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Test context summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.add(AiMessage.from("AI response"));
        
        var toolRequest = ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(memory)
                .build();
        
        var subject = new CompactSessionTool();
        subject.withToolRequest(toolRequest);

        // WHEN
        subject.compactSession(null);

        // THEN — ChatRequest should have no modelName override (null means provider default)
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isNull();
        
        // AND — memory should be replaced with compacted session message
        var compactedMemory = memory.getCopy();
        assertThat(compactedMemory).hasSize(1);
        assertThat(((UserMessage)compactedMemory.get(0)).singleText())
                .isEqualTo("Session compacted. Resume the task using the preserved context.");
    }

    @Test
    void testStandingOrdersSurviveCompaction() {
        // GIVEN — a request carrying a command body as a standing order
        var config = LlmConfig.builder().model("default-model").build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Compressed summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);

        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("/review refactor this class"));
        memory.add(AiMessage.from("I'll review it."));
        memory.add(UserMessage.from("Also check performance"));
        memory.add(AiMessage.from("Will do."));

        var toolRequest = ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(memory)
                .standingOrders(List.of("Review the code and report any issues."))
                .build();

        var subject = new CompactSessionTool();
        subject.withToolRequest(toolRequest);

        // WHEN
        subject.compactSession(null);

        // THEN — the standing order survives and precedes the resume message.
        // Consecutive user messages are merged into one (KV-cache friendly), so the order
        // survives as text inside the single remaining user message.
        var result = memory.getCopy();
        assertThat(result).hasSize(1);
        var text = ChatMessageUtil.toString(result.get(0));
        assertThat(text).contains("Review the code and report any issues.");
        assertThat(text).contains("Session compacted. Resume the task using the preserved context.");
        assertThat(text.indexOf("Review the code")).isLessThan(text.indexOf("Session compacted"));
    }

    @Test
    void testMultipleStandingOrdersSurviveCompaction() {
        // GIVEN — project context, AGENTS.md and a command body as standing orders
        var config = LlmConfig.builder().model("default-model").build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Compressed summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);

        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Do something"));
        memory.add(AiMessage.from("OK"));

        var toolRequest = ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(memory)
                .standingOrders(List.of(
                        "Project: test-project at /path/to/project",
                        "AGENTS.md: Rule 1 — be concise",
                        "/review: Review the code and report any issues."))
                .build();

        var subject = new CompactSessionTool();
        subject.withToolRequest(toolRequest);

        // WHEN
        subject.compactSession(null);

        // THEN — all three orders survive in order, followed by the resume message
        // (merged into the single remaining user message).
        var result = memory.getCopy();
        assertThat(result).hasSize(1);
        var text = ChatMessageUtil.toString(result.get(0));
        assertThat(text).contains("test-project");
        assertThat(text).contains("AGENTS.md");
        assertThat(text).contains("/review");
        assertThat(text).contains("Session compacted");
        assertThat(text.indexOf("test-project"))
                .isLessThan(text.indexOf("AGENTS.md"));
        assertThat(text.indexOf("/review"))
                .isLessThan(text.indexOf("Session compacted"));
    }

    @Test
    void testClearMemoryWithoutStandingOrdersIsIdentity() {
        // GIVEN — a request with no standing orders
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Some message"));
        memory.add(AiMessage.from("Some response"));

        var config = LlmConfig.builder().model("test").build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);

        var toolRequest = ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(memory)
                .build();

        // WHEN
        toolRequest.clearMemory();

        // THEN — memory is cleared with no extra messages added
        assertThat(memory.getCopy()).isEmpty();
    }
}

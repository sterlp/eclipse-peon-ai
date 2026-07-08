package org.sterl.llmpeon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Verifies that each agent resolves its configured model and sets it on ChatRequest.
 */
public class AiServicePerAgentModelTest {

    private StreamMock streamMock;
    private ToolService toolService;

    @BeforeEach
    void beforeEach() {
        toolService = new ToolService();
        streamMock = new StreamMock();
    }

    @Test
    void testDeveloperServiceUsesBaseModel() {
        // GIVEN — config with base model="dev-model" (Dev agent always uses base)
        var config = LlmConfig.builder()
                .model("dev-model")
                .planModel("gpt-4")
                .build();
        
        var cm = streamMock.buildMock(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("done")).build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        var agent = new AiDevAgent(configuredModel, toolService);

        // WHEN
        agent.call("test", null);

        // THEN — ChatRequest should have modelName="dev-model" (base model)
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isEqualTo("dev-model");
    }

    @Test
    void testPlannerServiceUsesPlanModel() {
        // GIVEN — config with planModel="claude-3"
        var config = LlmConfig.builder()
                .model("default-model")
                .planModel("claude-3")
                .build();

        var cm = streamMock.buildMock(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("done")).build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        var agent = new AiPlanAgent(configuredModel, toolService);

        // WHEN
        agent.call("test", null);

        // THEN — ChatRequest should have modelName="claude-3"
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isEqualTo("claude-3");
    }

    @Test
    void testNullAgentModelUsesDefault() {
        // GIVEN — config with planModel=null (no per-agent override)
        var config = LlmConfig.builder()
                .model("default-model")
                .planModel(null)
                .build();

        var cm = streamMock.buildMock(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("done")).build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        var agent = new AiPlanAgent(configuredModel, toolService);

        // WHEN
        agent.call("test", null);

        // THEN — ChatRequest should have no modelName override (null means provider default)
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isNull();
    }
}

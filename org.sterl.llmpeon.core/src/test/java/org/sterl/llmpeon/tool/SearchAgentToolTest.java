package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

class SearchAgentToolTest {

    private StreamMock streamMock;
    private ToolService toolService;

    @BeforeEach
    void beforeEach() {
        toolService = new ToolService();
        streamMock = new StreamMock();
    }
    
    @Test
    void testSearchAgentUsesConfiguredSearchModel() {
        // GIVEN — config with a search record model="search-specific-model"
        var config = LlmConfig.builder()
                .model("default-model")
                .modelConfigs(Map.of(AgentModelConfig.SEARCH,
                        new AgentModelConfig(null, null, "search-specific-model", null, null, null)))
                .build();
        
        var cm = streamMock.buildMock(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("Search done")).build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        
        var toolRequest = ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(new org.sterl.llmpeon.memory.ThreadSafeMemory())
                .build();
        
        var subject = new SearchAgentTool(toolService);
        subject.withToolRequest(toolRequest);

        // WHEN
        subject.searchAgent("Any funny search");

        // THEN — ChatRequest should have modelName="search-specific-model"
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isEqualTo("search-specific-model");
    }
}

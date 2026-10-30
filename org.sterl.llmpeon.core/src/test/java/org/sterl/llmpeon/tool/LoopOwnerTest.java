package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;

/**
 * The contract of {@link LoopOwner}: one source for a loop's name, its back-delegation agent and its
 * config — and the config is never a snapshot (docs/adr/0078-origin-in-core-owner-monitor.md).
 */
class LoopOwnerTest {

    private static LlmConfig baseConfig() {
        return LlmConfig.newConfig(AiProvider.OLLAMA, "base-model", "http://localhost:1");
    }

    @Test
    void configIsReadAtCallTime() {
        // GIVEN a loop whose owner names itself and reads the live SEARCH slot
        var chatModel = new ConfiguredChatModel(baseConfig());
        var request = ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(chatModel)
                .owner(LoopOwner.named("Search", LlmConfig::searchAgentConfig))
                .build();
        assertThat(request.getAgentConfig().getModel())
                .as("blank slot inherits the base model")
                .isEqualTo("base-model");

        // WHEN the same chat model is updated with a slot model (a preferences change, nothing rebuilt)
        chatModel.updateConfig(chatModel.getConfig().withModelConfig(AgentModelConfig.SEARCH,
                AgentModelConfig.empty().withModel("search-model")));

        // THEN the next read sees it — an owner holding an AgentConfig would still answer "base-model"
        assertThat(request.getAgentConfig().getModel()).isEqualTo("search-model");
    }

    @Test
    void namedOwnerHasNoAgent() {
        var base = baseConfig();
        var search = LoopOwner.named("Search", LlmConfig::searchAgentConfig);

        assertThat(search.name()).isEqualTo("Search");
        assertThat(search.agent()).isNull();

        // the bare-builder default keeps the historic dev-slot config and names NOTHING — a literal
        // like "(none)" would become a heading in the plugin and drop the line in the web
        assertThat(LoopOwner.NONE.name()).isNull();
        assertThat(LoopOwner.NONE.agent()).isNull();
        assertThat(LoopOwner.NONE.config(base).getModel()).isEqualTo("base-model");
        assertThat(LoopOwner.NONE.config(base).getId()).isEqualTo(AgentModelConfig.DEV);
    }

    @Test
    void agentOwnerNamesItselfAndStaysDelegatable() {
        var agent = mock(AiAgent.class);
        when(agent.getName()).thenReturn("Dev");

        var owner = LoopOwner.of(agent, LlmConfig::devAgentConfig);

        assertThat(owner.name()).isEqualTo("Dev");
        assertThat(owner.agent()).isSameAs(agent);
    }
}

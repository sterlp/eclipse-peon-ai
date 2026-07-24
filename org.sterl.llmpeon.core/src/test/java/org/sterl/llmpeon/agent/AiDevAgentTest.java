package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.tool.ToolService;

class AiDevAgentTest {

    @Test
    void setAgentModelName_updatesGlobalModel() {
        // GIVEN
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "qwen3", "http://localhost:9999");
        var subject = new AiDevAgent(config.build(), new ToolService());

        // WHEN
        var changed = subject.setAgentModelName("gpt-4");

        // THEN
        assertThat(changed).isTrue();
        assertThat(subject.getAgentModelName()).isEqualTo("gpt-4");
    }

    @Test
    void getAgentModelName_returnsGlobalModel() {
        // GIVEN
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "qwen3", "http://localhost:9999");
        var subject = new AiDevAgent(config.build(), new ToolService());

        // WHEN / THEN
        assertThat(subject.getAgentModelName()).isEqualTo("qwen3");
    }
}

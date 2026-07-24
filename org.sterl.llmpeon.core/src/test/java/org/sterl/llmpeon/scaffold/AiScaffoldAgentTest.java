package org.sterl.llmpeon.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;

class AiScaffoldAgentTest {

    @Test
    void getAgentModelName_returnsNull() throws Exception {
        // GIVEN
        var tmpDir = Files.createTempDirectory("scaffold-test");
        var config = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("qwen3")
                .url("http://localhost:9999")
                .configDir(tmpDir)
                .build();
        var subject = new AiScaffoldAgent(config.build());

        // WHEN / THEN — inherits null from interface default
        assertThat(subject.getAgentModelName()).isNull();
    }

    @Test
    void setAgentModelName_returnsFalse() throws Exception {
        // GIVEN
        var tmpDir = Files.createTempDirectory("scaffold-test");
        var config = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("qwen3")
                .url("http://localhost:9999")
                .configDir(tmpDir)
                .build();
        var subject = new AiScaffoldAgent(config.build());

        // WHEN / THEN — inherits no-op from interface default
        assertThat(subject.setAgentModelName("gpt-4")).isFalse();
    }
}

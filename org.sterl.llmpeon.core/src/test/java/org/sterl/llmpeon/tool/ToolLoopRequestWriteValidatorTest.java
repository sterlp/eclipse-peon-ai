package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;

class ToolLoopRequestWriteValidatorTest {

    @Test
    void defaultsToAllowAllValidator() {
        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        var req = ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .build();
        assertSame(WriteValidator.ALLOW_ALL, req.getWriteValidator());
    }

    @Test
    void carriesTheGivenValidator() {
        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        var req = ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .writeValidator(WriteValidator.DOCS)
                .build();
        assertSame(WriteValidator.DOCS, req.getWriteValidator());
    }
}

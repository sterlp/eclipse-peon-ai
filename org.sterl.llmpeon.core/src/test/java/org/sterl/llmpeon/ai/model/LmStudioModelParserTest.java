package org.sterl.llmpeon.ai.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LmStudioModelParserTest {

    @Test
    void parseLmStudioModels_returnsOnlyToolCapableLlms() throws Exception {
        // GIVEN
        var json = Files.readString(
                Path.of("src/test/resources/lm_studio.json"));

        // WHEN
        var models = AiModelParser.parseLmStudioModels(json);

        // THEN - only the Qwen LLM with trained_for_tool_use=true; embedding model excluded
        assertEquals(1, models.size(), "Only 1 tool-capable LLM expected");
        var model = models.get(0);
        assertEquals("qwen3.5-9b-claude-4.6-opus-reasoning-distilled-v2", model.getId());
        assertEquals("Qwen3.5 9B (262k)", model.getName());
        assertEquals(262144, model.getMaxInputTokens());
        assertTrue(model.getCapabilities().contains(AiCapability.TOOL_CALLING));
        assertTrue(model.getCapabilities().contains(AiCapability.VISION));
        assertTrue(model.supportsToolCalling());
    }

    @Test
    void parseLmStudioModels_excludesEmbeddingModels() throws Exception {
        // GIVEN
        var json = Files.readString(
                Path.of("src/test/resources/lm_studio.json"));

        // WHEN
        var models = AiModelParser.parseLmStudioModels(json);

        // THEN
        assertTrue(models.stream().noneMatch(m -> m.getId().contains("embed")),
                "Embedding models must be excluded");
    }
}

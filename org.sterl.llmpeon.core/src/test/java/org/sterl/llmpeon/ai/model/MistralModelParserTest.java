package org.sterl.llmpeon.ai.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MistralModelParserTest {

    @Test
    void parseMistralModels_onlyReturnsToolCallingModels() throws Exception {
        // GIVEN
        var json = Files.readString(Path.of("src/test/resources/mistral-models.json"));

        // WHEN
        var models = AiModelParser.parseMistralModels(json);

        // THEN
        assertFalse(models.isEmpty(), "Expected at least one tool-callable model");
        for (var model : models) {
            assertTrue(model.supportsToolCalling(),
                    "Every returned model must support tool calling: " + model.getId());
            assertTrue(model.getMaxInputTokens() > 0,
                    "maxInputTokens must be set: " + model.getId());
        }
    }

    @Test
    void parseMistralModels_excludesNonFunctionCallingModels() throws Exception {
        // GIVEN
        var json = Files.readString(Path.of("src/test/resources/mistral-models.json"));

        // WHEN
        var allParsed = AiModelParser.parseMistralModels(json);

        // THEN — JSON contains models with function_calling=false; they must be absent
        // Codestral / FIM models are known non-function-calling entries in the fixture
        assertTrue(allParsed.stream().noneMatch(m -> m.getCapabilities().isEmpty()
                && !m.supportsToolCalling()),
                "No model without TOOL_CALLING should be returned");
    }

    @Test
    void parseMistralModels_visionCapabilityMapped() throws Exception {
        // GIVEN
        var json = Files.readString(Path.of("src/test/resources/mistral-models.json"));

        // WHEN
        var models = AiModelParser.parseMistralModels(json);

        // THEN — at least one model should have VISION (mistral-medium has vision=true)
        assertTrue(models.stream().anyMatch(m -> m.getCapabilities().contains(AiCapability.VISION)),
                "Expected at least one model with VISION capability");
    }
}

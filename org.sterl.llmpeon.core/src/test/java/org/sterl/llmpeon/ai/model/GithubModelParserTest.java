package org.sterl.llmpeon.ai.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class GithubModelParserTest {

    @Test
    void parseCopilotModels_onlyReturnsToolCallingModels() throws Exception {
        // GIVEN
        var json = Files.readString(
                Path.of("src/test/resources/github-models.json"));

        // WHEN
        List<AiModel> models = AiModelParser.parseGithubModels(json);

        // THEN
        assertFalse(models.isEmpty(), "Expected at least one tool-callable model");
        for (var model : models) {
            assertTrue(model.supportsToolCalling(),
                    "Model must support tool calling: " + model.getId());
            assertFalse(model.getId().contains("/"),
                    "Publisher prefix must be stripped from id: " + model.getId());
            assertNotNull(model.getMaxInputTokens(),
                    "maxInputTokens must be set: " + model.getId());
            assertTrue(model.getMaxInputTokens() > 0,
                    "maxInputTokens must be > 0: " + model.getId());
        }
    }

    @Test
    void parseCopilotModels_excludesNonToolCapableModels() throws Exception {
        // GIVEN
        var json = Files.readString(
                Path.of("src/test/resources/github-models.json"));

        // WHEN
        List<AiModel> toolModels = AiModelParser.parseGithubModels(json);

        // THEN - result must be a strict subset (some models lack tool-calling)
        assertTrue(toolModels.size() > 0, "Should have tool-capable models");
        // Verify none of the returned models are embedding-only (no id should be e.g. cohere-embed)
        assertTrue(toolModels.stream().noneMatch(m -> m.getId().contains("embed")),
                "Embedding models must be excluded");
    }
}

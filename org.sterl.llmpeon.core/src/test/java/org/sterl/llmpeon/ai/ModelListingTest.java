package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class ModelListingTest {

    @Test
    void ollamaListsModels() {
        var config = LlmConfig.newConfig("fooo", "http://localhost:11434");
        var models = AiProvider.OLLAMA.listModels(config);
        System.out.println("Ollama models: " + models);
        assertFalse(models.isEmpty(), "Expected at least one Ollama model");
        assertFalse(models.contains("fooo"), "Foo should not be present " + models);
    }
    
    @Test
    void lmStudioListsModels() {
        var config = LlmConfig.newConfig(AiProvider.LM_STUDIO, "fooo", "http://localhost:1234/v1");
        var models = AiProvider.LM_STUDIO.listModels(config);
        System.out.println("LM Studio models: " + models);
        assertFalse(models.isEmpty(), "Expected at least one LM Studio model");
        assertFalse(models.contains("fooo"), "Foo should not be present " + models);
    }

    @Test
    void copilotListsModels() {
        var token = System.getenv("COPILOT_TOKEN");
        assumeTrue(token != null && !token.isBlank(), "COPILOT_TOKEN env var not set — skipping");

        var config = LlmConfig.builder().providerType(AiProvider.GITHUB_COPILOT).model("gpt-4o").build();
        var models = config.listAiModels();
        System.out.println("Copilot models: " + models);
        assertFalse(models.isEmpty(), "Expected at least one Copilot model");
    }

    @Test
    void mistralListsModels() {
        var token = System.getenv("MISTRAL_TOKEN");
        assumeTrue(token != null && !token.isBlank(), "MISTRAL_TOKEN env var not set — skipping");
        var config = LlmConfig.of(AiProvider.MISTRAL).model("mistral-small").apiKey(token).build();
        var models = config.listAiModels();
        System.out.println("Mistral models: " + models);
        assertFalse(models.isEmpty(), "Expected at least one Mistral model");
    }

    @Test
    void unsupportedProviderReturnsFallback() {
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4o").url("https://api.openai.com") .build();
        var models = config.listModels();
        // should return single-element fallback list (the configured model)
        assertFalse(models.isEmpty());
        assert models.contains("gpt-4o");
    }

    @Test
    void noModelConfiguredReturnsEmpty() {
        var config = LlmConfig.of(AiProvider.OPEN_AI).url("https://api.openai.com") .build();
        var models = config.listAiModels();
        assert models.isEmpty() : "Empty model name should yield empty list";
    }
}

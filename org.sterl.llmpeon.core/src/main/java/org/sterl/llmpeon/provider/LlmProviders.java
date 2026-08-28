package org.sterl.llmpeon.provider;

import java.util.Arrays;
import java.util.List;

import org.sterl.llmpeon.ai.AiProvider;

/**
 * Resolves the {@link LlmProvider} for an {@link AiProvider} name (provider.md R1).
 * Providers are stateless singletons (thread-safety invariant).
 */
public final class LlmProviders {

    private static final OllamaProvider OLLAMA = new OllamaProvider();
    private static final OpenAiProvider OPEN_AI = new OpenAiProvider();
    private static final OpenAiOfficialProvider OPEN_AI_OFFICIAL = new OpenAiOfficialProvider();
    private static final LmStudioProvider LM_STUDIO = new LmStudioProvider();
    private static final GithubModelsProvider GITHUB_MODELS = new GithubModelsProvider();
    private static final GithubCopilotProvider GITHUB_COPILOT = new GithubCopilotProvider();
    private static final GoogleGeminiProvider GOOGLE_GEMINI = new GoogleGeminiProvider();
    private static final MistralProvider MISTRAL = new MistralProvider();
    private static final AnthropicProvider ANTHROPIC = new AnthropicProvider();

    private LlmProviders() {
    }

    public static LlmProvider of(AiProvider provider) {
        return switch (provider) {
            case OLLAMA -> OLLAMA;
            case OPEN_AI -> OPEN_AI;
            case OPEN_AI_OFFICIAL -> OPEN_AI_OFFICIAL;
            case LM_STUDIO -> LM_STUDIO;
            case GITHUB_MODELS -> GITHUB_MODELS;
            case GITHUB_COPILOT -> GITHUB_COPILOT;
            case GOOGLE_GEMINI -> GOOGLE_GEMINI;
            case MISTRAL -> MISTRAL;
            case ANTHROPIC -> ANTHROPIC;
        };
    }

    /** All providers in {@link AiProvider#values()} order (for tests/UI). */
    public static List<LlmProvider> all() {
        return Arrays.stream(AiProvider.values()).map(LlmProviders::of).toList();
    }
}

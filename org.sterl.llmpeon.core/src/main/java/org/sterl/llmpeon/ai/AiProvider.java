package org.sterl.llmpeon.ai;

import org.sterl.llmpeon.provider.LlmProviders;

import lombok.extern.slf4j.Slf4j;

/**
 * Name registry for the LLM providers (Preference compatibility: stored as enum name).
 * Behaviour lives in the provider classes — resolve via {@link LlmProviders#of(AiProvider)}.
 */
@Slf4j
public enum AiProvider {

    OLLAMA,
    OPEN_AI,
    OPEN_AI_OFFICIAL,
    LM_STUDIO,
    GOOGLE_GEMINI,
    MISTRAL,
    ANTHROPIC,
    GITHUB_MODELS,
    GITHUB_COPILOT;

    public static AiProvider parse(String string) {
        try {
            return AiProvider.valueOf(string);
        } catch (Exception e) {
            log.warn("AiProvider: unknown " + string + " using " + OLLAMA);
            return OLLAMA;
        }
    }
}

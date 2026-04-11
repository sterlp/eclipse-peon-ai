package org.sterl.llmpeon.ai.voice;

import org.sterl.llmpeon.ai.LlmConfig;

public record VoiceConfig(
    boolean enabled,
    String model,       // e.g. "whisper-1", "whisper"
    String endpoint,    // e.g. "/v1/audio/transcriptions"
    String baseUrl,     // empty = use LlmConfig.url
    String language     // e.g. "en", "de" — empty = auto-detect
) {
    public String resolveBaseUrl(LlmConfig llm) {
        return (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : llm.getUrl();
    }
}

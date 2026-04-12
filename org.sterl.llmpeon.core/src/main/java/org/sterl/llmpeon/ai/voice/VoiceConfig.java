package org.sterl.llmpeon.ai.voice;

import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.StringUtil;

public record VoiceConfig(
    boolean enabled,
    String model,       // e.g. "voxtral-mini-latest", "whisper"
    String endpoint,    // e.g. "/v1/audio/transcriptions"
    String baseUrl,     // empty = unresolved, use LlmConfig.url
    String apiKey,      // empty = unresolved, use LlmConfig.apiKey
    String language,    // e.g. "en", "de" — empty = auto-detect
    String mixer        // mixer/device name — empty = system default
) {
    /**
     * Returns a new VoiceConfig with baseUrl and apiKey filled from llm where blank.
     * Call this in PeonAiService before passing the config to VoiceInputService.
     */
    public VoiceConfig resolve(LlmConfig llm) {
        String url = StringUtil.hasValue(baseUrl) ? baseUrl : llm.getUrl();
        String key = StringUtil.hasValue(apiKey)  ? apiKey  : llm.getApiKey();
        return new VoiceConfig(enabled, model, endpoint, url, key, language, mixer);
    }
}

package org.sterl.llmpeon.ai.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.LlmConfig;

class VoiceConfigTest {

    // GIVEN a standard LLM config with url and apiKey
    private static final LlmConfig LLM = LlmConfig.builder()
            .url("https://api.openai.com")
            .apiKey("llm-key")
            .build();

    @Test
    void shouldUseVoiceUrlWhenSet() {
        // GIVEN a voice config with an explicit base URL
        var voice = voice("https://api.mistral.ai", "");

        // WHEN resolved against the LLM config
        var resolved = voice.resolve(LLM);

        // THEN the voice URL is used
        assertEquals("https://api.mistral.ai", resolved.baseUrl());
    }

    @Test
    void shouldFallBackToLlmUrlWhenVoiceUrlBlank() {
        // GIVEN a voice config with no base URL
        var voice = voice("", "");

        // WHEN resolved against the LLM config
        var resolved = voice.resolve(LLM);

        // THEN the LLM URL is used
        assertEquals("https://api.openai.com", resolved.baseUrl());
    }

    @Test
    void shouldFallBackToLlmUrlWhenVoiceUrlNull() {
        // GIVEN a voice config with a null base URL
        var voice = voice(null, null);

        // WHEN resolved against the LLM config
        var resolved = voice.resolve(LLM);

        // THEN the LLM URL is used
        assertEquals("https://api.openai.com", resolved.baseUrl());
    }

    @Test
    void shouldUseVoiceApiKeyWhenSet() {
        // GIVEN a voice config with an explicit API key
        var voice = voice("", "voice-key");

        // WHEN resolved against the LLM config
        var resolved = voice.resolve(LLM);

        // THEN the voice API key is used
        assertEquals("voice-key", resolved.apiKey());
    }

    @Test
    void shouldFallBackToLlmApiKeyWhenVoiceKeyBlank() {
        // GIVEN a voice config with no API key
        var voice = voice("", "");

        // WHEN resolved against the LLM config
        var resolved = voice.resolve(LLM);

        // THEN the LLM API key is used
        assertEquals("llm-key", resolved.apiKey());
    }

    @Test
    void shouldUseVoiceValuesWhenBothSet() {
        // GIVEN a voice config with both URL and API key set
        var voice = voice("https://voice.host", "voice-key");

        // WHEN resolved against the LLM config
        var resolved = voice.resolve(LLM);

        // THEN both voice values are used
        assertEquals("https://voice.host", resolved.baseUrl());
        assertEquals("voice-key", resolved.apiKey());
    }

    @Test
    void shouldNotThrowWhenLlmFieldsAreNull() {
        // GIVEN a voice config with no values and an LLM config with no url or apiKey
        var llm = LlmConfig.builder().build();
        var voice = voice(null, null);

        // WHEN resolved
        var resolved = voice.resolve(llm);

        // THEN resolved values are null without NPE
        assertNull(resolved.baseUrl());
        assertNull(resolved.apiKey());
    }

    // --- helpers ---

    private VoiceConfig voice(String baseUrl, String apiKey) {
        return new VoiceConfig(true, "voxtral-mini-latest", "/v1/audio/transcriptions", baseUrl, apiKey, null, null);
    }
}

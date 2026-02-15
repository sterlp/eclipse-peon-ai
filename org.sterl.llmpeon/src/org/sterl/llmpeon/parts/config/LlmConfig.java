package org.sterl.llmpeon.parts.config;

public record LlmConfig(
    String providerType,
    String model,
    String url,
    int tokenWindow,
    boolean thinkingEnabled,
    String apiKey
) {}

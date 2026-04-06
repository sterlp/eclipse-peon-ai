package org.sterl.llmpeon.ai.model;

import java.util.Set;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class AiModel {

    String id;
    String name;
    @Default
    Integer maxInputTokens = null;
    @Default
    Set<AiCapability> capabilities = Set.of();

    public boolean supportsToolCalling() {
        return capabilities.contains(AiCapability.TOOL_CALLING);
    }
}

package org.sterl.llmpeon.parts.tools.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkspaceGuideline(
        @JsonProperty("text")     String text,
        @JsonProperty("createdAt") String createdAt
) {
    @JsonCreator
    public WorkspaceGuideline {}
}

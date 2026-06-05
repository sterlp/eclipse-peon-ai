package org.sterl.llmpeon.mock.model;

import java.util.List;

public record CapturedTool(
        String name,
        String description,
        List<String> requiredParams,
        List<String> allParams) {

    public boolean hasParam(String paramName) {
        return allParams.contains(paramName);
    }
    public boolean isRequired(String paramName) {
        return requiredParams.contains(paramName);
    }
}

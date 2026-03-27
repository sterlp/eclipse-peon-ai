package org.sterl.llmpeon.tool.model;

public record SimpleMessage(Type role, String message) {
    public enum Type {
        AI,
        USER,
        PROBLEM,
        TOOL,
        THINK,
    }
}

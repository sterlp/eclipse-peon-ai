package org.sterl.llmpeon.ai.model;

public enum AiCapability {
    TOOL_CALLING, STREAMING, AGENTS, REASONING, VISION;

    public static AiCapability parse(String s) {
        if (s == null) return null;
        s = s.toLowerCase();
        if (s.contains("tool"))   return TOOL_CALLING;
        if (s.contains("stream")) return STREAMING;
        if (s.contains("agent"))  return AGENTS;
        if (s.contains("reason")) return REASONING;
        if (s.contains("vision")) return VISION;
        return null;
    }
}

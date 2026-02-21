package org.sterl.llmpeon.ai;

public enum AiProvider {
    OLLAMA,
    OPEN_AI,
    GOOGLE_GEMINI;

    public static AiProvider parse(String string) {
        try {
            return AiProvider.valueOf(string);
        } catch (Exception e) {
            System.err.println("AiProvider: unknown " + string + " using " + OLLAMA);
            return OLLAMA;
        }
    }
}

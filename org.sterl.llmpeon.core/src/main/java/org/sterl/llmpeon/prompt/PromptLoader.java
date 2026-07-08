package org.sterl.llmpeon.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Load build in text prompts.
 * 
 * Better use the {@link PromptYmlParser}
 */
public class PromptLoader {

    private static final String DEFAULT = load("default.txt");

    public static String load(String filename) {
        
        var is = PromptLoader.class.getResourceAsStream("/org/sterl/llmpeon/prompts/" + filename);
        if (is == null) is = PromptLoader.class.getResourceAsStream("/" + filename);
        if (is == null) throw new IllegalStateException("Prompt not found: " + filename);

        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + filename, e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {}
        }
    }

    public static String loadWithDefault(String filename) {
        return withDefault(load(filename));
    }

    /** Prepends the shared default prompt to the given body (e.g. a custom agent's markdown). */
    public static String withDefault(String body) {
        return DEFAULT + "\n\n" + (body == null ? "" : body);
    }
}

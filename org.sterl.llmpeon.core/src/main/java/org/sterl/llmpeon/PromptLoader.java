package org.sterl.llmpeon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        return DEFAULT + "\n\n" + load(filename);
    }
}

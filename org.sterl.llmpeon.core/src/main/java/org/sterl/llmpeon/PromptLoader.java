package org.sterl.llmpeon;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PromptLoader {

    private static final String DEFAULT = load("default.txt");

    public static String load(String filename) {
        String path = "/org/sterl/llmpeon/prompts/" + filename;
        try (InputStream is = PromptLoader.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Prompt not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + path, e);
        }
    }

    public static String loadWithDefault(String filename) {
        return load(filename) + "\n" + DEFAULT;
    }
}

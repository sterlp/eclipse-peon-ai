package org.sterl.llmpeon.shared;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.sterl.llmpeon.shared.model.SimplePromptFile;

public class PromptYmlParser {
    
    public static String defaultSkillName(Path skillFile) {
        String fileName = skillFile.getFileName().toString();
        if ("SKILL.md".equalsIgnoreCase(fileName)) {
            return skillFile.getParent().getFileName().toString();
        }
        return fileName.substring(0, fileName.length() - 3);
    }

    public static SimplePromptFile parseYml(Path commandFile) throws IOException {
        if (!Files.isRegularFile(commandFile)) return null;
        var fileName = commandFile.getFileName().toString();
        var fileNameLower = fileName.toLowerCase();
        if (!fileNameLower.endsWith(".md")) return null;
       
        var frontmatter = parseFrontmatter(commandFile);

        String name = frontmatter.getOrDefault("name", defaultSkillName(commandFile));
        String description = frontmatter.getOrDefault("description", null);

        return new SimplePromptFile(name, description, commandFile);
    }

    private static Map<String, String> parseFrontmatter(Path file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;

            // Skip blank leading lines
            while ((line = reader.readLine()) != null && line.isBlank()) {}

            // Frontmatter is optional — if no opening ---, return empty map
            if (line == null || !"---".equals(line.trim())) return result;

            while ((line = reader.readLine()) != null && !"---".equals(line.trim())) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim();
                    String value = stripYamlValue(line.substring(colon + 1));
                    if (value != null) result.put(key, value);
                }
            }
        }
        return result;
    }

    public static String stripYamlValue(String value) {
        if (value == null) return null;
        value = value.strip();
        if (value.length() > 2 && value.charAt(0) == value.charAt(value.length() - 1)
                && (value.startsWith("\"") || value.startsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }
}
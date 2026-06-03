package org.sterl.llmpeon.shared;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.sterl.llmpeon.command.CommandPromptFile;
import org.sterl.llmpeon.skill.SkillPromptFile;

public class PromptYmlParser {

    public static SkillPromptFile parseSkill(Path skillFile) throws IOException {
        if (!Files.isRegularFile(skillFile)) return null;

        String fileName = skillFile.getFileName().toString();
        String defaultName;
        if ("SKILL.md".equalsIgnoreCase(fileName)) {
            defaultName = skillFile.getParent().getFileName().toString();
        } else if (fileName.endsWith(".md")) {
            defaultName = fileName.substring(0, fileName.length() - 3);
        } else {
            return null;
        }

        Map<String, String> frontmatter = parseFrontmatter(skillFile);
        String name = frontmatter.getOrDefault("name", defaultName);
        String description = frontmatter.get("description");

        if (name != null && description != null) {
            return new SkillPromptFile(name, description, skillFile);
        }
        return null;
    }

    public static CommandPromptFile parseCommand(Path commandFile) throws IOException {
        if (!Files.isRegularFile(commandFile)) return null;
        var fileName = commandFile.getFileName().toString();
        if (!fileName.endsWith(".md")) return null;
        var defaultName = fileName.substring(0, fileName.length() - 3);
        if (defaultName.isBlank()) return null;

        Map<String, String> frontmatter = parseFrontmatter(commandFile);
        String name = frontmatter.getOrDefault("name", defaultName);
        String description = frontmatter.get("description");

        return new CommandPromptFile(name, description, commandFile);
    }

    static Map<String, String> parseFrontmatter(Path file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String first = reader.readLine();
            while (first != null && first.isBlank()) first = reader.readLine();
            if (first == null || !"---".equals(first.trim())) return result;

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if ("---".equals(trimmed)) break;

                int colon = trimmed.indexOf(':');
                if (colon > 0) {
                    String key = trimmed.substring(0, colon).trim();
                    String value = stripYamlValue(trimmed.substring(colon + 1));
                    if (value != null) {
                        result.put(key, value);
                    }
                }
            }
        }
        return result;
    }

    public static String stripYamlValue(String value) {
        if (value == null) return null;
        value = value.strip();
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                 || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    public static String stripFrontmatter(String raw) {
        if (raw == null) return "";
        int start = 0;
        while (start < raw.length() && (raw.charAt(start) == '\n' || raw.charAt(start) == '\r')) start++;
        if (start >= raw.length() || !raw.startsWith("---", start)) return raw;
        int idx = start + 3;
        while (idx < raw.length() && raw.charAt(idx) != '\n') idx++;
        if (idx >= raw.length()) return raw;
        idx++;
        while (idx < raw.length()) {
            int lineEnd = raw.indexOf('\n', idx);
            String line = lineEnd < 0 ? raw.substring(idx) : raw.substring(idx, lineEnd);
            if (line.strip().equals("---")) {
                return lineEnd < 0 ? "" : raw.substring(lineEnd + 1);
            }
            if (lineEnd < 0) break;
            idx = lineEnd + 1;
        }
        return raw;
    }
}
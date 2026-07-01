package org.sterl.llmpeon.shared;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sterl.llmpeon.shared.model.SimplePromptFile;

public class PromptYmlParser {

    public static String defaultSkillName(Path skillFile) {
        String fileName = skillFile.getFileName().toString();
        if ("SKILL.md".equalsIgnoreCase(fileName) || "AGENT.md".equalsIgnoreCase(fileName)) {
            return skillFile.getParent().getFileName().toString();
        }
        return fileName.substring(0, fileName.length() - 3);
    }

    public static SimplePromptFile parseYml(Path commandFile) throws IOException {
        if (!Files.isRegularFile(commandFile)) return null;
        var fileName = commandFile.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".md")) return null;

        var frontmatter = parseFrontmatter(commandFile);

        String name = firstOrDefault(frontmatter, "name", defaultSkillName(commandFile));
        String description = firstOrDefault(frontmatter, "description", null);

        return new SimplePromptFile(name, description, commandFile);
    }

    /**
     * Sets (or inserts) a scalar {@code key: value} line in the YAML frontmatter of the file. If
     * the key exists it is overwritten, otherwise it is appended just before the closing
     * {@code ---}. If the file has no frontmatter block one is prepended. The markdown body is left
     * untouched.
     */
    public static void setFrontmatterValue(Path file, String key, String value) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(file));
        String newLine = key + ": " + value;

        int first = 0;
        while (first < lines.size() && lines.get(first).isBlank()) first++;

        if (first < lines.size() && "---".equals(lines.get(first).trim())) {
            int start = first + 1;
            int end = -1;
            for (int j = start; j < lines.size(); j++) {
                if ("---".equals(lines.get(j).trim())) { end = j; break; }
            }
            if (end < 0) {
                prependBlock(lines, newLine);
            } else {
                int keyLine = -1;
                for (int j = start; j < end; j++) {
                    int colon = lines.get(j).indexOf(':');
                    if (colon > 0 && lines.get(j).substring(0, colon).trim().equalsIgnoreCase(key)) {
                        keyLine = j;
                        break;
                    }
                }
                if (keyLine >= 0) lines.set(keyLine, newLine);
                else lines.add(end, newLine);
            }
        } else {
            prependBlock(lines, newLine);
        }
        Files.write(file, lines);
    }

    private static void prependBlock(List<String> lines, String newLine) {
        lines.add(0, "---");
        lines.add(1, newLine);
        lines.add(2, "---");
    }

    /**
     * Reads the leading {@code ---} frontmatter block into a map of key to value list. Scalar
     * {@code key: value} lines become a single-element list; a {@code key:} with an empty value
     * followed by indented {@code - item} lines becomes a multi-element list. Keys are lower-cased.
     * Returns an empty map when no frontmatter is present.
     */
    public static Map<String, List<String>> parseFrontmatter(Path file) throws IOException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;

            // Skip blank leading lines
            while ((line = reader.readLine()) != null && line.isBlank()) {}

            // Frontmatter is optional — if no opening ---, return empty map
            if (line == null || !"---".equals(line.trim())) return result;

            String currentListKey = null;
            while ((line = reader.readLine()) != null && !"---".equals(line.trim())) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.startsWith("-") && currentListKey != null) {
                    String item = stripYamlValue(trimmed.substring(1));
                    if (item != null) result.get(currentListKey).add(item);
                    continue;
                }

                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = stripYamlValue(line.substring(colon + 1));
                if (value == null) {
                    result.computeIfAbsent(key, k -> new ArrayList<>());
                    currentListKey = key;
                } else {
                    var list = new ArrayList<String>();
                    list.add(value);
                    result.put(key, list);
                    currentListKey = null;
                }
            }
        }
        return result;
    }

    public static String firstOrDefault(Map<String, List<String>> frontmatter, String key, String def) {
        var values = frontmatter.get(key);
        return (values == null || values.isEmpty()) ? def : values.get(0);
    }

    /**
     * Turns raw frontmatter list values into a flat allowlist. {@code null} (field absent) is
     * preserved to signal "all". Inline CSV entries ({@code tools: a, b}) are split on commas so
     * both list styles work.
     */
    public static List<String> toolAllowlist(List<String> raw) {
        if (raw == null) return null;
        List<String> out = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null) continue;
            for (String part : entry.split(",")) {
                String token = part.trim();
                if (!token.isEmpty()) out.add(token);
            }
        }
        return out;
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

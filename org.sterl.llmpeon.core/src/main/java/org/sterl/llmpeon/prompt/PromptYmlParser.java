package org.sterl.llmpeon.prompt;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.llmpeon.shared.StringUtil;

public class PromptYmlParser {

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";

    public static String defaultSkillName(Path skillFile) {
        String fileName = skillFile.getFileName().toString();
        if ("SKILL.md".equalsIgnoreCase(fileName)
                || "AGENT.md".equalsIgnoreCase(fileName)) {
            return skillFile.getParent().getFileName().toString();
        }
        return fileName.substring(0, fileName.length() - 3);
    }

    /**
     * Reads the leading {@code ---} frontmatter block into a map of key to
     * value list. Scalar {@code key: value} lines become a single-element list;
     * a {@code key:} with an empty value followed by indented {@code - item}
     * lines becomes a multi-element list. Keys are lower-cased. Returns an
     * empty map when no frontmatter is present.
     */
    public static SimplePromptFile parseYml(Path commandFile)
            throws IOException {
        if (!Files.isRegularFile(commandFile)) return null;

        var fileName = commandFile.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".md")) return null;

        try (BufferedReader reader = Files.newBufferedReader(commandFile)) {
            var result = parseFrontmatterAndBody(reader, commandFile);
            if (result.firstOrDefault(NAME, null) == null) result.setValue(NAME, defaultSkillName(commandFile));
            return result;
        }
    }

    private static SimplePromptFile parseFrontmatterAndBody(
            BufferedReader reader, Path commandFile) throws IOException {
        var result = new LinkedHashMap<String, List<String>>();
        String line;

        while ((line = reader.readLine()) != null && line.isBlank()) {
        }

        if (line == null || !"---".equals(line.trim())) {
            String body = line == null ? "" : line;
            var remaining = readRemaining(reader);
            if (!remaining.isEmpty()) body += System.lineSeparator() + remaining;
            return new SimplePromptFile(result, body, commandFile);
        }

        String currentListKey = null;
        while ((line = reader.readLine()) != null
                && !"---".equals(line.trim())) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("-") && currentListKey != null) {
                String item = stripYamlValue(trimmed.substring(1));
                if (item != null) result.get(currentListKey).add(item);
                continue;
            }

            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim()
                    .toLowerCase(Locale.ROOT);
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

        String body = readRemaining(reader);
        return new SimplePromptFile(result, body, commandFile);
    }

    private static String readRemaining(BufferedReader reader)
            throws IOException {
        var sb = new StringBuilder();
        String line;
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (first) first = false;
            else sb.append(System.lineSeparator());
            sb.append(line);
        }
        return sb.toString();
    }

    @Nullable
    public static String getName(
            @NonNull Map<String, List<String>> frontmatter) {
        return firstOrDefault(frontmatter, NAME, null);
    }
    @Nullable
    public static String getDescription(
            @NonNull Map<String, List<String>> frontmatter) {
        return firstOrDefault(frontmatter, DESCRIPTION, null);
    }

    public static String firstOrDefault(
            @NonNull Map<String, List<String>> frontmatter, @NonNull String key,
            String def) {
        var values = getValue(frontmatter, key);
        return (values == null || values.isEmpty()) ? def : values.get(0);
    }

    @Nullable
    public static List<String> getValue(
            @NonNull Map<String, List<String>> frontmatter,
            @NonNull String key) {
        var values = frontmatter.get(key);
        if (values == null) values = frontmatter.get(key.replace("-", ""));
        return values;
    }

    /**
     * Turns raw frontmatter list values into a flat allowlist. {@code null}
     * (field absent) is preserved to signal "all". Inline CSV entries
     * ({@code tools: a, b}) are split on commas so both list styles work.
     */
    public static List<String> toolAllowlist(List<String> raw) {
        if (raw == null) return null;
        var out = new LinkedList<String>();
        for (var entry : raw) {
            if (entry == null) continue;
            for (String part : entry.split(",")) {
                var token = StringUtil.strip(part);
                if (token != null && !token.isEmpty()) out.add(token);
            }
        }
        return out;
    }

    static String stripYamlValue(String value) {
        if (value == null) return null;
        value = value.strip();
        if (value.length() > 2
                && value.charAt(0) == value.charAt(value.length() - 1)
                && (value.startsWith("\"") || value.startsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }
}

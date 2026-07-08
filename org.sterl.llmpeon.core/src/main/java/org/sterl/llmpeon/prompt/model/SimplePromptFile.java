package org.sterl.llmpeon.prompt.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.prompt.PromptYmlParser;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Setter
@Getter
public class SimplePromptFile {

    protected final Map<String, List<String>> frontmatter;
    protected final String body;
    /** only not set in tests */
    protected final Path promptFile;
    @Setter
    @Getter
    protected volatile boolean enabled = true;

    public String getName() {
        return PromptYmlParser.getName(frontmatter);
    }

    public String firstOrDefault(String name, String def) {
        return PromptYmlParser.firstOrDefault(frontmatter, name, def);
    }
    public Double firstOrDefaultNumber(@NonNull String key, Double def) {
        var result = firstOrDefault(key, null);
        if (result == null)
            return def;
        return Double.parseDouble(result);
    }
    public @Nullable List<String> get(String name) {
        return PromptYmlParser.getValue(frontmatter, name);
    }

    public boolean isTrue(@NonNull String name) {
        var val = firstOrDefault(name, null);
        return "true".equalsIgnoreCase(val);
    }

    public String getDescription() {
        return PromptYmlParser.getDescription(frontmatter);
    }

    private String readFullContent() {
        try {
            return Files.readString(promptFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + promptFile, e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + getName() + ", enabled: "
                + enabled + ", promptFile: " + promptFile + "]";
    }

    public void setValue(String name, String value) {
        frontmatter.put(name, Arrays.asList(value));
    }
    public void set(String name, List<String> values) {
        frontmatter.put(name, values);
    }
    
    public void save() throws IOException {
        if (promptFile == null) {
            throw new IllegalStateException("Cannot save: promptFile is not set (test instance?)");
        }
        Files.writeString(promptFile, render(), StandardCharsets.UTF_8);
    }

    private String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        for (var entry : frontmatter.entrySet()) {
            var key = entry.getKey();
            var values = entry.getValue();
            if (values == null || values.isEmpty()) {
                sb.append(key).append(":\n");
            } else if (values.size() == 1) {
                sb.append(key).append(": ").append(values.get(0)).append("\n");
            } else {
                sb.append(key).append(":\n");
                for (String v : values) {
                    sb.append("  - ").append(v).append("\n");
                }
            }
        }
        sb.append("---\n");
        sb.append(body);
        return sb.toString();
    }
}
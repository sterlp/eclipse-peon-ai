package org.sterl.llmpeon.shared.model;

import java.nio.file.Files;
import java.nio.file.Path;

import lombok.Builder.Default;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@RequiredArgsConstructor
@SuperBuilder(toBuilder = true)
public class SimplePromptFile {

    protected final String name;
    protected final String description;
    protected final Path promptFile;
    @Default
    protected volatile boolean enabled = true;
    
    private String shortInfo; // Lazy-init with double-checked locking for thread safety
    
    public String buildShortInfo() {
        String result = shortInfo;
        if (result == null) {
            synchronized (this) {
                result = shortInfo;
                if (result == null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("---\nname: ").append(name)
                      .append("\ndescription: ").append(description);
                    result = sb.toString();
                    shortInfo = result;
                }
            }
        }
        return result;
    }

    public String name() {
        return getName();
    }

    public String description() {
        return getDescription();
    }

    public Path promptFile() {
        return getPromptFile();
    }

    public String readFullContent() {
        try {
            return Files.readString(promptFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + promptFile, e);
        }
    }
    
    public boolean isEnabled() {
        return enabled && Files.exists(promptFile);
    }

    public String readBody() {
        return stripFrontmatter(readFullContent());
    }

    
    public static String stripFrontmatter(String raw) {
        if (raw == null || raw.isBlank()) return raw;

        raw = raw.strip();
        if (!raw.startsWith("---")) return raw;

        int first = raw.indexOf("---");
        if (first == -1) return raw;

        int second = raw.indexOf("\n---", first + 3); // Second --- must be after newline
        if (second == -1) return raw;

        int afterSecond = raw.indexOf('\n', second + 1);
        return afterSecond == -1 ? "" : raw.substring(afterSecond).trim();
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + name + ", promptFile: " + promptFile + "]";
    }
}
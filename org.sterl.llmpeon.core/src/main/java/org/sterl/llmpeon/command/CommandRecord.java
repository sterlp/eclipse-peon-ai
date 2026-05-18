package org.sterl.llmpeon.command;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A single user-defined slash command.
 *
 * <p>Loaded from a {@code <name>.md} file inside the configured commands directory.
 * The filename (without the {@code .md} extension) becomes the {@link #name()}.
 * Optional YAML frontmatter with a {@code description:} field is supported and
 * surfaced to the user in the slash menu; if absent, {@link #description()} is {@code null}.</p>
 */
public record CommandRecord(String name, String description, Path commandFile) {

    public String readFullContent() {
        try {
            return Files.readString(commandFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + commandFile, e);
        }
    }

    /**
     * Returns the markdown body (the content after the optional YAML frontmatter).
     * If the file has no frontmatter, the full content is returned unchanged.
     */
    public String readBody() {
        var raw = readFullContent();
        return stripFrontmatter(raw);
    }

    static String stripFrontmatter(String raw) {
        if (raw == null) return "";
        // Trim leading whitespace/newlines before frontmatter detection.
        int start = 0;
        while (start < raw.length() && (raw.charAt(start) == '\n' || raw.charAt(start) == '\r')) start++;
        if (start >= raw.length() || !raw.startsWith("---", start)) return raw;
        // Locate the closing '---' on its own line.
        int idx = start + 3;
        // Skip the newline after the opening fence.
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

    public String shortDescription() {
        return "Command[name=" + name + (description == null ? "" : ", description=" + description) + "]";
    }
}

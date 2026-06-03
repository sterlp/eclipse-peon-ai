package org.sterl.llmpeon.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public class FileUtils {
    
    /**
     * Normalize given path to an OS neutral path using <code>/</code> for path separations, like Eclipse.
     */
    public static String normalizePath(String value) {
        if (value == null || value.length() == 0) return value;
        return value.replace('\\', '/');
    }
    
    public static Path toPath(String value) {
        if (value == null) return null;
        return Path.of(value).normalize();
    }

    public static String readString(Path filePath) {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + filePath, e);
        }
    }
    
    public static Optional<Path> findFirst(Path folder, String name) {
        if (name == null) return Optional.empty();
        try {
            return Files.walk(folder)
                .filter(p -> p.toString().toLowerCase().contains(name.toLowerCase()))
                .findFirst();
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk " + folder);
        }
    }

    /**
     * Resolves {@code path} against {@code base}: if {@code path} is absolute it is
     * returned normalized; otherwise it is resolved relative to {@code base}.
     * Returns {@code null} when {@code path} is {@code null}.
     */
    public static Path resolve(Path base, String path) {
        if (path == null) return null;
        Path p = Path.of(path);
        if (Files.exists(p) && p.isAbsolute()) return p.normalize();
        return base.resolve(p).normalize();
    }

    /**
     * Replaces exactly one occurrence of {@code oldStr} with {@code newStr} inside {@code content}.
     * Throws {@link IllegalArgumentException} if there are zero or more than one match.
     */
    public static String applyEdit(String filePath, String content, String oldStr, String newStr) {
        var edit = findEdit(filePath, content, oldStr, newStr);
        return content.replace(edit.oldStr(), edit.newStr());
    }

    private static Edit findEdit(String filePath, String content, String oldStr, String newStr) {
        int count = countMatches(content, oldStr);
        String lineEnding = dominantLineEnding(content);
        if (count == 1) return new Edit(oldStr, normalizeLineEndings(newStr, lineEnding));

        if (count == 0) {
            String normalizedOld = normalizeLineEndings(oldStr, lineEnding);
            if (!oldStr.equals(normalizedOld)) {
                int normalizedCount = countMatches(content, normalizedOld);
                if (normalizedCount == 1) {
                    return new Edit(normalizedOld, normalizeLineEndings(newStr, lineEnding));
                }
                if (normalizedCount > 1) {
                    throw tooManyMatches(filePath, normalizedCount);
                }
            }
            throw notFound(filePath, oldStr, hasLineEndingMismatch(content, oldStr));
        }

        throw tooManyMatches(filePath, count);
    }

    private static int countMatches(String content, String oldStr) {
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(oldStr, idx)) != -1) {
            count++;
            idx += oldStr.length();
        }
        return count;
    }

    public static String dominantLineEnding(String content) {
        return content.contains("\r\n") ? "\r\n" : "\n";
    }

    private static String normalizeLineEndings(String value, String lineEnding) {
        return value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", lineEnding);
    }

    private static boolean hasLineEndingMismatch(String content, String oldStr) {
        return content.contains("\r\n") && oldStr.contains("\n") && !oldStr.contains("\r\n")
                || !content.contains("\r\n") && oldStr.contains("\r\n");
    }

    private static IllegalArgumentException notFound(String filePath, String oldStr, boolean lineEndingMismatch) {
        String suffix = lineEndingMismatch
                ? " The file and old_string use different line endings; line endings were normalized but the text still did not match."
                : "";
        return new IllegalArgumentException(
                "old_string: '" 
                        + oldStr
                        + "' not found in " + filePath + ". Read the file first to verify the exact content."
                        + suffix);
    }

    private static IllegalArgumentException tooManyMatches(String filePath, int count) {
        return new IllegalArgumentException(
                "old_string found " + count + " times in " + filePath
                        + ". Include more surrounding context to make the match unique.");
    }

    private record Edit(String oldStr, String newStr) {}

    public static void writeString(Path f, String content) {
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + f, e);
        }
    }
}

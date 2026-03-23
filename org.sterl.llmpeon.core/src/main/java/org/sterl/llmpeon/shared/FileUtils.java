package org.sterl.llmpeon.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public class FileUtils {
    
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
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(oldStr, idx)) != -1) {
            count++;
            idx += oldStr.length();
        }
        if (count == 0) {
            throw new IllegalArgumentException(
                    "old_string not found in " + filePath + ". Read the file first to verify the exact content.");
        }
        if (count > 1) {
            throw new IllegalArgumentException(
                    "old_string found " + count + " times in " + filePath
                            + ". Include more surrounding context to make the match unique.");
        }
        return content.replace(oldStr, newStr);
    }

    public static void writeString(Path f, String content) {
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + f, e);
        }
    }
}

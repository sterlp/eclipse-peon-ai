package org.sterl.llmpeon.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtils {
    
    /**
     * Normalize given path to an OS neutral path using <code>/</code> for path separations, like Eclipse.
     */
    public static String normalizePath(String value) {
        if (value == null || value.length() == 0) return value;
        return value.replace('\\', '/');
    }
    
    public static String makeReltive(String value) {
        value = normalizePath(value);
        value = value.replace("../", ""); // /../ -> / 
        if (value.startsWith("/")) value = value.substring(1);
        return value;
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
        if (oldStr.equals(newStr)) throw new IllegalArgumentException("Old and new string is the same.");

        String fileLineEnding = dominantLineEnding(content);
        // fix endings
        var oldE = dominantLineEnding(oldStr);
        if (oldE != fileLineEnding) {
            oldStr = oldStr.replace(oldE, fileLineEnding);
            log.warn("Bad file endings by LLM for old string in {} -- expected {} but got {}", filePath, ending(fileLineEnding), ending(oldE));
        }
        var newE = dominantLineEnding(newStr);
        if (newE != fileLineEnding) {
            newStr = newStr.replace(newE, fileLineEnding);
            log.warn("Bad file endings by LLM for new string in {} -- expected {} but got {}", filePath, ending(fileLineEnding), ending(newE));
        }

        if (content.contains(oldStr)) {
            return content.replace(oldStr, newStr);
        } else {
            throw new IllegalArgumentException(
                    "Bad replace in file: " + filePath + " oldStr: " + fileLineEnding
                            + oldStr + fileLineEnding + fileLineEnding
                            + "=> not found! Please check your replace. Current content of the file:" + fileLineEnding
                            + content);
        }
    }

    public static String dominantLineEnding(String content) {
        int crlf = 0, lf = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\r') {
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    crlf++;
                    i++;
                }
            } else if (c == '\n') {
                lf++;
            }
        }
        return crlf > lf ? "\r\n" : "\n";
    }

    public static void writeString(Path f, String content) {
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + f, e);
        }
    }
    
    private static String ending(String v) {
        if (v == "\n") return "'n'";
        if (v == "\r\n") return "'rn'";
        return "<unknown " + v + ">";
    }
}

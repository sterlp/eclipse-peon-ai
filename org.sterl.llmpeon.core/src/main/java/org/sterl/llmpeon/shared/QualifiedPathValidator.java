package org.sterl.llmpeon.shared;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * R5 (docs/file-copy-tool.md): copy/rename paths must be fully qualified —
 * disk paths absolute, eclipse paths workspace-qualified as {@code /project/path}.
 * One implementation for both tool families; the eclipse project check is
 * supplied by the caller because project existence is an environment concern.
 */
public final class QualifiedPathValidator {

    private QualifiedPathValidator() {
    }

    public static void requireQualifiedDisk(String toolName, String path) {
        if (!Path.of(path).isAbsolute())
            throw new IllegalArgumentException(contract(toolName, path));
    }

    public static void requireQualifiedEclipse(String toolName, String path, Predicate<String> projectExists) {
        var segments = path.split("[/\\\\]");
        // leading '/' required, so real segments start at index 1; need project + at least one more
        if (!path.startsWith("/") || segments.length < 3 || !projectExists.test(segments[1]))
            throw new IllegalArgumentException(contract(toolName, path));
    }

    private static String contract(String toolName, String path) {
        return toolName + " paths must be fully qualified — disk: absolute, eclipse: /project/path (got: " + path + ")";
    }
}

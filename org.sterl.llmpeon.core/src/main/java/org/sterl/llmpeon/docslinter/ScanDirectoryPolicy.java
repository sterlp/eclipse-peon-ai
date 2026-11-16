package org.sterl.llmpeon.docslinter;

import java.nio.file.Path;
import java.util.Set;

/**
 * R-DL-28 (docs/docs-linter.md)
 */
final class ScanDirectoryPolicy {

    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", "target", "bin", "build", "out", ".git", ".settings", ".gradle");

    static boolean isExcluded(Path directory) {
        Path name = directory.getFileName();
        return name != null && EXCLUDED.contains(name.toString());
    }

    private ScanDirectoryPolicy() {
    }
}

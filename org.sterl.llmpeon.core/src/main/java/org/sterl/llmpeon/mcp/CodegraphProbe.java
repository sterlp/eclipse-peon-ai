package org.sterl.llmpeon.mcp;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Detects the Codegraph binary and a project's Codegraph index — pure path-based and
 * read-only: no process is started, nothing is written (install/init are user tasks, R-CG-9).
 */
public interface CodegraphProbe {

    /**
     * Finds the Codegraph binary: the default install {@code ~/.local/bin/codegraph} first,
     * then a PATH scan. Empty when no regular file is found.
     */
    Optional<Path> findBinary();

    /**
     * Whether the project has a Codegraph index — {@code .codegraph/codegraph.db} must be a
     * regular file. A bare {@code .codegraph/} directory (init transient) is negative (R-CG-2).
     */
    boolean hasIndex(Path projectDiskPath);
}

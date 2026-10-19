package org.sterl.llmpeon.mcp;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Detects the Codegraph binary and a project's Codegraph index — pure path-based and
 * read-only: no process is started, nothing is written (install/init are user tasks, R-CG-9).
 */
public interface CodegraphProbe {

    /**
     * Finds the Codegraph binary as a START HINT, not a gate (R-CG-2): the default install
     * {@code ~/.local/bin/codegraph} first, then the PATH entries of the user tool environment
     * (the same one {@code ShellTool} uses — a Dock-started Eclipse misses Homebrew/npm in
     * {@code System.getenv("PATH")}). Empty = the server is still started via the process PATH.
     */
    Optional<Path> findBinary();

    /**
     * Finds the directory holding the project's Codegraph index (R-CG-2):
     * {@code .codegraph/codegraph.db} as a regular file in the project folder, or — when
     * absent there — in an ANCESTOR up to the Git root (module projects often index one level
     * up). The found directory is the server's workDir. A bare {@code .codegraph/} directory
     * (init transient) is negative.
     */
    Optional<Path> findIndexWorkDir(Path projectDiskPath);
}

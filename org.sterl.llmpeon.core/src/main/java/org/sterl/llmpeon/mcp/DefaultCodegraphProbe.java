package org.sterl.llmpeon.mcp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Path-based {@link CodegraphProbe}: checks {@code ~/.local/bin/codegraph}, then the PATH
 * entries, for a regular file named {@code codegraph} (macOS/Linux — Windows shims are out
 * of scope for v1). Read-only: never starts a process, never writes (R-CG-9).
 */
public final class DefaultCodegraphProbe implements CodegraphProbe {

    private static final String BINARY_NAME = "codegraph";
    private static final Path INDEX_RELPATH = Path.of(".codegraph", "codegraph.db");

    private final Path home;
    private final String pathEnv;

    public DefaultCodegraphProbe() {
        this(Path.of(System.getProperty("user.home")), System.getenv("PATH"));
    }

    public DefaultCodegraphProbe(Path home, String pathEnv) {
        this.home = home;
        this.pathEnv = pathEnv == null ? "" : pathEnv;
    }

    @Override
    public Optional<Path> findBinary() {
        var defaultInstall = home.resolve(".local").resolve("bin").resolve(BINARY_NAME);
        if (Files.isRegularFile(defaultInstall)) return Optional.of(defaultInstall);
        for (var entry : pathEnv.split(File.pathSeparator)) {
            if (entry.isBlank()) continue;
            var candidate = Path.of(entry).resolve(BINARY_NAME);
            if (Files.isRegularFile(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    @Override
    public boolean hasIndex(Path projectDiskPath) {
        return Files.isRegularFile(projectDiskPath.resolve(INDEX_RELPATH));
    }
}

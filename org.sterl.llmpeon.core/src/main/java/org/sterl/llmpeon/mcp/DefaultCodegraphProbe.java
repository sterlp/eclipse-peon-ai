package org.sterl.llmpeon.mcp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.sterl.llmpeon.shared.UserToolEnvironment;

/**
 * Path-based {@link CodegraphProbe} (R-CG-2): the binary is a start HINT —
 * {@code ~/.local/bin/codegraph} first, then the PATH entries of the user tool environment
 * (the same one {@code ShellTool} uses); the index search walks UP from the project to the
 * Git root (module projects index one level up), and the directory holding
 * {@code .codegraph/codegraph.db} is the server's workDir. Read-only: never starts a
 * process, never writes (R-CG-9).
 */
public final class DefaultCodegraphProbe implements CodegraphProbe {

    private static final String BINARY_NAME = "codegraph";
    private static final Path INDEX_RELPATH = Path.of(".codegraph", "codegraph.db");

    private final Path home;
    private final String userPathPrefix;
    private final String systemPath;

    public DefaultCodegraphProbe() {
        this(Path.of(System.getProperty("user.home")),
                UserToolEnvironment.current().pathPrefix(),
                System.getenv("PATH"));
    }

    public DefaultCodegraphProbe(Path home, String userPathPrefix, String systemPath) {
        this.home = home;
        this.userPathPrefix = userPathPrefix == null ? "" : userPathPrefix;
        this.systemPath = systemPath == null ? "" : systemPath;
    }

    @Override
    public Optional<Path> findBinary() {
        var defaultInstall = home.resolve(".local").resolve("bin").resolve(BINARY_NAME);
        if (Files.isRegularFile(defaultInstall)) return Optional.of(defaultInstall);
        for (var entry : (userPathPrefix + File.pathSeparator + systemPath).split(File.pathSeparator)) {
            if (entry.isBlank()) continue;
            var candidate = Path.of(entry).resolve(BINARY_NAME);
            if (Files.isRegularFile(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Path> findIndexWorkDir(Path projectDiskPath) {
        for (var dir = projectDiskPath; dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve(INDEX_RELPATH))) return Optional.of(dir);
            // A Git root (a .git DIRECTORY) bounds the upward search — a submodule's .git is a
            // FILE, so the walk continues up to the superproject (R-CG-2)
            if (Files.isDirectory(dir.resolve(".git"))) break;
        }
        return Optional.empty();
    }
}

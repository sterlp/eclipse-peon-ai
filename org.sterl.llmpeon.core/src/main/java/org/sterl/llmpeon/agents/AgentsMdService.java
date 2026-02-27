package org.sterl.llmpeon.agents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.sterl.llmpeon.shared.FileUtils;

public class AgentsMdService {

    private Path resolvedFile;

    /**
     * Loads the AGENTS.md / agents.md content for the given path.
     * The path may point directly to the file or to a directory containing it.
     *
     * @return true if the content changed (including first load or path switch)
     */
    public boolean load(String path) {
        return load(FileUtils.toPath(path));
    }
    /**
     * Loads the AGENTS.md / agents.md content for the given path.
     * The path may point directly to the file or to a directory containing it.
     *
     * @return true if the content changed (including first load or path switch)
     */
    public boolean load(Path path) {
        if (path == null && resolvedFile == null) return false;
        Path file = resolve(path);

        if (Objects.equals(file, resolvedFile)) {
            return false;
        } else {
            resolvedFile = file;
            return true;
        }
    }

    /** Returns the cached content, or null if not loaded / file not present. */
    public String read() {
        return FileUtils.readString(resolvedFile);
    }

    public boolean hasAgentFile() {
        return resolvedFile != null;
    }

    // -------------------------------------------------------------------------

    private static Path resolve(Path path) {
        if (path == null) return null;
        if (Files.isRegularFile(path)) return path;
        if (Files.isDirectory(path)) {
            Path upper = path.resolve("AGENTS.md");
            if (Files.isRegularFile(upper)) return upper;
            Path lower = path.resolve("agents.md");
            if (Files.isRegularFile(lower)) return lower;
            return null;
        }
        return null;
    }
}

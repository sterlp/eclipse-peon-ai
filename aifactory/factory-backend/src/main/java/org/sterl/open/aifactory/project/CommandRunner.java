package org.sterl.open.aifactory.project;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Seam around process execution — keeps the git tests deterministic and free of a real shell.
 */
public interface CommandRunner {

    /**
     * @param exitCode    process exit code, or -1 when it timed out
     * @param output      stdout with stderr merged, in arrival order
     * @param timedOut    true when the timeout killed the process
     */
    record CommandResult(int exitCode, String output, boolean timedOut) {
        public boolean ok() {
            return exitCode == 0 && !timedOut;
        }
    }

    CommandResult run(Path workingDir, List<String> command, Duration timeout);
}

package org.sterl.llmpeon.shared;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The user's terminal tool environment: the directories a login shell finds (sdkman,
 * Homebrew, …) that an Eclipse started from the Dock often cannot see via
 * {@code System.getenv("PATH")}, plus the sdkman {@code JAVA_HOME}. Discovery is pure
 * path-based — no process is started (R-CG-9).
 *
 * <p>Cached: the environment does not change during the IDE's lifetime. Consumers:
 * {@code ShellTool} (PATH/JAVA_HOME for shell commands) and {@code DefaultCodegraphProbe}
 * (binary hint search, R-CG-2).
 */
public record UserToolEnvironment(String pathPrefix, String javaHome) {

    private static volatile UserToolEnvironment cached;

    /** Cached discovery — the first call scans, later calls return the same instance. */
    public static UserToolEnvironment current() {
        var result = cached;
        if (result == null) {
            result = discover();
            cached = result;
        }
        return result;
    }

    /** Scans the well-known user tool locations — existing directories only. */
    public static UserToolEnvironment discover() {
        var home = System.getProperty("user.home");
        var sdkmanJava = home + "/.sdkman/candidates/java/current";
        var pathPrefix = Stream.of(
                home + "/.sdkman/candidates/maven/current/bin",
                sdkmanJava + "/bin",
                "/opt/homebrew/bin",
                "/usr/local/bin",
                "/usr/bin",
                "/bin",
                "/usr/sbin",
                "/sbin")
            .filter(p -> Files.isDirectory(Path.of(p)))
            .reduce((a, b) -> a + File.pathSeparator + b)
            .orElse("");
        return new UserToolEnvironment(pathPrefix, Files.isDirectory(Path.of(sdkmanJava)) ? sdkmanJava : null);
    }
}

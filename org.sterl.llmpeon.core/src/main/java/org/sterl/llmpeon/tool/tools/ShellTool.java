
package org.sterl.llmpeon.tool.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.SearchQuery;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Executes shell commands (e.g. maven, git, npm) with timeout support.
 */
public class ShellTool extends AbstractTool {

    @FunctionalInterface
    public interface ShellConfirmationProvider {
        String confirm(String command, String workingDirectory);
    }

    private static final int DEFAULT_TIMEOUT_S = 60;
    private static final int MAX_OUTPUT_LENGTH = 3000;
    private static final int DEFAULT_TAIL_LINES = 60;

    private static volatile UserToolEnvironment userToolEnvironment;

    private ShellConfirmationProvider confirmationProvider = null;

    @Override
    public boolean isEditTool() { return true; }

    public void setConfirmationProvider(ShellConfirmationProvider confirmationProvider) {
        this.confirmationProvider = confirmationProvider;
    }

    @Tool("Read OS and environment info: name, Java version, user home, PATH, temp dir.")
    public String readOperationSystemInformation() {
        return "java.version: " + System.getProperty("java.version")
            + "\nos.name: " + System.getProperty("os.name")
            + "\nos.arch: " + System.getProperty("os.arch")
            + "\nuser.home: " + System.getProperty("user.home")
            + "\nuser.dir: " + System.getProperty("user.dir")
            + "\nuser.name: " + System.getProperty("user.name")
            + "\nPATH: " + System.getenv("PATH")
            + "\ntmpdir: " + System.getProperty("java.io.tmpdir");
    }

    @Tool("Run a shell command (mvn, npm, git). Not for file I/O — use read/write tools.")
    public String shellRunCommand(
            @P(description = "shell command", name = "command") 
            String command,
            @P(description = "use disk path not eclipse workspace path", name = "workingDirectory", required = false) 
            String workingDirectory,
            @P(description = "timeout in seconds, default=" + DEFAULT_TIMEOUT_S, required = false, name = "timeout") 
            Integer timeout,
            @P(description = "max tail lines, default=" + DEFAULT_TAIL_LINES + "; 0 or -1 = all (hard cap " + MAX_OUTPUT_LENGTH + "); use this instead of `| tail -50`", required = false, name = "tailLines") 
            Integer tailLines,
            @P(description = "filter output lines (regex, literal fallback) — like `| grep`", name = "filter", required = false) 
            String filter) {

        ArgsUtil.requireNonBlank(command, "command");
        if (timeout == null) timeout = DEFAULT_TIMEOUT_S;
        if (workingDirectory == null) workingDirectory = Path.of(".").toAbsolutePath().toString();
        if (tailLines == null) tailLines = DEFAULT_TAIL_LINES;


        if (confirmationProvider != null) {
            String updatedCommand = confirmationProvider.confirm(command, workingDirectory);

            if ("No".equalsIgnoreCase(updatedCommand)) {
                return "Shell command execution denied!";
            }
            if (!"Yes".equalsIgnoreCase(updatedCommand)) {
                command = updatedCommand;
            }
        }

        Path effectiveDir = Path.of(workingDirectory).toAbsolutePath().normalize();
        if (!effectiveDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("workingDirectory is not a valid directory: " + workingDirectory);
        }

        String[] shellCommand;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            shellCommand = new String[] { "cmd.exe", "/c", command };
        } else if (Files.isRegularFile(Path.of("/bin/zsh"))) {
            shellCommand = new String[] { "/bin/zsh", "-l", "-c", command };
        } else {
            shellCommand = new String[] { "/bin/bash", "-l", "-c", command };
        }

        // Shared with the reader thread; only read after reader.join() to
        // guarantee visibility (join() establishes a happens-before edge).
        List<String> lines = new LinkedList<>();
        try {
            onTool("Running: `" + command + "` in " + effectiveDir);

            var pb = new ProcessBuilder(shellCommand);
            pb.directory(effectiveDir.toFile());
            prepareUserToolEnvironment(pb.environment());
            // ensure we have set Xmx for mvn as it is very slow otherwise ...
            if (command.contains("mvn")) pb.environment().putIfAbsent("MAVEN_OPTS", "-Xmx4g");
            pb.redirectErrorStream(true); // merge stderr into stdout
            var process = pb.start();

            Thread reader = new Thread(() -> {
                try (var br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (IOException e) {
                    // stream closed
                }
            });
            reader.setDaemon(true);
            reader.start();

            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.descendants().forEach(ProcessHandle::destroy); // kill grandchildren too
                reader.join(3000); // ensure visibility of all buffered output
                if (process.isAlive() && !process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }

                String partial;
                if (lines.isEmpty() && commandUsesShellTail(command)) {
                    partial = "No output captured - the command pipes through `tail`/`head`, which buffers "
                        + "everything internally and only flushes on normal completion. Killing the process "
                        + "on timeout discarded that buffer. Use the `tailLines` parameter instead of "
                        + "`| tail -N` so output is available even on timeout. Consider a longer timeout.";
                } else {
                    partial = formatOutput(lines, filter, tailLines).text();
                }
                onTool("Command timed out (exit killed) - " + (lines.isEmpty() ? "no output" : lines.size() + " lines captured"));
                return "Command timed out after " + timeout + "s. Partial output:\n" + partial;
            }

            reader.join(2000);
            int exitCode = process.exitValue();

            var output = formatOutput(lines, filter, tailLines);
            String resultStr = output.text();
            if (exitCode != 0) {
                resultStr += System.lineSeparator() + "Exit code: " + exitCode;
            }
            onTool("Command finished (exit " + exitCode + ") reading " 
                    + output.shown() + " lines ...");
            return resultStr;

        } catch (IOException e) {
            onProblem("Failed to run: " + command + " " + e.getMessage());
            return "Error executing command: " + e.getMessage()
                + System.lineSeparator() + "Output so far:" + System.lineSeparator()
                + formatOutput(lines, filter, tailLines).text();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onTool("Stopped " + command);
            return "Command interrupted: " + e.getMessage()
                + System.lineSeparator() + "Output so far:" + System.lineSeparator()
                + formatOutput(lines, filter, tailLines).text();
        }
    }

    /** crude but effective: catches `| tail`, `|tail`, `| head`, `|head` as a trailing/mid pipe stage */
    private static boolean commandUsesShellTail(String command) {
        return (command.contains("| tail") || command.contains("|tail")) 
            || (command.contains("| head") || command.contains("|head"));
    }


    private static void prepareUserToolEnvironment(java.util.Map<String, String> env) {
        var userEnv = userToolEnvironment();
        if (!userEnv.pathPrefix().isEmpty()) {
            env.put("PATH", userEnv.pathPrefix() + File.pathSeparator + env.getOrDefault("PATH", ""));
        }
        if (userEnv.javaHome() != null) env.putIfAbsent("JAVA_HOME", userEnv.javaHome());
    }

    private static UserToolEnvironment userToolEnvironment() {
        var result = userToolEnvironment;
        if (result == null) {
            result = discoverUserToolEnvironment();
            userToolEnvironment = result;
        }
        return result;
    }

    private static UserToolEnvironment discoverUserToolEnvironment() {
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

    private record UserToolEnvironment(String pathPrefix, String javaHome) {}


    /**
     * Applies the optional filter (regex-first, literal fallback) then the tail limit.
     * When a filter is active the output is prefixed with a disclosure line naming the
     * pattern, the search mode and how many of the total lines are shown (repo contract:
     * every truncation/filter is named). {@code shown} = lines actually returned
     * (after filter + tail).
     */
    private static FormattedOutput formatOutput(List<String> allLines, String filter, Integer tailLines) {
        var query = (filter == null || filter.isBlank()) ? null : SearchQuery.of(filter);
        List<String> shownLines = (query == null) ? allLines : allLines.stream().filter(query::matches).toList();
        Tail tail = tail(shownLines, tailLines);
        String text = tail.text();
        if (query != null) {
            String disclosure = "filter: " + query.query() + " (" + (query.literal() ? "literal" : "regex")
                + ", showing " + tail.shown() + " of " + allLines.size() + " lines)";
            text = disclosure + System.lineSeparator() + text;
        }
        return new FormattedOutput(text, tail.shown());
    }

    private record FormattedOutput(String text, int shown) {}

    private record Tail(String text, int shown) {}

    private static Tail tail(List<String> lines, Integer maxLines) {
        int cap = (maxLines == null || maxLines <= 0) ? MAX_OUTPUT_LENGTH : Math.min(maxLines, MAX_OUTPUT_LENGTH);
        if (lines.size() <= cap) {
            return new Tail(String.join(System.lineSeparator(), lines), lines.size());
        }
        int skipped = lines.size() - cap;
        var sb = new StringBuilder();
        sb.append("... (").append(skipped).append(" lines skipped)").append(System.lineSeparator());
        for (int i = skipped; i < lines.size(); i++) {
            sb.append(lines.get(i)).append(System.lineSeparator());
        }
        return new Tail(sb.toString(), cap);
    }
}

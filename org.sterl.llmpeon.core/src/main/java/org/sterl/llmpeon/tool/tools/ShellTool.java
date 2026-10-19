package org.sterl.llmpeon.tool.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.CallStats;
import org.sterl.llmpeon.shared.OutputTrim;
import org.sterl.llmpeon.shared.SearchQuery;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Executes shell commands (e.g. maven, git, npm) with timeout support.
 */
public class ShellTool extends AbstractTool {

    public static final String OPERATION_SYSTEM_INFORMATION = "readOperationSystemInformation";
    public static final String SHELL_RUN_COMMAND = "shellRunCommand";

    @FunctionalInterface
    public interface ShellConfirmationProvider {
        String confirm(String command, String workDir);
    }

    private static final int DEFAULT_TIMEOUT_S = 60;

    private static volatile UserToolEnvironment userToolEnvironment;

    private ShellConfirmationProvider confirmationProvider = null;
    private Supplier<Path> defaultWorkingDir = null; // set at construction, before first use (same as confirmationProvider)

    @Override
    public boolean isEditTool() { return true; }

    public void setConfirmationProvider(ShellConfirmationProvider confirmationProvider) {
        this.confirmationProvider = confirmationProvider;
    }

    /** R3 (shell-tool.md): supplies the default working directory at call time (active project);
     *  null or a null result = fall back to the process CWD (".") as before. */
    public void setDefaultWorkingDir(Supplier<Path> defaultWorkingDir) {
        this.defaultWorkingDir = defaultWorkingDir;
    }

    @Tool(name = OPERATION_SYSTEM_INFORMATION, value = "Read OS and environment info: name, Java version, user home, PATH, temp dir.")
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

    @Tool(name = SHELL_RUN_COMMAND, value = "Run a shell command — only for build, test and git. Reading or searching files → use the Read/Grep tools. Output is trimmed beyond maxChars (default "
            + OutputTrim.DEFAULT_MAX_CHARS + " chars, head + tail excerpt; lines over " + OutputTrim.MAX_LINE_CHARS + " chars cut).")
    public String shellRunCommand(
            @P(description = "shell command", name = "command") 
            String command,
            @P(description = "disk path, default current project", name = "workDir", required = false) 
            String workDir,
            @P(description = "timeout in seconds, default=" + DEFAULT_TIMEOUT_S, required = false, name = "timeout") 
            Integer timeout,
            @P(description = "total output budget in chars (head + tail excerpt), default=" + OutputTrim.DEFAULT_MAX_CHARS + "; 0 or negative = default; no hard cap — use this instead of `| tail -50`", required = false, name = "maxChars") 
            Integer maxChars,
            @P(description = "filter output lines (regex, literal fallback) — like `| grep`", name = "filter", required = false) 
            String filter) {

        ArgsUtil.requireNonBlank(command, "command");
        if (timeout == null) timeout = DEFAULT_TIMEOUT_S;
        Path defaultDir = defaultWorkingDir != null ? defaultWorkingDir.get() : null;
        if (workDir == null) {
            // R3: default = supplier (active project) at call time; no supplier/no project = process CWD
            workDir = (defaultDir != null ? defaultDir : Path.of(".")).toAbsolutePath().toString();
        }
        // maxChars is a default, not a hard cap: null/<=0 falls back, larger values are allowed
        if (maxChars == null || maxChars <= 0) maxChars = OutputTrim.DEFAULT_MAX_CHARS;
        // the filter runs at read time, before the trim window, so it sees the whole stream
        SearchQuery query = (filter == null || filter.isBlank()) ? null : SearchQuery.of(filter);


        if (confirmationProvider != null) {
            String updatedCommand = confirmationProvider.confirm(command, workDir);

            if ("No".equalsIgnoreCase(updatedCommand)) {
                return "Shell command execution denied!";
            }
            if (!"Yes".equalsIgnoreCase(updatedCommand)) {
                command = updatedCommand;
            }
        }

        Path effectiveDir = Path.of(workDir).toAbsolutePath().normalize();
        if (!effectiveDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("workDir is not a valid directory: " 
                    + workDir + " - set default is/was: " + defaultDir);
        }
        // R3: disclose the effective (absolute + normalized) working dir — first line of every run result
        String cwdPrefix = "cwd=" + effectiveDir + System.lineSeparator();

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
        // Writer = reader thread; the window caps every line before buffering (bounded memory).
        OutputTrim.Window window = OutputTrim.window(maxChars);
        int[] totalLines = new int[1];
        var stats = CallStats.start();
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
                        totalLines[0]++;
                        if (query == null || query.matches(line)) {
                            window.addLine(line);
                        }
                    }
                } catch (IOException e) {
                    // stream closed
                }
            });
            reader.setDaemon(true);
            reader.start();

            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!completed) {
                // R4 (shell-tool.md): freeze the measured time until the timeout fired — the
                // cleanup below must not inflate it; the total (incl. cleanup) stays in the suffix
                String timedOutAfter = stats.duration();
                // R4: snapshot the tree BEFORE the kill — afterwards the children are reparented
                // out of the subtree and a surviving writer holds the pipe end open
                var descendants = process.descendants().toList();
                descendants.forEach(ProcessHandle::destroyForcibly); // children first
                process.destroyForcibly(); // then the parent
                process.waitFor(); // reap
                reader.join(3000); // safety net only — the kill's EOF already unblocks the reader

                String partial;
                if (totalLines[0] == 0 && commandUsesShellTail(command)) {
                    partial = "Don't pipe through `tail`/`head` — the tool trims output itself (maxChars). Consider a longer timeout.";
                } else {
                    partial = buildOutput(window, query, totalLines[0]).text();
                }
                String result = cwdPrefix + "Command timed out after " + timedOutAfter
                    + ". Partial output:\n" + partial + System.lineSeparator() + stats.suffix();
                onTool("Command timed out (exit killed) - " + (totalLines[0] == 0 ? "no output" : totalLines[0] + " lines captured")
                    + " " + stats.suffix() + " " + StringUtil.charsSuffix(result));
                return result;
            }

            reader.join(2000);
            int exitCode = process.exitValue();

            var output = buildOutput(window, query, totalLines[0]);
            String resultStr = output.text();
            if (exitCode != 0) {
                resultStr += System.lineSeparator() + "Exit code: " + exitCode;
            }
            String result = resultStr.isEmpty()
                    ? cwdPrefix + stats.suffix()
                    : cwdPrefix + resultStr + System.lineSeparator() + stats.suffix();
            onTool("Command finished (exit " + exitCode + ") reading " 
                    + output.shownLines() + " lines ..." + " " + stats.suffix() + " " + StringUtil.charsSuffix(result));
            return result;

        } catch (IOException e) {
            onProblem("Failed to run: " + command + " " + e.getMessage());
            return cwdPrefix + "Error executing command: " + e.getMessage()
                + System.lineSeparator() + "Output so far:" + System.lineSeparator()
                + buildOutput(window, query, totalLines[0]).text();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String result = cwdPrefix + "Command interrupted: " + e.getMessage()
                + System.lineSeparator() + "Output so far:" + System.lineSeparator()
                + buildOutput(window, query, totalLines[0]).text();
            onTool("Stopped " + command + " " + stats.suffix() + " " + StringUtil.charsSuffix(result));
            return result;
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
     * Builds the output body: the trimmed window text, prefixed with a filter disclosure when a
     * filter is active (repo contract: every truncation/filter is named). The disclosure names
     * the pattern, the search mode and how many of the total read lines are shown in the window.
     */
    private static ShownOutput buildOutput(OutputTrim.Window window, SearchQuery query, int totalLines) {
        OutputTrim.Result result = window.build();
        String text = result.text();
        if (query != null) {
            String disclosure = "filter: " + query.query() + " (" + (query.literal() ? "literal" : "regex")
                + ", showing " + result.shownLines() + " of " + totalLines + " lines)";
            text = disclosure + System.lineSeparator() + text;
        }
        return new ShownOutput(text, result.shownLines());
    }

    private record ShownOutput(String text, int shownLines) {}
}

package org.sterl.llmpeon.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Executes shell commands (e.g. maven, git, npm) with timeout support.
 */
public class ShellTool extends AbstractTool {

    private static final long DEFAULT_TIMEOUT_MS = 120_000;
    private static final long MAX_TIMEOUT_MS = 600_000;
    private static final int MAX_OUTPUT_LENGTH = 30_000;
    private static final int DEFAULT_TAIL_LINES = 50;
    
    @Override
    public boolean isEditTool() { return true; }

    @Tool("Returns operation system and user info os.name, user.name etc. use this if the operation system matters if command should be executed.")
    public String readOperationSystemInformation() {
        return "java.version: " + System.getProperty("java.version")
            + "\nos.name: " + System.getProperty("os.name")
            + "\nos.arch: " + System.getProperty("os.arch")
            + "\nuser.home: " + System.getProperty("user.home")
            + "\nuser.dir: " + System.getProperty("user.dir")
            + "\nuser.name: " + System.getProperty("user.name");
    }

    @Tool("Executes a shell command (e.g. mvn, git, npm, gradle) in a given working directory and returns the output. "
            + "Use this for build tools, version control, and other CLI operations. "
            + "By default only the last 50 lines are returned to keep output concise. "
            + "Do NOT use this for file reading or writing - use the file tools instead.")
    public String runOsCommand(
            @P("The shell command to execute, e.g. 'mvn clean install' or 'git status'") String command,
            @P("The working directory to run the command in, e.g. a project disk path") String workingDirectory,
            @P("Optional timeout in milliseconds, default 120000 (2 min), max 600000 (10 min)") Long timeoutMs,
            @P("Optional max number of lines to return from the end of the output, default 50. Use -1 for all output.") Integer tailLines) {

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        if (workingDirectory == null || workingDirectory.isBlank()) {
            throw new IllegalArgumentException("workingDirectory must not be empty");
        }

        Path effectiveDir = Path.of(workingDirectory).toAbsolutePath().normalize();
        if (!effectiveDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("workingDirectory is not a valid directory: " + workingDirectory);
        }

        long timeout = DEFAULT_TIMEOUT_MS;
        if (timeoutMs != null && timeoutMs > 0) {
            timeout = Math.min(timeoutMs, MAX_TIMEOUT_MS);
        }

        monitorMessage("Running: " + command + " in " + effectiveDir);

        String[] shellCommand;
        String extraPaths = "";
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            shellCommand = new String[] { "cmd.exe", "/c", command };
        } else if (Files.isRegularFile(Path.of("/bin/zsh"))) {
            shellCommand = new String[] { "/bin/zsh", "-l", "-c", command };
        }
        else {
            shellCommand = new String[] { "/bin/bash", "-l", "-c", command };
        }
        // and brew by default
        if (Files.isDirectory(Path.of("/opt/homebrew/bin"))) {
            extraPaths += File.pathSeparatorChar + "/opt/homebrew/bin";
        };

        try {
            ProcessBuilder pb = new ProcessBuilder(shellCommand);
            Map<String, String> env = pb.environment();
            env.put("PATH", StringUtil.stripToEmpty(System.getenv("PATH")) + extraPaths);

            pb.directory(effectiveDir.toFile());
            pb.redirectErrorStream(true); // merge stderr into stdout
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (var br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    // stream closed
                }
            });
            reader.setDaemon(true);
            reader.start();

            boolean completed = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                int maxLines = (tailLines != null && tailLines != 0) ? tailLines : DEFAULT_TAIL_LINES;
                String partial = (maxLines < 0) ? output.toString() : tailLines(output.toString(), maxLines);
                return "Command timed out after " + timeout + "ms. Partial output:\n" + truncate(partial);
            }

            reader.join(2000);
            int exitCode = process.exitValue();

            int maxLines = (tailLines != null && tailLines != 0) ? tailLines : DEFAULT_TAIL_LINES;
            String result = (maxLines < 0) ? output.toString() : tailLines(output.toString(), maxLines);
            result = truncate(result);
            if (exitCode != 0) {
                result += "\nExit code: " + exitCode;
            }
            monitorMessage("Command finished (exit " + exitCode + ")");
            return result;

        } catch (IOException e) {
            onProblem("Failed to run: " + command);
            return "Error executing command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Command interrupted: " + e.getMessage();
        }
    }

    private static String tailLines(String output, int maxLines) {
        String[] lines = output.split("\n", -1);
        if (lines.length <= maxLines) return output;
        int skipped = lines.length - maxLines;
        var sb = new StringBuilder();
        sb.append("... (").append(skipped).append(" lines skipped)\n");
        for (int i = lines.length - maxLines; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    private static String truncate(String output) {
        if (output.length() > MAX_OUTPUT_LENGTH) {
            return output.substring(0, MAX_OUTPUT_LENGTH) + "\n... (output truncated)";
        }
        return output;
    }
}

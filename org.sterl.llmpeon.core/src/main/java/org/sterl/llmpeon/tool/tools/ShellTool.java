package org.sterl.llmpeon.tool.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.sterl.llmpeon.shared.ArgsUtil;

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

    private static final long DEFAULT_TIMEOUT_MS = 120_000;
    private static final long MAX_TIMEOUT_MS = 600_000;
    private static final int MAX_OUTPUT_LENGTH = 30_000;
    private static final int DEFAULT_TAIL_LINES = 50;

    private ShellConfirmationProvider confirmationProvider = null;

    @Override
    public boolean isEditTool() { return true; }

    public void setConfirmationProvider(ShellConfirmationProvider confirmationProvider) {
        this.confirmationProvider = confirmationProvider;
    }

    @Tool("OS/user info (os.name, user.name, path info etc.).")
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

    @Tool("Run shell command. (mvn, npm etc.) Not for file I/O.")
    public String runOsCommand(
            @P(description = "shell command", name = "command") String command,
            @P(description = "use disk path not eclipse workspace path", name = "workingDirectory") String workingDirectory,
            @P(description = "timeout ms, default 120000", required = false, name = "timeoutMs") Long timeoutMs,
            @P(description = "max tail lines, default 50 (-1 for all)", required = false, name = "tailLines") Integer tailLines) {
        
        tailLines = ArgsUtil.getOrDefault(tailLines, DEFAULT_TAIL_LINES);

        ArgsUtil.requireNonBlank(command, "command");
        ArgsUtil.requireNonBlank(workingDirectory, "workingDirectory");

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

        long timeout = DEFAULT_TIMEOUT_MS;
        if (timeoutMs != null && timeoutMs > 0) {
            timeout = Math.min(timeoutMs, MAX_TIMEOUT_MS);
        }

        onTool("Running: `" + command + "` in " + effectiveDir);

        String[] shellCommand;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            shellCommand = new String[] { "cmd.exe", "/c", command };
        } else if (Files.isRegularFile(Path.of("/bin/zsh"))) {
            shellCommand = new String[] { "/bin/zsh", "-l", "-c", command };
        }
        else {
            shellCommand = new String[] { "/bin/bash", "-l", "-c", command };
        }

        try {
            // Do NOT set PATH here: the login shell (-l) rebuilds it from the user's
            // rc files (SDKMAN, brew, mvn wrapper, etc.). Clobbering it with Eclipse's
            // GUI-inherited PATH breaks tools like mvn when launched from Finder.
            ProcessBuilder pb = new ProcessBuilder(shellCommand);
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
                String partial = tailLines(output.toString(), tailLines);
                return "Command timed out after " + timeout + "ms. Partial output:\n" + truncate(partial);
            }

            reader.join(2000);
            int exitCode = process.exitValue();

            String result = tailLines(output.toString(), tailLines);
            result = truncate(result);
            if (exitCode != 0) {
                result += "\nExit code: " + exitCode;
            }
            onTool("Command finished (exit " + exitCode + ")");
            return result;

        } catch (IOException e) {
            onProblem("Failed to run: " + command);
            return "Error executing command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Command interrupted: " + e.getMessage();
        }
    }

    private static String tailLines(String output, Integer maxLines) {
        if (maxLines == null || maxLines <= 0) return output;

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

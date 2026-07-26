
package org.sterl.llmpeon.tool.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
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

    private static final long DEFAULT_TIMEOUT_S = 30;
    private static final int MAX_OUTPUT_LENGTH = 3000;
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
    public String shellRunCommand(
            @P(description = "shell command", name = "command") 
            String command,
            @P(description = "use disk path not eclipse workspace path", name = "workingDirectory", required = false) 
            String workingDirectory,
            @P(description = "timeout in seconds, default " + DEFAULT_TIMEOUT_S + "s", required = false, name = "timeout") 
            Long timeout,
            @P(description = "max tail lines, default " + DEFAULT_TAIL_LINES + " (-1 for all); use this instead of `| tail -50`", required = false, name = "tailLines") 
            Integer tailLines) {

        ArgsUtil.requireNonBlank(command, "command");
        if (timeout == null) timeout = DEFAULT_TIMEOUT_S;
        if (workingDirectory == null) workingDirectory = Path.of(".").toAbsolutePath().toString();

        tailLines = ArgsUtil.getOrDefault(tailLines, DEFAULT_TAIL_LINES);


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

            // Do NOT set PATH here: the login shell (-l) rebuilds it from the user's
            // rc files (SDKMAN, brew, mvn wrapper, etc.). Clobbering it with Eclipse's
            // GUI-inherited PATH breaks tools like mvn when launched from Finder.
            var pb = new ProcessBuilder(shellCommand);
            pb.directory(effectiveDir.toFile());
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
                        + "`| tail -N` so output is available even on timeout.";
                } else {
                    partial = tailLines(lines, tailLines);
                }
                onTool("Command timed out (exit killed) - " + (lines.isEmpty() ? "no output" : lines.size() + " lines captured"));
                return "Command timed out after " + timeout + "s. Partial output:\n" + partial;
            }

            reader.join(2000);
            int exitCode = process.exitValue();

            String resultStr = tailLines(lines, tailLines);
            if (exitCode != 0) {
                resultStr += System.lineSeparator() + "Exit code: " + exitCode;
            }
            onTool("Command finished (exit " + exitCode + ") reading " 
                    + Math.min(lines.size(), tailLines) + " lines ...");
            return resultStr;

        } catch (IOException e) {
            onProblem("Failed to run: " + command + " " + e.getMessage());
            return "Error executing command: " + e.getMessage()
                + System.lineSeparator() + "Output so far:" + System.lineSeparator()
                + tailLines(lines, tailLines);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onTool("Stopped " + command);
            return "Command interrupted: " + e.getMessage()
                + System.lineSeparator() + "Output so far:" + System.lineSeparator()
                + tailLines(lines, tailLines);
        }
    }

    /** crude but effective: catches `| tail`, `|tail`, `| head`, `|head` as a trailing/mid pipe stage */
    private static boolean commandUsesShellTail(String command) {
        return command.matches("(?s).*\\|\\s*(tail|head)\\b.*");
    }

    private static String tailLines(List<String> lines, Integer maxLines) {
        if (maxLines == null) maxLines = MAX_OUTPUT_LENGTH;
        if (maxLines > MAX_OUTPUT_LENGTH) maxLines = MAX_OUTPUT_LENGTH;

        if (maxLines <= 0 || lines.size() <= maxLines) {
            return String.join(System.lineSeparator(), lines);
        }
        int skipped = lines.size() - maxLines;
        var sb = new StringBuilder();
        sb.append("... (").append(skipped).append(" lines skipped)").append(System.lineSeparator());
        for (int i = skipped; i < lines.size(); i++) {
            sb.append(lines.get(i)).append(System.lineSeparator());
        }
        return sb.toString();
    }
}

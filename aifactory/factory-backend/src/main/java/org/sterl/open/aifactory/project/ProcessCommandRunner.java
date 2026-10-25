package org.sterl.open.aifactory.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ProcessBuilder} behind the {@link CommandRunner} seam: explicit timeout, stdout+stderr
 * captured, and a hardened environment ({@code GIT_TERMINAL_PROMPT=0}, {@code GCM_INTERACTIVE=never})
 * so a clone can never block on a credential question (D11 — public HTTPS repos only).
 */
@Component
public class ProcessCommandRunner implements CommandRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessCommandRunner.class);

    private static final Map<String, String> NON_INTERACTIVE = Map.of(
            "GIT_TERMINAL_PROMPT", "0",
            "GIT_ASKPASS", "echo",
            "GCM_INTERACTIVE", "never",
            "SSH_ASKPASS", "",
            "BatchMode", "yes");

    @Override
    public CommandResult run(Path workingDir, List<String> command, Duration timeout) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workingDir != null) {
                builder.directory(workingDir.toFile());
            }
            builder.redirectErrorStream(true);
            applyNonInteractive(builder);

            process = builder.start();
            Process running = process;
            // drain while waiting, otherwise a full pipe buffer deadlocks before the timeout
            CompletableFuture<String> output = readAsync(running.getInputStream());
            if (!running.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                running.destroyForcibly();
                return new CommandResult(-1, await(output, timeout), true);
            }
            return new CommandResult(running.exitValue(), await(output, timeout), false);
        } catch (IOException e) {
            LOG.info("command failed to start: {} ({})", command, e.getMessage());
            return new CommandResult(-1, "failed to start " + command.getFirst() + ": " + e.getMessage(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "interrupted: " + String.join(" ", command), false);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static void applyNonInteractive(ProcessBuilder builder) {
        builder.environment().putAll(NON_INTERACTIVE);
    }

    private static CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = stream) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }

    private static String await(CompletableFuture<String> output, Duration timeout) {
        try {
            return output.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException e) {
            return "";
        }
    }
}

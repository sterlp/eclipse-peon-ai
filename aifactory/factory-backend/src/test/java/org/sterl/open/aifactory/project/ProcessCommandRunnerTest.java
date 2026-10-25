package org.sterl.open.aifactory.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import org.sterl.open.aifactory.project.CommandRunner.CommandResult;

/**
 * The process layer with <em>real</em> child processes — the only place that can prove the two
 * guards keeping git from ever parking a request thread: the non-interactive environment and the
 * forced kill (R-WP-21). Needs {@code sh}, {@code env}, {@code grep} and {@code pgrep}.
 */
@Timeout(30)
@EnabledOnOs({ OS.MAC, OS.LINUX })
class ProcessCommandRunnerTest {

    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    @TempDir
    Path work;

    @Test
    // UC-WP-32
    void gitIsNeverAllowedToPrompt() {
        CommandResult result = runner.run(null, List.of("sh", "-c",
                "env | grep -E '^(GIT_TERMINAL_PROMPT|GIT_ASKPASS|GCM_INTERACTIVE)='"),
                Duration.ofSeconds(10));

        assertThat(result.ok()).isTrue();
        // the assertions run in a child of the production runner, so the values have to come from it
        assertThat(result.output())
                .contains("GIT_TERMINAL_PROMPT=0")
                .contains("GIT_ASKPASS=echo")
                .contains("GCM_INTERACTIVE=never");
    }

    @Test
    // UC-WP-31
    void stalledCloneAbortsAndLeavesNothing() throws IOException {
        // a script file, not `sh -c "sleep 30 # x"`: a single simple command may be exec'd, which would
        // drop the marker from the process arguments and leave this test green without any kill.
        Path script = Files.writeString(work.resolve("aifactory-stalled-" + UUID.randomUUID() + ".sh"),
                "#!/bin/sh\nsleep 30\n");
        long started = System.nanoTime();

        CommandResult stalled = runner.run(null, List.of("/bin/sh", script.toString()),
                Duration.ofMillis(300));

        assertThat(stalled.timedOut()).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));

        CommandResult probe = runner.run(null, List.of("pgrep", "-f", script.getFileName().toString()),
                Duration.ofSeconds(5));
        // both destroyForcibly() sites are needed to keep this red when either one is deleted
        assertThat(probe.ok()).isFalse();
    }
}

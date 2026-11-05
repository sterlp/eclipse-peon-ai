package org.sterl.open.aifactory.chat;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The gate device on its own, with no context around it: the chat tests claim „the turn is inside a
 * running tool" only as far as this loop holds and releases, so both halves are proven here — and neither
 * half may leave a process behind.
 */
@Timeout(30)
class TurnGateTest {

    @TempDir
    Path checkout;

    private Process gate;

    @AfterEach
    void killAGateNobodyOpened() {
        if (gate != null && gate.isAlive()) {
            gate.destroyForcibly();
        }
    }

    @Test
    void theGateRunsUntilItIsReleased() throws Exception {
        gate = startTurn(TurnGate.command());

        assertThat(gate.waitFor(300, MILLISECONDS))
                .as("ein geschlossenes Gate hält das Werkzeug offen — 300 ms lang mindestens")
                .isFalse();

        TurnGate.release(checkout);

        assertThat(gate.waitFor(2, SECONDS))
                .as("die Freigabe beendet das Werkzeug, statt auf eine Pause zu warten")
                .isTrue();
        assertThat(gate.exitValue())
                .as("und es endet als fertiger Befehl, nicht als Kill")
                .isZero();
    }

    @Test
    void anUnreleasedGateEndsWithItsTimeout() throws Exception {
        gate = startTurn(TurnGate.command(3));

        assertThat(gate.waitFor(10, SECONDS))
                .as("niemand hat freigegeben, und der Prozess ist trotzdem weg — die Obergrenze greift")
                .isTrue();
    }

    /** The very invocation {@code ShellTool} makes of a command on this machine, in the checkout. */
    private Process startTurn(String command) throws IOException {
        return new ProcessBuilder(shellCommand(command))
                .directory(checkout.toFile())
                .redirectErrorStream(true)
                .start();
    }

    private static String[] shellCommand(String command) {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return new String[] { "cmd.exe", "/c", command };
        }
        if (Files.isRegularFile(Path.of("/bin/zsh"))) {
            return new String[] { "/bin/zsh", "-l", "-c", command };
        }
        return new String[] { "/bin/bash", "-l", "-c", command };
    }
}

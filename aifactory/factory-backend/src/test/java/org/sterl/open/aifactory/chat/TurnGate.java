package org.sterl.open.aifactory.chat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A real {@code shellRunCommand} that keeps a turn busy for exactly as long as the test wants
 * (F7): the loop spins in the checkout until the gate file arrives, so the turn is provably inside a
 * running tool — the same proof a fixed {@code sleep} bought — and its length is no longer a wait.
 *
 * <p>Two bounds keep a forgotten release from hanging anything: the loop's own tick bound
 * ({@link #DEFAULT_TICKS} × 0.1 s) and the tool timeout of {@link #toolArguments()}. Both sit
 * <b>above</b> every test deadline on purpose — a gate nobody opens must turn the test red inside its
 * own deadline, not hand the turn back one second before it.</p>
 */
public final class TurnGate {

    /** Name of the file whose arrival opens the gate; it lives in the checkout, the tool's work dir. */
    public static final String FILE = ".turn-gate";

    /** 0.1 s ticks the gate waits on its own — 45 s, above the 30 s deadlines the turn tests use. */
    private static final int DEFAULT_TICKS = 450;

    /** Seconds the shell tool may run: above the gate's own bound, so the gate always decides first. */
    private static final int TOOL_TIMEOUT_S = 60;

    private TurnGate() {
    }

    /** The arguments of the {@code shellRunCommand} call that holds the turn until it is released. */
    public static String toolArguments() {
        return "{\"command\":\"" + command() + "\",\"timeout\":" + TOOL_TIMEOUT_S + "}";
    }

    /** The gate command of a turn test — busy until released, self-bounded if nobody releases it. */
    public static String command() {
        return command(DEFAULT_TICKS);
    }

    /** A gate with its own bound, so the proof that an unreleased gate ends stays cheap. */
    public static String command(int ticks) {
        return "i=0; while [ ! -f " + FILE + " ] && [ $i -lt " + ticks + " ]; do sleep 0.1; i=$((i+1)); done";
    }

    /**
     * The live line the running gate reports — {@code ShellTool} writes it before it blocks in
     * {@code process.waitFor}, so a test that has seen it may claim the turn is inside the tool.
     */
    public static String runningLine() {
        return "Running: `" + command() + "`";
    }

    /** Ends the running gate: the file is the loop's exit condition, so the tool returns immediately. */
    public static void release(Path checkout) {
        try {
            Files.writeString(checkout.resolve(FILE), "released");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot release the turn gate of " + checkout, e);
        }
    }
}

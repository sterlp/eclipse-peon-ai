package org.sterl.llmpeon;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs code that must terminate on a daemon thread with a bounded join. A non-terminating
 * regression (e.g. a removed cycle guard) then fails the test red after the bound instead of
 * hanging the suite: JUnit's {@code @Timeout} cannot kill a non-interruptible loop — the engine
 * waits for the executor thread and the whole run stalls. The daemon probe dies with the JVM.
 */
public final class TerminationProbe {

    /** Outcome of a bounded run: {@code result} or {@code thrown} is set only when {@code terminated}. */
    public record Outcome<T>(T result, Throwable thrown, boolean terminated) {
    }

    public static <T> Outcome<T> run(String name, long timeoutMillis, Callable<T> code) {
        var result = new AtomicReference<T>();
        var thrown = new AtomicReference<Throwable>();
        var probe = new Thread(() -> {
            try {
                result.set(code.call());
            } catch (Throwable t) {
                thrown.set(t);
            }
        }, name);
        probe.setDaemon(true);
        probe.start();
        try {
            probe.join(timeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new Outcome<>(result.get(), thrown.get(), !probe.isAlive());
    }

    private TerminationProbe() {
    }
}

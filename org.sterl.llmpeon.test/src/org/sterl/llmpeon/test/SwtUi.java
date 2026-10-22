package org.sterl.llmpeon.test;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.sterl.llmpeon.parts.shared.EclipseUtil;

/**
 * One UI-thread call and sleep mechanism for the test fragment, shared by SWT widget tests
 * ({@link AbstractSwtUiTest}) and integration tests that deliberately do not extend it.
 */
public final class SwtUi {

    /** Runs the given code on the workbench UI thread and returns its result (or throws). */
    public static <T> T ui(Supplier<T> fn) {
        try {
            return EclipseUtil.runInUiThread(fn).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for UI thread", e);
        } catch (Exception e) {
            throw new AssertionError("UI thread call failed", e);
        }
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }

    private SwtUi() {
    }
}

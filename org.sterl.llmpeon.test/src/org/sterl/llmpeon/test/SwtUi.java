package org.sterl.llmpeon.test;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.ui.PlatformUI;
import org.junit.Assume;
import org.sterl.llmpeon.parts.shared.EclipseUtil;

/**
 * One UI-thread call and sleep mechanism for the test fragment, shared by SWT widget tests
 * ({@link AbstractSwtUiTest}) and integration tests that deliberately do not extend it.
 */
public final class SwtUi {

    /**
     * Skips the calling test (JUnit Assume) when no workbench display is reachable. Headless CI
     * runs the test bundle with <code>useUIHarness=false</code>, where {@code PlatformUI} has no
     * workbench and {@link #ui} can only fail — the assumption turns that into a skip. In a PDE-UI
     * launch it holds and the test stays fully effective.
     */
    public static void assumeUiAvailable() {
        try {
            var workbench = PlatformUI.getWorkbench();
            if (workbench == null || workbench.getDisplay() == null) {
                throw new IllegalStateException("no workbench instance or display");
            }
        } catch (RuntimeException noWorkbench) {
            Assume.assumeNoException("no workbench display available — SWT test skipped", noWorkbench);
        }
    }

    /** Runs the given code on the workbench UI thread and returns its result (or throws). */
    public static <T> T ui(Supplier<T> fn) {
        try {
            return EclipseUtil.runInUiThread(fn).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for UI thread", e);
        } catch (Exception e) {
            throw new AssertionError("UI thread call failed: " + e.getMessage(), e);
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

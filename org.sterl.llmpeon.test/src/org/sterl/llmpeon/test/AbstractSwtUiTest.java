package org.sterl.llmpeon.test;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.sterl.llmpeon.parts.shared.EclipseUtil;

/**
 * Base for SWT-UI tests: reuses the PDE workbench's {@link Display} (SWT supports only one
 * display per process), provides a per-test {@link Shell} and runs all widget access on the UI
 * thread via {@link EclipseUtil#runInUiThread}. Skipped (Assume) when no workbench display is
 * available. The workbench display itself is never disposed.
 */
public abstract class AbstractSwtUiTest extends AbstractUnitTest {

    protected Display display;
    protected Shell shell;

    @Before
    public void setUpDisplay() {
        try {
            ui(() -> {
                display = Display.getDefault();
                shell = new Shell(display);
                return null;
            });
        } catch (AssertionError e) {
            Assume.assumeNoException("no workbench display available — SWT test skipped", e.getCause());
        }
        if (display == null || shell == null) {
            Assume.assumeNoException("no workbench display available — SWT test skipped",
                    new IllegalStateException("Display.getDefault() returned null"));
        }
    }

    @After
    public void tearDownDisplay() {
        if (shell != null && !shell.isDisposed()) {
            try {
                ui(() -> {
                    shell.dispose();
                    return null;
                });
            } catch (AssertionError ignored) {
                // display already gone — nothing to dispose
            }
        }
        shell = null;
        // never dispose the workbench's display
    }

    /** Runs the given code on the workbench UI thread and returns its result (or throws). */
    protected <T> T ui(Supplier<T> fn) {
        try {
            return EclipseUtil.runInUiThread(fn).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for UI thread", e);
        } catch (Exception e) {
            throw new AssertionError("UI thread call failed", e);
        }
    }

    protected static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}

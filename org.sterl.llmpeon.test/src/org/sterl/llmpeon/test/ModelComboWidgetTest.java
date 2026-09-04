package org.sterl.llmpeon.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.parts.config.widgets.ModelComboWidget;
import org.sterl.llmpeon.parts.shared.EclipseUtil;

/**
 * Workbench-display test for the shared {@link ModelComboWidget} (first SWT-UI test of the
 * project): reuses the PDE workbench's {@link Display} (SWT supports only one display per
 * process) and performs all widget access on the UI thread via asyncExec. Skipped (Assume)
 * when no workbench display is available.
 */
public class ModelComboWidgetTest extends AbstractUnitTest {

    private static final long WAIT_TIMEOUT_MS = 5_000;
    private static final long SETTLE_MS = 2_000;

    private Display display;
    private Shell shell;

    @Before
    public void setUpDisplay() {
        ModelListCache.instance().clear();
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
        ModelListCache.instance().clear();
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

    @Test
    public void dropdownAndRefreshBuilt() {
        // GIVEN a widget in a 2-column parent grid (like the config pages)
        var widget = ui(() -> newWidget("gpt-4o"));

        // THEN it shows a model combo and a refresh button
        assertNotNull("model combo missing", ui(() -> combo(widget)));
        assertTrue("refresh button missing", ui(() -> hasButton(widget, "Refresh")));
    }

    @Test
    public void fetchShowsListAndKeepsConfiguredModel() {
        // GIVEN a widget with a mock-server connection and configured model "gpt-4o"
        var widget = ui(() -> newWidget("gpt-4o"));

        // WHEN the page-open fetch completes
        ui(() -> {
            widget.fetchModels();
            return null;
        });
        waitUntil(() -> ui(() -> combo(widget).getItems().length) > 0, "model list not applied");

        // THEN the dropdown contains the server's models and the configured one is selected
        assertArrayEquals(new String[] { "gpt-4o", "mock-model" }, ui(() -> combo(widget).getItems()));
        assertEquals("gpt-4o", ui(widget::getModel));
    }

    @Test
    public void refreshRefetchesAndUpdates() {
        // GIVEN a fetched list
        var widget = ui(() -> newWidget("gpt-4o"));
        ui(() -> {
            widget.fetchModels();
            return null;
        });
        waitUntil(() -> ui(() -> combo(widget).getItems().length) > 0, "initial fetch not applied");

        // WHEN the server's model list changes and the user presses Refresh
        mockLlmServer.setModelIds(List.of("new-model"));
        ui(() -> {
            clickRefresh(widget);
            return null;
        });
        waitUntil(() -> ui(() -> List.of(combo(widget).getItems()).contains("new-model")), "refresh not applied");

        // THEN the dropdown shows the new list (configured model kept, still selected)
        assertArrayEquals(new String[] { "new-model", "gpt-4o" }, ui(() -> combo(widget).getItems()));
        assertEquals("gpt-4o", ui(widget::getModel));
    }

    @Test
    public void refreshFailureKeepsPreviousList() {
        // GIVEN a fetched list
        var widget = ui(() -> newWidget("gpt-4o"));
        ui(() -> {
            widget.fetchModels();
            return null;
        });
        waitUntil(() -> ui(() -> combo(widget).getItems().length) > 0, "initial fetch not applied");
        var previous = ui(() -> combo(widget).getItems().clone());

        // WHEN the server fails (and would serve a different list on success) and the user presses Refresh
        mockLlmServer.setModelIds(List.of("new-model"));
        mockLlmServer.enableModelsError();
        ui(() -> {
            clickRefresh(widget);
            return null;
        });
        sleep(SETTLE_MS); // the failing round-trip + apply settles well within this window

        // THEN the previous list is kept (no clear, no auto-switch, no new list applied)
        assertArrayEquals(previous, ui(() -> combo(widget).getItems()));
        assertFalse("new list must not be applied on failure",
                ui(() -> List.of(combo(widget).getItems()).contains("new-model")));
        assertEquals("gpt-4o", ui(widget::getModel));
    }

    // --- helpers ---

    /** Runs the given code on the workbench UI thread and returns its result (or throws). */
    private <T> T ui(Supplier<T> fn) {
        try {
            return EclipseUtil.runInUiThread(fn).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for UI thread", e);
        } catch (Exception e) {
            throw new AssertionError("UI thread call failed", e);
        }
    }

    /** UI-thread only. */
    private ModelComboWidget newWidget(String model) {
        var parent = new Composite(shell, SWT.NONE);
        parent.setLayout(new GridLayout(2, false));
        var widget = new ModelComboWidget(parent, "test",
                () -> ModelComboWidget.baseSnapshot(mockLlmServer.newConfig(model)));
        widget.setModel(model);
        return widget;
    }

    /** UI-thread only. */
    private static CCombo combo(ModelComboWidget widget) {
        for (var child : widget.getChildren()) {
            if (child instanceof CCombo c) return c;
        }
        throw new AssertionError("no CCombo in " + widget);
    }

    /** UI-thread only. */
    private static boolean hasButton(ModelComboWidget widget, String text) {
        for (var child : widget.getChildren()) {
            if (child instanceof Button b && text.equals(b.getText())) return true;
        }
        return false;
    }

    /** UI-thread only. */
    private void clickRefresh(ModelComboWidget widget) {
        for (var child : widget.getChildren()) {
            if (child instanceof Button b && "Refresh".equals(b.getText())) {
                b.notifyListeners(SWT.Selection, null);
                return;
            }
        }
        fail("no refresh button in " + widget);
    }

    private void waitUntil(BooleanSupplier condition, String timeoutMessage) {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            if (Display.getCurrent() != null) {
                while (display.readAndDispatch()) {
                    // Drain UI updates posted by the background model-fetch job.
                }
            }
            sleep(50);
        }
        fail(timeoutMessage + " (after " + WAIT_TIMEOUT_MS + "ms)");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}

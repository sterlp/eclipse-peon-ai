package org.sterl.llmpeon.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.parts.config.widgets.ModelComboWidget;

/**
 * Workbench-display test for the shared {@link ModelComboWidget}: the widget is a controller
 * that builds its combo and refresh button directly into the 2-column parent grid, so the
 * helpers look them up in the parent. Display/Shell lifecycle, the Assume-skip and
 * UI-thread access come from {@link AbstractSwtUiTest}.
 */
public class ModelComboWidgetTest extends AbstractSwtUiTest {

    private static final long WAIT_TIMEOUT_MS = 5_000;
    private static final long SETTLE_MS = 2_000;

    @Before
    public void setUp() {
        ModelListCache.instance().clear();
    }

    @After
    public void tearDown() {
        ModelListCache.instance().clear();
    }

    @Test
    public void dropdownAndRefreshBuilt() {
        // GIVEN a widget in a 2-column parent grid (like the config pages)
        var built = ui(() -> newWidget("gpt-4o"));

        // THEN it shows a model combo and a refresh button
        assertNotNull("model combo missing", ui(() -> combo(built.parent())));
        assertTrue("refresh button missing", ui(() -> hasButton(built.parent(), "Refresh")));
        assertTrue("model combo must be editable (no SWT.READ_ONLY)",
                ui(() -> (combo(built.parent()).getStyle() & SWT.READ_ONLY) == 0));
    }

    @Test
    public void fetchShowsListAndKeepsConfiguredModel() {
        // GIVEN a widget with a mock-server connection and configured model "gpt-4o"
        var built = ui(() -> newWidget("gpt-4o"));

        // WHEN the page-open fetch completes
        ui(() -> {
            built.widget().fetchModels();
            return null;
        });
        waitUntil(() -> ui(() -> combo(built.parent()).getItems().length) > 0, "model list not applied");

        // THEN the dropdown contains the server's models and the configured one is selected
        assertArrayEquals(new String[] { "gpt-4o", "mock-model" }, ui(() -> combo(built.parent()).getItems()));
        assertEquals("gpt-4o", ui(built.widget()::getModel));
    }

    @Test
    public void refreshRefetchesAndUpdates() {
        // GIVEN a fetched list
        var built = ui(() -> newWidget("gpt-4o"));
        ui(() -> {
            built.widget().fetchModels();
            return null;
        });
        waitUntil(() -> ui(() -> combo(built.parent()).getItems().length) > 0, "initial fetch not applied");

        // WHEN the server's model list changes and the user presses Refresh
        mockLlmServer.setModelIds(List.of("new-model"));
        ui(() -> {
            clickRefresh(built.parent());
            return null;
        });
        waitUntil(() -> ui(() -> List.of(combo(built.parent()).getItems()).contains("new-model")), "refresh not applied");

        // THEN the dropdown shows the new list (configured model kept, still selected)
        assertArrayEquals(new String[] { "new-model", "gpt-4o" }, ui(() -> combo(built.parent()).getItems()));
        assertEquals("gpt-4o", ui(built.widget()::getModel));
    }

    @Test
    public void refreshFailureKeepsPreviousList() {
        // GIVEN a fetched list
        var built = ui(() -> newWidget("gpt-4o"));
        ui(() -> {
            built.widget().fetchModels();
            return null;
        });
        waitUntil(() -> ui(() -> combo(built.parent()).getItems().length) > 0, "initial fetch not applied");
        var previous = ui(() -> combo(built.parent()).getItems().clone());

        // WHEN the server fails (and would serve a different list on success) and the user presses Refresh
        mockLlmServer.setModelIds(List.of("new-model"));
        mockLlmServer.enableModelsError();
        ui(() -> {
            clickRefresh(built.parent());
            return null;
        });
        sleep(SETTLE_MS); // the failing round-trip + apply settles well within this window

        // THEN the previous list is kept (no clear, no auto-switch, no new list applied)
        assertArrayEquals(previous, ui(() -> combo(built.parent()).getItems()));
        assertFalse("new list must not be applied on failure",
                ui(() -> List.of(combo(built.parent()).getItems()).contains("new-model")));
        assertEquals("gpt-4o", ui(built.widget()::getModel));
    }

    @Test
    public void typedCaseVariantOfListedModelIsNotDuplicated() {
        // GIVEN the server list contains "FOO" and the user typed "foo"
        mockLlmServer.setModelIds(List.of("FOO"));
        var built = ui(() -> newWidget("foo"));

        // WHEN the page-open fetch completes
        ui(() -> { built.widget().fetchModels(); return null; });
        waitUntil(() -> ui(() -> combo(built.parent()).getItems().length) > 0, "model list not applied");

        // THEN the list has exactly one entry for that name (SOLL: append only if not in the server list)
        var items = ui(() -> combo(built.parent()).getItems());
        assertEquals(1, (int) List.of(items).stream().filter(s -> s.equalsIgnoreCase("foo")).count());
        // canonical (PO 2026-09-12): the server's ID wins — typed variant disappears
        assertEquals("FOO", ui(built.widget()::getModel));
    }

    // --- helpers ---

    /** The widget plus the 2-column parent grid it builds into. */
    private record BuiltWidget(Composite parent, ModelComboWidget widget) {}

    /** UI-thread only. Like the production callers: the "Model:" label comes first, then the widget. */
    private BuiltWidget newWidget(String model) {
        var parent = new Composite(shell, SWT.NONE);
        parent.setLayout(new GridLayout(2, false));
        var label = new Label(parent, SWT.LEFT);
        label.setText("Model:");
        var widget = new ModelComboWidget(parent, "test",
                () -> ModelComboWidget.baseSnapshot(mockLlmServer.newConfig(model)));
        widget.setModel(model);
        return new BuiltWidget(parent, widget);
    }

    /** UI-thread only. */
    private static Combo combo(Composite parent) {
        for (var child : parent.getChildren()) {
            if (child instanceof Combo c) return c;
        }
        throw new AssertionError("no Combo in " + parent);
    }

    /** UI-thread only. */
    private static boolean hasButton(Composite parent, String text) {
        for (var child : parent.getChildren()) {
            if (child instanceof Button b && text.equals(b.getText())) return true;
        }
        return false;
    }

    /** UI-thread only. */
    private void clickRefresh(Composite parent) {
        for (var child : parent.getChildren()) {
            if (child instanceof Button b && "Refresh".equals(b.getText())) {
                b.notifyListeners(SWT.Selection, null);
                return;
            }
        }
        fail("no refresh button in " + parent);
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
}

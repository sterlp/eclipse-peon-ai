package org.sterl.llmpeon.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.agent.AgentModelSelection;
import org.sterl.llmpeon.agent.AgentModelSelection.Target;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.parts.config.widgets.ModelComboWidget;
import org.sterl.llmpeon.parts.widget.ChatModelComboWidget;

/**
 * Workbench-display test for the chat dialog's model selector ({@link ChatModelComboWidget},
 * R-ML5): the widget is a controller that builds its editable combo and refresh button directly
 * into the RowLayout parent (like the production ActionsBar), so the helpers look them up there.
 * Display/Shell lifecycle, the Assume-skip and UI-thread access come from {@link AbstractSwtUiTest}.
 * The commit callback is the contract boundary — AIChatView saves its argument for the active
 * agent (routing: core {@code AgentModelSelectionTest}, Eclipse store: {@link ChatModelPreferenceTest}).
 *
 * <p>Programmatic {@code select()}/{@code setText()} fire no {@code SWT.Selection}/{@code FocusOut};
 * user interaction is simulated with {@code notifyListeners}. Headless runs skip these tests —
 * run them in a PDE launch with a workbench.</p>
 */
public class ChatModelComboWidgetTest extends AbstractSwtUiTest {

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
    public void selectorBuilt() {
        // GIVEN a widget in a RowLayout parent (like the chat's ActionsBar)
        var built = ui(() -> newWidget("gpt-4o", new ArrayList<>()));

        // THEN it shows an editable model combo and a refresh button
        assertNotNull("model combo missing", ui(() -> combo(built.parent())));
        assertTrue("model combo must be editable (no SWT.READ_ONLY)",
                ui(() -> (combo(built.parent()).getStyle() & SWT.READ_ONLY) == 0));
        assertTrue("refresh button missing", ui(() -> hasButton(built.parent(), "↻")));
    }

    // R-ML5 BDD 1/2 — display + tooltip of the active agent's selection
    @Test
    public void showDisplaysEffectiveModelAndTargetTooltip() {
        // GIVEN a widget showing Dev's base model
        var committed = new ArrayList<String>();
        var built = ui(() -> newWidget("qwen3", committed));

        // WHEN the agent switches to PO with its own claude-sonnet
        ui(() -> {
            built.widget().show(new AgentModelSelection("PO", Target.AGENT_SLOT, "claude-sonnet", "claude-sonnet"));
            return null;
        });

        // THEN PO's model is shown, the tooltip says how to restore inheritance, nothing is committed
        assertEquals("claude-sonnet", ui(built.widget()::getModel));
        assertTrue(ui(() -> combo(built.parent()).getToolTipText()).contains("clear the field"));
        assertTrue("programmatic show must not commit", committed.isEmpty());
    }

    @Test
    public void tooltipStatesTheEffectPerTarget() {
        assertTrue(ChatModelComboWidget.tooltipFor(new AgentModelSelection("Dev", Target.BASE, "qwen3", "qwen3"))
                .contains("every agent without its own model"));
        assertTrue(ChatModelComboWidget.tooltipFor(new AgentModelSelection("Plan", Target.AGENT_SLOT, null, "qwen3"))
                .contains("inherits the base model"));
        assertTrue(ChatModelComboWidget.tooltipFor(new AgentModelSelection("Tester", Target.AGENT_FILE, null, "qwen3"))
                .contains("AGENT.md"));
        assertTrue(ChatModelComboWidget.tooltipFor(new AgentModelSelection("PO", Target.AGENT_SLOT, "gpt-5", "gpt-5"))
                .contains("inherit the base model again"));
    }

    // R-ML5 BDD 8
    @Test
    public void focusOutWithoutChangeDoesNotCommit() {
        // GIVEN a widget showing the inherited base "qwen3"
        var committed = new ArrayList<String>();
        var built = ui(() -> newWidget("qwen3", committed));

        // WHEN the user clicks in and leaves without a change (also a case variant, R-ML4)
        ui(() -> {
            combo(built.parent()).notifyListeners(SWT.FocusOut, null);
            combo(built.parent()).setText("QWEN3");
            combo(built.parent()).notifyListeners(SWT.FocusOut, null);
            return null;
        });

        // THEN nothing is committed
        assertTrue("an unchanged field must not commit", committed.isEmpty());
    }

    // R-ML5 BDD 10
    @Test
    public void commitPendingFlushesTypedTextOnce() {
        // GIVEN the user typed gpt-5 without confirming
        var committed = new ArrayList<String>();
        var built = ui(() -> newWidget("qwen3", committed));
        ui(() -> {
            combo(built.parent()).setText("gpt-5");
            return null;
        });

        // WHEN the view flushes before the agent switch, and a late FocusOut follows
        ui(() -> {
            built.widget().commitPending();
            combo(built.parent()).notifyListeners(SWT.FocusOut, null);
            return null;
        });

        // THEN gpt-5 is committed exactly once (for the previous agent — the view switches after)
        assertEquals(List.of("gpt-5"), committed);
    }

    // R-ML5 BDD 7 — the empty field is committed as "", the routing removes the key
    @Test
    public void clearedFieldCommitsEmpty() {
        var committed = new ArrayList<String>();
        var built = ui(() -> newWidget("qwen3", committed));

        ui(() -> {
            combo(built.parent()).setText("");
            combo(built.parent()).notifyListeners(SWT.FocusOut, null);
            return null;
        });

        assertEquals(List.of(""), committed);
    }

    @Test
    public void fetchShowsListAndKeepsConfiguredModel() {
        // GIVEN a widget with a mock-server connection and configured model "gpt-4o"
        var committed = new ArrayList<String>();
        var built = ui(() -> newWidget("gpt-4o", committed));

        // WHEN the view-open fetch completes
        ui(() -> {
            built.widget().fetchModels();
            return null;
        });
        waitUntil(() -> ui(() -> combo(built.parent()).getItems().length) > 0, "model list not applied");

        // THEN the dropdown contains the server's models, the configured one is selected,
        // and the programmatic apply did not commit
        assertArrayEquals(new String[] { "gpt-4o", "mock-model" }, ui(() -> combo(built.parent()).getItems()));
        assertEquals("gpt-4o", ui(built.widget()::getModel));
        assertTrue("programmatic list apply must not fire the commit callback", committed.isEmpty());
    }

    @Test
    public void fetchFailureKeepsConfiguredModel() {
        // GIVEN a widget whose model-list fetch fails (no cache yet)
        mockLlmServer.enableModelsError();
        var committed = new ArrayList<String>();
        var built = ui(() -> newWidget("gpt-4o", committed));

        // WHEN the view-open fetch completes (with an error)
        ui(() -> {
            built.widget().fetchModels();
            return null;
        });
        sleep(SETTLE_MS); // the failing round-trip + apply settles well within this window

        // THEN the configured model stays (no error, no auto-switch, no list)
        assertEquals(0, (int) ui(() -> combo(built.parent()).getItems().length));
        assertEquals("gpt-4o", ui(built.widget()::getModel));
        assertTrue("a failed fetch must not fire the commit callback", committed.isEmpty());
    }

    @Test
    public void refreshFailureKeepsPreviousList() {
        // GIVEN a fetched list
        var built = ui(() -> newWidget("gpt-4o", new ArrayList<>()));
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
    public void selectionCommitsShownText() {
        // GIVEN a fetched list
        var committed = new ArrayList<String>();
        var built = ui(() -> newWidget("gpt-4o", committed));
        ui(() -> {
            built.widget().fetchModels();
            return null;
        });
        waitUntil(() -> ui(() -> combo(built.parent()).getItems().length) > 0, "model list not applied");

        // WHEN the user selects another entry — select() alone fires no SWT.Selection (only a
        // real dropdown click does, on all platforms), so the test raises the event itself
        ui(() -> {
            combo(built.parent()).select(1);
            combo(built.parent()).notifyListeners(SWT.Selection, new Event());
            return null;
        });

        // THEN the commit callback receives the selected text
        assertEquals(List.of("mock-model"), committed);
    }

    @Test
    public void focusOutCommitsTypedText() {
        // GIVEN a widget showing the configured model
        var committed = new ArrayList<String>();
        var built = ui(() -> newWidget("gpt-4o", committed));

        // WHEN the user types free text and the combo loses focus
        ui(() -> {
            combo(built.parent()).setText("typed-x");
            combo(built.parent()).notifyListeners(SWT.FocusOut, null);
            return null;
        });

        // THEN the commit callback receives the typed text
        assertEquals(List.of("typed-x"), committed);
    }

    @Test
    public void programmaticSetModelDoesNotCommit() {
        // GIVEN a widget
        var committed = new ArrayList<String>();
        var built = ui(() -> newWidget("gpt-4o", committed));

        // WHEN applyConfig sets the model programmatically (incl. a null model)
        ui(() -> {
            built.widget().setModel(null);
            return null;
        });

        // THEN the combo shows empty and no commit callback fired
        assertEquals("", ui(built.widget()::getModel));
        assertTrue("programmatic setModel must not fire the commit callback", committed.isEmpty());
    }

    @Test
    public void typedCaseVariantOfListedModelIsCanonicalized() {
        // GIVEN the server list contains "FOO" and the configured model is the typed variant "foo"
        mockLlmServer.setModelIds(List.of("FOO"));
        var built = ui(() -> newWidget("foo", new ArrayList<>()));

        // WHEN the view-open fetch completes
        ui(() -> {
            built.widget().fetchModels();
            return null;
        });
        waitUntil(() -> ui(() -> combo(built.parent()).getItems().length) > 0, "model list not applied");

        // THEN the list has exactly one entry and the server's ID wins (R-ML4 canonical)
        assertArrayEquals(new String[] { "FOO" }, ui(() -> combo(built.parent()).getItems()));
        assertEquals("FOO", ui(built.widget()::getModel));
    }

    // --- helpers ---

    /** The widget plus the RowLayout parent it builds into. */
    private record BuiltWidget(Composite parent, ChatModelComboWidget widget) {}

    /** UI-thread only. Like the production caller (ActionsBar): the combo is built directly into the RowLayout. */
    private BuiltWidget newWidget(String model, List<String> committed) {
        var parent = new Composite(shell, SWT.NONE);
        parent.setLayout(new RowLayout(SWT.HORIZONTAL));
        var widget = new ChatModelComboWidget(parent,
                () -> ModelComboWidget.baseSnapshot(mockLlmServer.newConfig(model)), committed::add);
        widget.setModel(model); // like AIChatView.applyConfig
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
            if (child instanceof Button b && "↻".equals(b.getText())) {
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

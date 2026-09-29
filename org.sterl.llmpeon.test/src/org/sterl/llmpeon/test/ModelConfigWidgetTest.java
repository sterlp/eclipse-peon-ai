package org.sterl.llmpeon.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.parts.config.widgets.ModelConfigWidget;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.provider.ThinkSupport;
import org.sterl.llmpeon.provider.ThinkValueSupport;

/**
 * Workbench-display test for the basic page's {@link ModelConfigWidget}: the widget is a
 * controller that builds its fields directly into the 2-column parent grid, so the helpers look
 * them up in the parent. Display/Shell lifecycle, the Assume-skip and UI-thread access come from
 * {@link AbstractSwtUiTest}.
 */
public class ModelConfigWidgetTest extends AbstractSwtUiTest {

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

    // UC-MCW-1
    @Test
    public void showsConnectionFieldset() {
        // GIVEN a widget in a 2-column grid with an Ollama connection loaded
        var built = ui(() -> newWidget(() -> LlmConfig.newOllama("mock-model"),
                new ModelConfigWidget.ConnectionValues(AiProvider.OLLAMA, "http://127.0.0.1:11434", "secret",
                        "false", "mock-model", null, null)));

        // THEN it shows exactly provider · url · ping · key · model+refresh · think · temperature in
        // that parent-child order
        var children = ui(() -> rendered(built.parent()));
        assertEquals(14, children.length);
        assertEquals("Provider Type:", ((Label) children[0]).getText());
        var provider = (Combo) children[1];
        assertTrue("provider combo must be read-only", (provider.getStyle() & SWT.READ_ONLY) != 0);
        assertEquals(9, provider.getItemCount());
        assertEquals("OpenAI (llama.cpp, unsloth, OmniRoute)", provider.getItem(0));
        assertEquals("URL (incl. port):", ((Label) children[2]).getText());
        assertTrue(children[3] instanceof Text);
        assertEquals("Ping", ((Button) children[4]).getText());
        assertEquals("API Key:", ((Label) children[5]).getText());
        assertTrue(children[6] instanceof Text);
        assertEquals("Model:", ((Label) children[7]).getText());
        var model = (Combo) children[8];
        assertTrue("model combo must be editable (no SWT.READ_ONLY)", (model.getStyle() & SWT.READ_ONLY) == 0);
        assertEquals("Refresh", ((Button) children[9]).getText());
        assertEquals("Think (Default):", ((Label) children[10]).getText());
        var think = (Combo) children[11];
        assertArrayEquals(new String[] { "", "true", "false" }, think.getItems());
        assertEquals("false", think.getText());
        assertEquals("mock-model", model.getText());
        assertEquals("Temperature (empty = unset):", ((Label) children[12]).getText());
        assertTrue(children[13] instanceof Text);

        // AND the free-string variant exists but is excluded (created once, toggled per form)
        var all = ui(() -> built.parent().getChildren());
        assertEquals(19, all.length);
        assertTrue(all[12] instanceof Text);
        assertTrue("free-string variant must be excluded", ((GridData) all[12].getLayoutData()).exclude);

        // AND the extra-body field (binding 6) is created but excluded for the NONE provider (live gate, R-DEF-11)
        assertEquals("Extra body (JSON):", ((Label) all[15]).getText());
        assertTrue("extra-body label must be excluded for a NONE provider", ((GridData) all[15].getLayoutData()).exclude);
    }

    // UC-MCW-7
    @Test
    public void thinkFieldFollowsProviderChange() {
        // GIVEN the widget shows Ollama (toggle) with think "false"
        var built = ui(() -> newWidget(() -> LlmConfig.newOllama("mock-model"),
                new ModelConfigWidget.ConnectionValues(AiProvider.OLLAMA, "http://127.0.0.1:11434", null, "false",
                        "mock-model", null, null)));
        assertEquals("false", ui(() -> thinkCombo(built.parent()).getText()));

        // WHEN the provider changes to OpenAI (fixed values list) via the combo selection
        ui(() -> {
            selectProvider(built.parent(), "OpenAI (llama.cpp, unsloth, OmniRoute)");
            return null;
        });

        // THEN the think field immediately renders the OpenAI values list and the non-matching value is cleared
        var expectedItems = ThinkValueSupport
                .valuesItems((ThinkSupport.Values) LlmProviders.of(AiProvider.OPEN_AI).thinkSupport());
        assertArrayEquals(expectedItems.toArray(String[]::new), ui(() -> thinkCombo(built.parent()).getItems()));
        assertEquals("no silent replacement value", "", ui(() -> thinkCombo(built.parent()).getText()));
        assertEquals("", ui(built.widget()::getValues).think());

        // WHEN a listed value is chosen and the provider changes to LM Studio (free string)
        ui(() -> {
            thinkCombo(built.parent()).select(thinkCombo(built.parent()).indexOf("low"));
            return null;
        });
        ui(() -> {
            selectProvider(built.parent(), "LM Studio (OpenAI-compatible)");
            return null;
        });

        // THEN the free-string field carries the value verbatim
        assertEquals("low", ui(() -> thinkText(built.parent()).getText()));

        // WHEN the provider changes to Gemini (no think support)
        ui(() -> {
            selectProvider(built.parent(), "Google Gemini");
            return null;
        });

        // THEN the think field is gone entirely (label + combo + text all excluded) and the last
        // visible value is preserved (hidden ≠ delete, R-DEF-11)
        assertEquals(12, (int) ui(() -> rendered(built.parent()).length));
        var noneAll = ui(() -> built.parent().getChildren());
        assertTrue(((GridData) noneAll[10].getLayoutData()).exclude);
        assertTrue(((GridData) noneAll[11].getLayoutData()).exclude);
        assertTrue(((GridData) noneAll[12].getLayoutData()).exclude);
        assertEquals("low", ui(built.widget()::getValues).think());

        // WHEN the provider changes back to Ollama, a toggle value is set, then LM Studio again
        ui(() -> {
            selectProvider(built.parent(), "Ollama");
            return null;
        });
        ui(() -> {
            thinkCombo(built.parent()).setText("true");
            return null;
        });
        ui(() -> {
            selectProvider(built.parent(), "LM Studio (OpenAI-compatible)");
            return null;
        });

        // THEN the toggle value is carried over verbatim into the free-string field
        assertEquals("true", ui(() -> thinkText(built.parent()).getText()));
    }

    // UC-DEF-11
    @Test
    public void extraBodyFieldFollowsProviderChangeLive() {
        // GIVEN a widget with Ollama (no extra-body support → the field is hidden)
        var built = ui(() -> newWidget(() -> LlmConfig.newOllama("mock-model"),
                new ModelConfigWidget.ConnectionValues(AiProvider.OLLAMA, "http://127.0.0.1:11434", null, "false",
                        "mock-model", null, null)));

        // WHEN the provider changes to OpenAI (extra-body capable) via the combo selection
        ui(() -> {
            selectProvider(built.parent(), "OpenAI (llama.cpp, unsloth, OmniRoute)");
            return null;
        });

        // THEN the extra-body field is immediately visible (label · multi-text · examples row) without Apply
        var visible = ui(() -> rendered(built.parent()));
        assertEquals("Extra body (JSON):", ((Label) visible[14]).getText());
        assertTrue("extra-body field must be multi-line", (((Text) visible[15]).getStyle() & SWT.MULTI) != 0);
        assertTrue("examples row must be a composite", visible[16] instanceof Composite);

        // WHEN a value is typed and the provider changes to Gemini (no extra-body support)
        ui(() -> {
            extraBodyText(built.parent()).setText("{\"a\":1}");
            return null;
        });
        ui(() -> {
            selectProvider(built.parent(), "Google Gemini");
            return null;
        });

        // THEN the field is hidden again but the typed value survives in the values (hidden ≠ delete)
        assertEquals(12, (int) ui(() -> rendered(built.parent()).length));
        assertEquals("{\"a\":1}", ui(built.widget()::getValues).extraBody());

        // WHEN the provider changes back to OpenAI
        ui(() -> {
            selectProvider(built.parent(), "OpenAI (llama.cpp, unsloth, OmniRoute)");
            return null;
        });

        // THEN the value is back in the field
        assertEquals("{\"a\":1}", ui(() -> extraBodyText(built.parent()).getText()));
    }

    // UC-DEF-11
    @Test
    public void hiddenThinkSurvivesNoneProvider() {
        // GIVEN the widget shows Ollama (toggle) with think "false"
        var built = ui(() -> newWidget(() -> LlmConfig.newOllama("mock-model"),
                new ModelConfigWidget.ConnectionValues(AiProvider.OLLAMA, "http://127.0.0.1:11434", null, "false",
                        "mock-model", null, null)));

        // WHEN the provider changes to Gemini (no think support → the field is hidden)
        ui(() -> {
            selectProvider(built.parent(), "Google Gemini");
            return null;
        });

        // THEN the hidden value survives in the values (not "" — hidden ≠ delete)
        assertEquals("false", ui(built.widget()::getValues).think());

        // WHEN the provider changes back to Ollama
        ui(() -> {
            selectProvider(built.parent(), "Ollama");
            return null;
        });

        // THEN the value is back in the field
        assertEquals("false", ui(() -> thinkCombo(built.parent()).getText()));
    }

    // UC-MCW-2
    @Test
    public void reloadUsesLiveWidgetValues() {
        // GIVEN the base ("store state") points at a dead URL and the widget holds the live mock-server URL (typed, no Apply)
        var built = ui(() -> newWidget(
                () -> LlmConfig.newConfig(AiProvider.OPEN_AI, "gpt-4o", "http://127.0.0.1:1/v1"),
                new ModelConfigWidget.ConnectionValues(null, null, null, "", "gpt-4o", null, null)));
        ui(() -> {
            urlText(built.parent()).setText(mockLlmServer.getUrl());
            return null;
        });

        // WHEN the user presses Refresh → THEN the list comes from the mock server (widget identity wins)
        ui(() -> {
            clickRefresh(built.parent());
            return null;
        });
        waitUntil(() -> ui(() -> modelCombo(built.parent()).getItems().length) > 0, "model list not applied");
        assertArrayEquals(new String[] { "gpt-4o", "mock-model" }, ui(() -> modelCombo(built.parent()).getItems()));

        // AND the other way: a live base with a dead URL typed in the widget → no list (widget identity still wins)
        var builtDead = ui(() -> newWidget(() -> mockLlmServer.newConfig("gpt-4o"),
                new ModelConfigWidget.ConnectionValues(null, null, null, "", "gpt-4o", null, null)));
        ui(() -> {
            urlText(builtDead.parent()).setText("http://127.0.0.1:1/v1");
            return null;
        });
        ui(() -> {
            builtDead.widget().fetchModels();
            return null;
        });
        sleep(SETTLE_MS); // the failing round-trip + apply settles well within this window
        assertEquals("no list from the typed dead URL (a base-identity fetch would have listed the mock's models)",
                0, (int) ui(() -> modelCombo(builtDead.parent()).getItems().length));
        assertEquals("typed model kept verbatim", "gpt-4o", ui(builtDead.widget()::getValues).model());
    }

    // UC-MCW-5
    @Test
    public void pingUsesLiveWidgetUrl() {
        // GIVEN the base points at a dead URL (irrelevant) and the widget holds the live mock-server URL
        var built = ui(() -> newWidget(
                () -> LlmConfig.newConfig(AiProvider.OPEN_AI, "gpt-4o", "http://127.0.0.1:1/v1"), null));
        ui(() -> {
            urlText(built.parent()).setText(mockLlmServer.getUrl());
            return null;
        });

        // WHEN the ping is computed → THEN reachable, with the widget's URL
        var ok = ui(built.widget()::computePing);
        assertTrue("ping must reach the typed mock URL", ok.reachable());
        assertEquals(mockLlmServer.getUrl(), ok.url());

        // AND a dead widget URL is unreachable
        ui(() -> {
            urlText(built.parent()).setText("http://127.0.0.1:1/v1");
            return null;
        });
        var dead = ui(built.widget()::computePing);
        assertFalse("dead typed URL must not be reachable", dead.reachable());
        assertEquals("http://127.0.0.1:1/v1", dead.url());
    }

    // UC-DEF-8
    @Test
    public void showsTemperatureFieldAfterThink() {
        // GIVEN a widget with an Ollama connection loaded (think = toggle form)
        var built = ui(() -> newWidget(() -> LlmConfig.newOllama("mock-model"),
                new ModelConfigWidget.ConnectionValues(AiProvider.OLLAMA, "http://127.0.0.1:11434", "secret", "false",
                        "mock-model", null, null)));

        // THEN the temperature field sits right after the think field (binding 6) and is rendered
        // (no provider gate — request-level like think, R-T5). Rendered indices 12/13: the
        // excluded think free-string text (parent index 12) shifts the rendered order.
        var children = ui(() -> rendered(built.parent()));
        assertEquals("Temperature (empty = unset):", ((Label) children[12]).getText());
        assertTrue(children[13] instanceof Text);
    }

    // UC-DEF-8
    @Test
    public void temperatureRoundTripsThroughValues() {
        // GIVEN a widget loaded with a temperature
        var built = ui(() -> newWidget(() -> LlmConfig.newOllama("mock-model"),
                new ModelConfigWidget.ConnectionValues(AiProvider.OLLAMA, "http://127.0.0.1:11434", null, "false",
                        "mock-model", "0.7", null)));

        // THEN the value round-trips load → getValues
        assertEquals("0.7", ui(built.widget()::getValues).temperature());

        // WHEN the field is cleared
        ui(() -> {
            temperatureText(built.parent()).setText("");
            return null;
        });

        // THEN getValues returns null (empty = unset)
        assertNull(ui(built.widget()::getValues).temperature());
    }

    // --- helpers ---

    /** The widget plus the 2-column parent grid it builds into. */
    private record BuiltWidget(Composite parent, ModelConfigWidget widget) {}

    /** UI-thread only. Like the production caller: the widget builds into a 2-column grid. */
    private BuiltWidget newWidget(Supplier<LlmConfig> base, ModelConfigWidget.ConnectionValues values) {
        var parent = new Composite(shell, SWT.NONE);
        parent.setLayout(new GridLayout(2, false));
        var widget = new ModelConfigWidget(parent, "test", base);
        widget.load(values);
        return new BuiltWidget(parent, widget);
    }

    /** UI-thread only. The real combo selection trigger: select + Selection event (like a user pick). */
    private static void selectProvider(Composite parent, String label) {
        var combo = (Combo) parent.getChildren()[1];
        var idx = combo.indexOf(label);
        if (idx < 0) fail("no provider entry labeled '" + label + "'");
        combo.select(idx);
        combo.notifyListeners(SWT.Selection, null);
    }

    /** UI-thread only. */
    private static Text urlText(Composite parent) {
        return (Text) parent.getChildren()[3];
    }

    /** UI-thread only. */
    private static Combo modelCombo(Composite parent) {
        return (Combo) parent.getChildren()[8];
    }

    /** UI-thread only. The think combo — created once, so its slot is stable (field 5 of the order). */
    private static Combo thinkCombo(Composite parent) {
        var c = parent.getChildren()[11];
        if (c instanceof Combo combo) return combo;
        throw new AssertionError("expected think combo, got " + c.getClass().getSimpleName());
    }

    /** UI-thread only. The think free-string field — created once, toggled per form. */
    private static Text thinkText(Composite parent) {
        var c = parent.getChildren()[12];
        if (c instanceof Text t) return t;
        throw new AssertionError("expected think text, got " + c.getClass().getSimpleName());
    }

    /** UI-thread only. The temperature field — created once, always rendered (no provider gate). */
    private static Text temperatureText(Composite parent) {
        var c = parent.getChildren()[14];
        if (c instanceof Text t) return t;
        throw new AssertionError("expected temperature text, got " + c.getClass().getSimpleName());
    }

    /** UI-thread only. The extra-body field — created once, gated live per provider (R-DEF-11). */
    private static Text extraBodyText(Composite parent) {
        var c = parent.getChildren()[16];
        if (c instanceof Text t) return t;
        throw new AssertionError("expected extra-body text, got " + c.getClass().getSimpleName());
    }

    /** UI-thread only. The rendered children — GridLayout honors only {@code GridData.exclude}. */
    private static Control[] rendered(Composite parent) {
        var list = new java.util.ArrayList<Control>();
        for (var child : parent.getChildren()) {
            var gd = child.getLayoutData();
            if (gd == null || !((GridData) gd).exclude) {
                list.add(child);
            }
        }
        return list.toArray(new Control[0]);
    }

    /** UI-thread only. */
    private static void clickRefresh(Composite parent) {
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

package org.sterl.llmpeon.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
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
import org.osgi.service.prefs.BackingStoreException;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.AiConfigPreferenceView;
import org.sterl.llmpeon.parts.config.EclipseLlmConfigStore;

/**
 * Page-level test for the basic config page after the {@link ModelConfigWidget} rewiring
 * (ADR-0060): the connection fields live in the widget in the binding order, OK persists the
 * widget values (incl. the dev think slot), and Reload/Ping never touch the store.
 *
 * <p>No-cross-run-state rule: the fixture is written VOR the page build (the page reads the
 * store at build time) and the original values of the 5 keys are restored in finally. Store
 * reads go through {@link org.osgi.service.prefs.Preferences} — the truth below the
 * ScopedPreferenceStore cache.</p>
 */
public class AiConfigPreferenceViewTest extends AbstractSwtUiTest {

    private static final long WAIT_TIMEOUT_MS = 5_000;
    private static final String THINK_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_THINK);
    private static final String TEMPERATURE_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV,
            LlmConfigKeys.AGENT_FIELD_TEMPERATURE);

    private static final List<String> KEYS = List.of(PeonConstants.PREF_PROVIDER_TYPE, PeonConstants.PREF_URL,
            PeonConstants.PREF_API_KEY, PeonConstants.PREF_MODEL, THINK_KEY, TEMPERATURE_KEY);

    private IEclipsePreferences prefs;
    private Map<String, String> original;

    @Before
    public void setUpStore() {
        ModelListCache.instance().clear();
        prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        original = new HashMap<>();
        for (var key : KEYS) {
            original.put(key, prefs.get(key, null));
        }
        // Fixture VOR the page build: Ollama · dead URL · empty key · fixture model · unset think/temperature
        var store = new EclipseLlmConfigStore(prefs);
        store.put(PeonConstants.PREF_PROVIDER_TYPE, "OLLAMA");
        store.put(PeonConstants.PREF_URL, "http://127.0.0.1:1");
        store.put(PeonConstants.PREF_API_KEY, "");
        store.put(PeonConstants.PREF_MODEL, "fixture-model");
        store.remove(THINK_KEY);
        store.remove(TEMPERATURE_KEY);
    }

    @After
    public void restoreStore() {
        ModelListCache.instance().clear();
        if (original == null) {
            return;
        }
        for (var entry : original.entrySet()) {
            if (entry.getValue() == null) {
                prefs.remove(entry.getKey());
            } else {
                prefs.put(entry.getKey(), entry.getValue());
            }
        }
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            throw new AssertionError("store restore flush failed", e);
        }
    }

    // UC-MCW-6
    @Test
    public void performOkPersistsWidgetValues() {
        // GIVEN the basic page is built (fixture: Ollama · dead URL · empty key · fixture-model · unset think)
        var page = ui(() -> buildPage());
        var parent = ui(() -> fieldEditorParent(page));

        // THEN the connection fields sit in the widget in the binding order 1-5 (+ Ping) and no
        // loose connection field editors remain
        var rendered = ui(() -> rendered(parent));
        assertEquals(24, rendered.length);
        assertEquals("Provider Type:", ((Label) rendered[0]).getText());
        var provider = (Combo) rendered[1];
        assertTrue("provider combo must be read-only", (provider.getStyle() & SWT.READ_ONLY) != 0);
        assertEquals(9, provider.getItemCount());
        assertEquals("URL (incl. port):", ((Label) rendered[2]).getText());
        assertEquals("Ping", ((Button) rendered[4]).getText());
        assertEquals("API Key:", ((Label) rendered[5]).getText());
        assertEquals("Model:", ((Label) rendered[7]).getText());
        assertEquals("fixture-model", ((Combo) rendered[8]).getText());
        assertEquals("Refresh", ((Button) rendered[9]).getText());
        assertEquals("Think (Default):", ((Label) rendered[10]).getText());
        assertArrayEquals(new String[] { "", "true", "false" }, ((Combo) rendered[11]).getItems());
        // the exact rendered count + indices above also rule out loose connection field editors
        // (any extra editor would add children and shift the order)

        // WHEN the widget values change (provider → OpenAI, url/key/model typed, think selected) and OK is pressed
        ui(() -> {
            selectProvider(parent, "OpenAI (llama.cpp, unsloth, OmniRoute)");
            ((Text) parent.getChildren()[3]).setText("http://127.0.0.1:9/v1");
            ((Text) parent.getChildren()[6]).setText("sk-test-123");
            ((Combo) parent.getChildren()[8]).setText("gpt-4o");
            var think = (Combo) parent.getChildren()[11];
            think.select(think.indexOf("low"));
            return null;
        });

        // AND the binding field order survives the provider switch (think stays field 5, not reordered to the page end)
        var afterSwitch = ui(() -> rendered(parent));
        assertEquals("Think (Default):", ((Label) afterSwitch[10]).getText());
        assertTrue(afterSwitch[11] instanceof Combo);

        ui(page::performOk);

        // THEN the store carries provider/url/key/model + the dev think
        assertEquals("OPEN_AI", prefs.get(PeonConstants.PREF_PROVIDER_TYPE, null));
        assertEquals("http://127.0.0.1:9/v1", prefs.get(PeonConstants.PREF_URL, null));
        assertEquals("sk-test-123", prefs.get(PeonConstants.PREF_API_KEY, null));
        assertEquals("gpt-4o", prefs.get(PeonConstants.PREF_MODEL, null));
        assertEquals("low", prefs.get(THINK_KEY, null));
    }

    // UC-DEF-8
    @Test
    public void performOkPersistsTemperature() {
        // GIVEN the basic page is built (fixture: unset temperature → empty field)
        var page = ui(() -> buildPage());
        var parent = ui(() -> fieldEditorParent(page));
        assertEquals("unset temperature loads as an empty field", "", temperatureText(parent).getText());

        // WHEN the temperature is typed and OK is pressed — the key field must be non-empty:
        // performOk's setValue(null) NPEs in the JFace store for an empty key (preexisting bug,
        // reported to the PO — not fixed in this increment)
        ui(() -> {
            temperatureText(parent).setText("0.7");
            ((Text) parent.getChildren()[6]).setText("sk-test-123");
            return null;
        });
        ui(page::performOk);

        // THEN the dev temperature key carries the value
        assertEquals("0.7", prefs.get(TEMPERATURE_KEY, null));
    }

    @Test
    public void performOkWithEmptyUrlAndApiKey_removesKeysAndSavesCompletely() {
        // GIVEN the basic page is built (fixture: Ollama · dead URL · empty key · fixture-model · unset think/temperature)
        var page = ui(() -> buildPage());
        var parent = ui(() -> fieldEditorParent(page));

        // WHEN URL and API key are cleared, think/temperature are set and OK is pressed —
        // empty = unset: both keys must be removed (not null-put — JFace's setValue(name, null)
        // NPEd on the empty key and left a partial save), and the save must run to completion
        ui(() -> {
            ((Text) parent.getChildren()[3]).setText("");
            ((Text) parent.getChildren()[6]).setText("");
            var think = (Combo) parent.getChildren()[11];
            think.select(think.indexOf("true"));
            temperatureText(parent).setText("0.7");
            return null;
        });
        ui(page::performOk);

        // THEN both empty keys are removed (not stored as empty strings) and the rest of the save completed
        assertNull("empty URL must remove the key, not store an empty value", prefs.get(PeonConstants.PREF_URL, null));
        assertNull("empty API key must remove the key, not store an empty value", prefs.get(PeonConstants.PREF_API_KEY, null));
        assertEquals("OLLAMA", prefs.get(PeonConstants.PREF_PROVIDER_TYPE, null));
        assertEquals("fixture-model", prefs.get(PeonConstants.PREF_MODEL, null));
        assertEquals("true", prefs.get(THINK_KEY, null));
        assertEquals("0.7", prefs.get(TEMPERATURE_KEY, null));
    }

    // UC-DEF-11
    @Test
    public void hiddenThinkKeySurvivesOk() {
        // GIVEN the fixture is Gemini (no think support → the think field is hidden) with a stored dev think
        var store = new EclipseLlmConfigStore(prefs);
        store.put(PeonConstants.PREF_PROVIDER_TYPE, "GOOGLE_GEMINI");
        store.put(THINK_KEY, "false");

        // WHEN the page is built and OK is pressed without any change
        var page = ui(() -> buildPage());
        ui(page::performOk);

        // THEN the stored think key survives (hidden ≠ delete: the hidden field returns its last visible value)
        assertEquals("false", prefs.get(THINK_KEY, null));
    }

    // UC-MCW-4
    @Test
    public void reloadDoesNotTouchStore() {
        // GIVEN the widget's URL is changed to the mock server (new identity, no Apply)
        var page = ui(() -> buildPage());
        var parent = ui(() -> fieldEditorParent(page));
        ui(() -> {
            selectProvider(parent, "OpenAI (llama.cpp, unsloth, OmniRoute)");
            ((Text) parent.getChildren()[3]).setText(mockLlmServer.getUrl());
            return null;
        });

        // WHEN Reload delivers a list under the new widget identity
        ui(() -> {
            clickRefresh(parent);
            return null;
        });
        waitUntil(() -> ui(() -> modelItems(parent).length) > 0, "model list not applied");

        // THEN the connection keys still carry the stored values
        assertEquals("OLLAMA", prefs.get(PeonConstants.PREF_PROVIDER_TYPE, null));
        assertEquals("http://127.0.0.1:1", prefs.get(PeonConstants.PREF_URL, null));
        assertEquals("", prefs.get(PeonConstants.PREF_API_KEY, null));
        assertEquals("fixture-model", prefs.get(PeonConstants.PREF_MODEL, null));
        assertNull(prefs.get(THINK_KEY, null));
    }

    // UC-MCW-3
    @Test
    public void reloadDoesNotPersist() {
        // GIVEN the widget's URL is changed and Reload succeeded under the new identity
        var page = ui(() -> buildPage());
        var parent = ui(() -> fieldEditorParent(page));
        ui(() -> {
            selectProvider(parent, "OpenAI (llama.cpp, unsloth, OmniRoute)");
            ((Text) parent.getChildren()[3]).setText(mockLlmServer.getUrl());
            return null;
        });
        ui(() -> {
            clickRefresh(parent);
            return null;
        });
        waitUntil(() -> ui(() -> modelItems(parent).length) > 0, "model list not applied");

        // WHEN the page is cancelled
        ui(page::performCancel);

        // THEN the store is unchanged (old state)
        assertEquals("OLLAMA", prefs.get(PeonConstants.PREF_PROVIDER_TYPE, null));
        assertEquals("http://127.0.0.1:1", prefs.get(PeonConstants.PREF_URL, null));
        assertEquals("", prefs.get(PeonConstants.PREF_API_KEY, null));
        assertEquals("fixture-model", prefs.get(PeonConstants.PREF_MODEL, null));
        assertNull(prefs.get(THINK_KEY, null));
    }

    // --- helpers ---

    /** UI-thread only. Builds the page into the test shell (like a preference dialog would). */
    private AiConfigPreferenceView buildPage() {
        var page = new AiConfigPreferenceView();
        page.createControl(shell);
        return page;
    }

    /** UI-thread only. The 2-column grid the field editors (and the widget) build into. */
    private static Composite fieldEditorParent(AiConfigPreferenceView page) {
        var providerLabel = findControl(page.getControl(),
                c -> c instanceof Label l && "Provider Type:".equals(l.getText()));
        assertNotNull("provider label not found under the page control", providerLabel);
        return providerLabel.getParent();
    }

    private static Control findControl(Control root, java.util.function.Predicate<Control> match) {
        if (match.test(root)) {
            return root;
        }
        if (root instanceof Composite composite) {
            for (var child : composite.getChildren()) {
                var found = findControl(child, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** UI-thread only. The rendered children — GridLayout honors only {@code GridData.exclude}. */
    private static Control[] rendered(Composite parent) {
        var list = new ArrayList<Control>();
        for (var child : parent.getChildren()) {
            var gd = child.getLayoutData();
            if (gd == null || !((GridData) gd).exclude) {
                list.add(child);
            }
        }
        return list.toArray(new Control[0]);
    }

    /** UI-thread only. The real combo selection trigger: select + Selection event (like a user pick). */
    private static void selectProvider(Composite parent, String label) {
        var combo = (Combo) parent.getChildren()[1];
        var idx = combo.indexOf(label);
        if (idx < 0) {
            throw new AssertionError("no provider entry labeled '" + label + "'");
        }
        combo.select(idx);
        combo.notifyListeners(SWT.Selection, null);
    }

    /** UI-thread only. The widget's model combo (slot 4 of the binding order). */
    private static Combo modelCombo(Composite parent) {
        return (Combo) parent.getChildren()[8];
    }

    /** UI-thread only. The widget's temperature field (binding 6 of the order). */
    private static Text temperatureText(Composite parent) {
        return (Text) parent.getChildren()[14];
    }

    /** UI-thread only. */
    private static String[] modelItems(Composite parent) {
        return modelCombo(parent).getItems();
    }

    /** UI-thread only. */
    private static void clickRefresh(Composite parent) {
        for (var child : parent.getChildren()) {
            if (child instanceof Button b && "Refresh".equals(b.getText())) {
                b.notifyListeners(SWT.Selection, null);
                return;
            }
        }
        throw new AssertionError("no refresh button in " + parent);
    }

    private void waitUntil(BooleanSupplier condition, String timeoutMessage) {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            if (Display.getCurrent() != null) {
                while (display.readAndDispatch()) {
                    // Drain UI updates posted by the background model-fetch job.
                }
            }
            sleep(50);
        }
        throw new AssertionError(timeoutMessage + " (after " + WAIT_TIMEOUT_MS + "ms)");
    }
}

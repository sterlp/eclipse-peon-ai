package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
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
import org.sterl.llmpeon.parts.config.AiAdvancedPreferenceView;
import org.sterl.llmpeon.parts.config.EclipseLlmConfigStore;

/**
 * Page-level test for the advanced config page's dev section (R-DEF-4/5/7, ADR-0062): the
 * "Dev (Default)" section carries the full basic-page fieldset (provider · url · key ·
 * model+refresh · think · temperature · ping · extra body), and OK writes the base keys — the
 * legacy llm.agent.dev.url/apiKey overrides are removed (clean break).
 *
 * <p>No-cross-run-state rule: the fixture is written VOR the page build (the page reads the
 * store at build time) and the original values are restored in finally.</p>
 */
public class AiAdvancedPreferenceViewTest extends AbstractSwtUiTest {

    private static final String DEV_URL_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_URL);
    private static final String DEV_API_KEY_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_API_KEY);
    private static final String DEV_THINK_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_THINK);
    private static final String DEV_TEMPERATURE_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_TEMPERATURE);
    private static final String DEV_EXTRA_BODY_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_EXTRA_BODY);

    private static final List<String> KEYS = List.of(PeonConstants.PREF_PROVIDER_TYPE, PeonConstants.PREF_URL,
            PeonConstants.PREF_API_KEY, PeonConstants.PREF_MODEL, DEV_URL_KEY, DEV_API_KEY_KEY, DEV_THINK_KEY,
            DEV_TEMPERATURE_KEY, DEV_EXTRA_BODY_KEY);

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
        // Fixture VOR the page build: OpenAI (extra-body-capable) · dead URL · empty key ·
        // fixture model · legacy dev overrides present (must be removed on OK) · unset dev
        // think/temperature/extraBody
        var store = new EclipseLlmConfigStore(prefs);
        store.put(PeonConstants.PREF_PROVIDER_TYPE, "OPEN_AI");
        store.put(PeonConstants.PREF_URL, "http://127.0.0.1:1");
        store.put(PeonConstants.PREF_API_KEY, "");
        store.put(PeonConstants.PREF_MODEL, "fixture-model");
        store.put(DEV_URL_KEY, "http://legacy-dev:11434");
        store.put(DEV_API_KEY_KEY, "legacy-dev-key");
        store.remove(DEV_THINK_KEY);
        store.remove(DEV_TEMPERATURE_KEY);
        store.remove(DEV_EXTRA_BODY_KEY);
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

    // UC-DEF-4
    @Test
    public void performOkWritesBaseKeysForDev() {
        // GIVEN the advanced page is built (fixture: OpenAI · dead URL · legacy dev overrides present)
        var page = ui(() -> buildPage());
        var grid = ui(() -> devGrid(page));
        assertEquals("fixture-model", ((Combo) grid.getChildren()[8]).getText());

        // WHEN the dev section values change (url/key/model typed, think + temperature + extra body set) and OK is pressed
        ui(() -> {
            ((Text) grid.getChildren()[3]).setText("http://127.0.0.1:9/v1");
            ((Text) grid.getChildren()[6]).setText("sk-test-123");
            ((Combo) grid.getChildren()[8]).setText("dev-base-model");
            var think = (Combo) grid.getChildren()[11];
            var lowIdx = think.indexOf("low");
            assertTrue("the think combo must offer 'low'", lowIdx >= 0);
            think.select(lowIdx);
            ((Text) grid.getChildren()[14]).setText("0.7");
            ((Text) grid.getChildren()[16]).setText("{\"a\":1}");
            return null;
        });
        ui(page::performOk);

        // THEN the base keys carry the widget values and the dev record fields are persisted
        assertEquals("OPEN_AI", prefs.get(PeonConstants.PREF_PROVIDER_TYPE, null));
        assertEquals("http://127.0.0.1:9/v1", prefs.get(PeonConstants.PREF_URL, null));
        assertEquals("sk-test-123", prefs.get(PeonConstants.PREF_API_KEY, null));
        assertEquals("dev-base-model", prefs.get(PeonConstants.PREF_MODEL, null));
        assertEquals("low", prefs.get(DEV_THINK_KEY, null));
        assertEquals("0.7", prefs.get(DEV_TEMPERATURE_KEY, null));
        assertEquals("{\"a\":1}", prefs.get(DEV_EXTRA_BODY_KEY, null));

        // AND the legacy dev override keys are removed (clean break, ADR-0062)
        assertNull(prefs.get(DEV_URL_KEY, null));
        assertNull(prefs.get(DEV_API_KEY_KEY, null));
    }

    // UC-DEF-7
    @Test
    public void devSectionCarriesFullFieldset() {
        // GIVEN the advanced page is built (fixture: OpenAI — extra-body-capable)
        var page = ui(() -> buildPage());
        var grid = ui(() -> devGrid(page));

        // THEN the dev section carries the full basic-page fieldset + extra body (19 children,
        // the think free-string text is excluded for the OpenAI values form)
        assertEquals(19, grid.getChildren().length);
        assertEquals("Provider Type:", ((Label) grid.getChildren()[0]).getText());
        var provider = (Combo) grid.getChildren()[1];
        assertTrue("provider combo must be read-only", (provider.getStyle() & SWT.READ_ONLY) != 0);
        assertEquals(9, provider.getItemCount());
        assertEquals(0, provider.getSelectionIndex()); // fixture provider OPEN_AI = first entry
        assertEquals("URL (incl. port):", ((Label) grid.getChildren()[2]).getText());
        assertEquals("http://127.0.0.1:1", ((Text) grid.getChildren()[3]).getText());
        assertEquals("Ping", ((Button) grid.getChildren()[4]).getText());
        assertEquals("API Key:", ((Label) grid.getChildren()[5]).getText());
        assertEquals("Model:", ((Label) grid.getChildren()[7]).getText());
        assertEquals("fixture-model", ((Combo) grid.getChildren()[8]).getText());
        assertEquals("Refresh", ((Button) grid.getChildren()[9]).getText());
        assertEquals("Think (Default):", ((Label) grid.getChildren()[10]).getText());
        assertTrue(grid.getChildren()[11] instanceof Combo);
        assertEquals("Temperature (empty = unset):", ((Label) grid.getChildren()[13]).getText());
        assertEquals("Extra body (JSON):", ((Label) grid.getChildren()[15]).getText());
        assertTrue("extra body field must be multi-line", (((Text) grid.getChildren()[16]).getStyle() & SWT.MULTI) != 0);
        assertTrue("examples row must be a composite", grid.getChildren()[17] instanceof Composite);
        assertTrue("paste status must be a label", grid.getChildren()[18] instanceof Label);
    }

    // --- helpers ---

    /** UI-thread only. Builds the page into the test shell (like a preference dialog would). */
    private AiAdvancedPreferenceView buildPage() {
        var page = new AiAdvancedPreferenceView();
        page.createControl(shell);
        return page;
    }

    /** UI-thread only. The 2-column grid inside the "Dev (Default)" titled group. */
    private static Composite devGrid(AiAdvancedPreferenceView page) {
        var group = findGroup(page.getControl(), "Dev (Default)");
        assertNotNull("the Dev (Default) section group not found", group);
        assertEquals("the dev group must hold exactly the 2-column grid", 1, group.getChildren().length);
        return (Composite) group.getChildren()[0];
    }

    private static Group findGroup(Control root, String title) {
        if (root instanceof Group g && title.equals(g.getText())) {
            return g;
        }
        if (root instanceof Composite composite) {
            for (var child : composite.getChildren()) {
                var found = findGroup(child, title);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

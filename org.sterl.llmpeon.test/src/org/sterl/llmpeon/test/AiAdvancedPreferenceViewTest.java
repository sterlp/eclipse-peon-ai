package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
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
 * Page-level test for the advanced config page (ADR-0063, single owner): the page carries only
 * the per-agent override sections (po/plan/search/compact) — OK writes exactly the per-agent
 * slot keys, never the base keys or the dev default slot (owned by the basic page).
 *
 * <p>No-cross-run-state rule: the fixture is written VOR the page build (the page reads the
 * store at build time) and the original values are restored in finally.</p>
 */
public class AiAdvancedPreferenceViewTest extends AbstractSwtUiTest {

    private static final String DEV_THINK_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_THINK);
    private static final String DEV_TEMPERATURE_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_TEMPERATURE);
    private static final String DEV_EXTRA_BODY_KEY = LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_EXTRA_BODY);
    private static final String PO_URL_KEY = LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_URL);
    private static final String PO_API_KEY_KEY = LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_API_KEY);
    private static final String PO_MODEL_KEY = LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_MODEL);
    private static final String PO_THINK_KEY = LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_THINK);
    private static final String PO_TEMPERATURE_KEY = LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_TEMPERATURE);
    private static final String PO_EXTRA_BODY_KEY = LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_EXTRA_BODY);

    private static final List<String> KEYS = List.of(PeonConstants.PREF_PROVIDER_TYPE, PeonConstants.PREF_URL,
            PeonConstants.PREF_API_KEY, PeonConstants.PREF_MODEL, DEV_THINK_KEY, DEV_TEMPERATURE_KEY,
            DEV_EXTRA_BODY_KEY, PO_URL_KEY, PO_API_KEY_KEY, PO_MODEL_KEY, PO_THINK_KEY, PO_TEMPERATURE_KEY,
            PO_EXTRA_BODY_KEY);

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
        // Fixture VOR the page build: base keys (OpenAI · dead URL · empty key · fixture model)
        // + dev default slot set (owned by the basic page — must survive an advanced OK) + a
        // stale PO url (must be overwritten by the typed value on OK)
        var store = new EclipseLlmConfigStore(prefs);
        store.put(PeonConstants.PREF_PROVIDER_TYPE, "OPEN_AI");
        store.put(PeonConstants.PREF_URL, "http://127.0.0.1:1");
        store.put(PeonConstants.PREF_API_KEY, "");
        store.put(PeonConstants.PREF_MODEL, "fixture-model");
        store.put(DEV_THINK_KEY, "low");
        store.put(DEV_TEMPERATURE_KEY, "0.5");
        store.put(DEV_EXTRA_BODY_KEY, "{\"dev\":true}");
        store.put(PO_URL_KEY, "http://stale-po:11111");
        store.remove(PO_API_KEY_KEY);
        store.remove(PO_MODEL_KEY);
        store.remove(PO_THINK_KEY);
        store.remove(PO_TEMPERATURE_KEY);
        store.remove(PO_EXTRA_BODY_KEY);
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

    // UC-DEF-10
    @Test
    public void performOkWritesOnlySlotKeys() {
        // GIVEN the advanced page is built (fixture: base keys + dev slot set, stale PO url)
        var page = ui(() -> buildPage());
        var grid = ui(() -> sectionGrid(page, "PO agent (Jon)"));
        assertEquals("the PO section must carry url/key/model+refresh/think/temperature/extra body",
                15, grid.getChildren().length);
        assertEquals("stale-po must be loaded into the section", "http://stale-po:11111",
                ((Text) grid.getChildren()[1]).getText());

        // WHEN the PO section values change (url/key/model typed, think + temperature + extra body set) and OK is pressed
        ui(() -> {
            ((Text) grid.getChildren()[1]).setText("http://127.0.0.1:9/po");
            ((Text) grid.getChildren()[3]).setText("sk-po-123");
            ((Combo) grid.getChildren()[5]).setText("po-model");
            var think = (Combo) grid.getChildren()[8];
            var lowIdx = think.indexOf("low");
            assertTrue("the PO think combo must offer 'low'", lowIdx >= 0);
            think.select(lowIdx);
            ((Text) grid.getChildren()[10]).setText("0.6");
            ((Text) grid.getChildren()[12]).setText("{\"po\":true}");
            return null;
        });
        ui(page::performOk);

        // THEN the PO slot keys carry the typed values (the stale url is overwritten)
        assertEquals("http://127.0.0.1:9/po", prefs.get(PO_URL_KEY, null));
        assertEquals("sk-po-123", prefs.get(PO_API_KEY_KEY, null));
        assertEquals("po-model", prefs.get(PO_MODEL_KEY, null));
        assertEquals("low", prefs.get(PO_THINK_KEY, null));
        assertEquals("0.6", prefs.get(PO_TEMPERATURE_KEY, null));
        assertEquals("{\"po\":true}", prefs.get(PO_EXTRA_BODY_KEY, null));

        // AND the base keys are UNCHANGED (fixture values — the basic page is the sole owner)
        assertEquals("OPEN_AI", prefs.get(PeonConstants.PREF_PROVIDER_TYPE, null));
        assertEquals("http://127.0.0.1:1", prefs.get(PeonConstants.PREF_URL, null));
        assertEquals("", prefs.get(PeonConstants.PREF_API_KEY, null));
        assertEquals("fixture-model", prefs.get(PeonConstants.PREF_MODEL, null));

        // AND the dev default slot is UNCHANGED (no write path from this page)
        assertEquals("low", prefs.get(DEV_THINK_KEY, null));
        assertEquals("0.5", prefs.get(DEV_TEMPERATURE_KEY, null));
        assertEquals("{\"dev\":true}", prefs.get(DEV_EXTRA_BODY_KEY, null));
    }

    // --- helpers ---

    /** UI-thread only. Builds the page into the test shell (like a preference dialog would). */
    private AiAdvancedPreferenceView buildPage() {
        var page = new AiAdvancedPreferenceView();
        page.createControl(shell);
        return page;
    }

    /** UI-thread only. The per-agent section composite inside the given titled group. */
    private static Composite sectionGrid(AiAdvancedPreferenceView page, String groupTitle) {
        var group = findGroup(page.getControl(), groupTitle);
        assertNotNull("section group not found: " + groupTitle, group);
        assertEquals("the section group must hold exactly one section composite", 1, group.getChildren().length);
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

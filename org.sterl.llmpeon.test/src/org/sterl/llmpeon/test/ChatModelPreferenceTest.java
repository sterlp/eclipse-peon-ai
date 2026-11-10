package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;

/**
 * R-ML5 BDD 3 (org.sterl.llmpeon/docs/model-loading.md): the chat model selector's commit
 * persists the base model — PREF_MODEL shows it and the next config
 * ({@link LlmPreferenceInitializer#buildWithDefaults()}) carries it. Works on the real instance
 * node (buildWithDefaults reads only that one), so the previous PREF_MODEL is restored in tearDown.
 */
public class ChatModelPreferenceTest {

    private IEclipsePreferences prefs;
    private String previousModel;

    @Before
    public void setUp() {
        prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        previousModel = prefs.get(PeonConstants.PREF_MODEL, null);
    }

    @After
    public void tearDown() throws Exception {
        if (previousModel == null) prefs.remove(PeonConstants.PREF_MODEL);
        else prefs.put(PeonConstants.PREF_MODEL, previousModel);
        prefs.flush();
    }

    @Test
    public void selectedModelIsPersistedAndCarriedByNextConfig() throws Exception {
        // GIVEN the chat is open
        // WHEN the user commits another model in the chat dropdown
        LlmPreferenceInitializer.saveBaseModel(prefs, "chat-selected-model");

        // THEN PREF_MODEL shows the model and the next config carries it
        assertEquals("chat-selected-model", prefs.get(PeonConstants.PREF_MODEL, null));
        assertEquals("chat-selected-model", LlmPreferenceInitializer.buildWithDefaults().getModel());
    }

    @Test
    public void typedModelIsStrippedBeforePersisting() throws Exception {
        // GIVEN the chat is open
        // WHEN the user confirms typed text with surrounding whitespace (FocusOut)
        LlmPreferenceInitializer.saveBaseModel(prefs, "  typed-model  ");

        // THEN the stripped text is persisted and carried by the next config
        assertEquals("typed-model", prefs.get(PeonConstants.PREF_MODEL, null));
        assertEquals("typed-model", LlmPreferenceInitializer.buildWithDefaults().getModel());
    }
}

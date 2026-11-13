package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.agent.AgentModelSelection;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.EclipseLlmConfigStore;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.tool.ToolService;

/**
 * R-ML5 (org.sterl.llmpeon/docs/model-loading.md) against the real Eclipse preference store: the
 * chat model selector saves through {@code PeonAiService.saveActiveAgentModel} →
 * {@link AgentModelSelection#save} with an {@link EclipseLlmConfigStore} — the same store
 * AIChatView passes. The next config ({@link LlmPreferenceInitializer#buildWithDefaults()}) must
 * carry the change, and only for the active agent. Works on the real instance node
 * (buildWithDefaults reads only that one), so the touched keys are restored in tearDown.
 * The routing per target is covered in core ({@code AgentModelSelectionTest}).
 */
public class ChatModelPreferenceTest {

    private static final String PO_KEY = LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_MODEL);

    private IEclipsePreferences prefs;
    private EclipseLlmConfigStore store;
    private String previousModel;
    private String previousPoModel;
    private Path configDir;

    @Before
    public void setUp() throws Exception {
        prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        store = new EclipseLlmConfigStore(prefs);
        previousModel = prefs.get(PeonConstants.PREF_MODEL, null);
        previousPoModel = prefs.get(PO_KEY, null);
        configDir = Files.createTempDirectory("chat-model-pref");
        // GIVEN base qwen3, PO without its own model
        prefs.put(PeonConstants.PREF_MODEL, "qwen3");
        prefs.remove(PO_KEY);
        prefs.flush();
    }

    @After
    public void tearDown() throws Exception {
        restore(PeonConstants.PREF_MODEL, previousModel);
        restore(PO_KEY, previousPoModel);
        prefs.flush();
    }

    // R-ML5 BDD 3
    @Test
    public void poSelectionIsPersistedInThePoSlotOnly() {
        // GIVEN PO is active
        var model = currentModel();
        var po = new AiPoAgent(model, new ToolService());

        // WHEN gpt-5 is chosen in the chat dropdown
        assertTrue(AgentModelSelection.save(po, model.getConfig(), store, "gpt-5"));

        // THEN the next config carries gpt-5 for PO, the base (Dev) keeps qwen3
        var next = LlmPreferenceInitializer.buildWithDefaults();
        assertEquals("gpt-5", next.modelConfigFor(AgentModelConfig.PO).model());
        assertEquals("qwen3", next.getModel());
    }

    // R-ML5 BDD 4
    @Test
    public void devSelectionIsPersistedAsBaseModel() {
        // GIVEN Dev is active
        var model = currentModel();
        var dev = new AiDevAgent(model, new ToolService());

        // WHEN the user confirms typed text with surrounding whitespace (FocusOut)
        assertTrue(AgentModelSelection.save(dev, model.getConfig(), store, "  gpt-5  "));

        // THEN the stripped text is the base model, no PO slot is created
        assertEquals("gpt-5", prefs.get(PeonConstants.PREF_MODEL, null));
        assertEquals("gpt-5", LlmPreferenceInitializer.buildWithDefaults().getModel());
        assertNull(prefs.get(PO_KEY, null));
    }

    // R-ML5 BDD 7
    @Test
    public void clearedBaseModelRemovesTheKeyInsteadOfSavingEmpty() {
        // GIVEN Dev is active
        var model = currentModel();
        var dev = new AiDevAgent(model, new ToolService());

        // WHEN the field is cleared
        assertTrue(AgentModelSelection.save(dev, model.getConfig(), store, ""));

        // THEN the instance key is gone (the default applies) — never stored as ""
        assertFalse(java.util.Arrays.asList(keys()).contains(PeonConstants.PREF_MODEL));
    }

    private ConfiguredChatModel currentModel() {
        return new ConfiguredChatModel(LlmPreferenceInitializer.buildWithDefaults().toBuilder().configDir(configDir).build());
    }

    private String[] keys() {
        try {
            return prefs.keys();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void restore(String key, String value) {
        if (value == null) prefs.remove(key);
        else prefs.put(key, value);
    }
}

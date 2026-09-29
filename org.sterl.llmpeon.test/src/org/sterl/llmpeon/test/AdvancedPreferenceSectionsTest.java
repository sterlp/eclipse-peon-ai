package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.UUID;

import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.Test;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.LlmConfigLoader;
import org.sterl.llmpeon.ai.LlmConfigSaver;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.config.AiAdvancedPreferenceView;
import org.sterl.llmpeon.parts.config.EclipseLlmConfigStore;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;

public class AdvancedPreferenceSectionsTest {

    // UC-DEF-10
    @Test
    public void devSectionIsGoneAndSlotOrderStable() {
        var sections = AiAdvancedPreferenceView.AGENT_SECTIONS;

        assertEquals(List.of("po", "plan", "search", "compact"),
                sections.stream().map(AiAdvancedPreferenceView.AgentSection::id).toList());
        sections.forEach(section -> assertFalse(section.title().isBlank()));
        sections.forEach(section -> assertFalse(section.title().contains("Dev")));
    }

    @Test
    public void everyCoreSlotHasASection() {
        // Single owner (ADR-0063): dev is owned by the basic page — this page covers the overrides only.
        var overrideIds = AgentModelConfig.CORE_IDS.stream()
                .filter(id -> !AgentModelConfig.DEV.equals(id))
                .toList();
        var sectionIds = AiAdvancedPreferenceView.AGENT_SECTIONS.stream()
                .map(AiAdvancedPreferenceView.AgentSection::id)
                .toList();

        assertEquals(overrideIds.size(), sectionIds.size());
        assertEquals(overrideIds.stream().distinct().count(), sectionIds.stream().distinct().count());
        assertEquals(overrideIds.stream().sorted().toList(), sectionIds.stream().sorted().toList());
    }

    @Test
    public void legacyTemperaturePreferencesRemoved() {
        new LlmPreferenceInitializer().initializeDefaultPreferences();
        var defaults = DefaultScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);

        assertNull(defaults.get("llm.planTemperature", null));
        assertNull(defaults.get("llm.devTemperature", null));
    }

    @Test
    public void temperatureRoundTripsThroughEclipseStore() throws Exception {
        var scratch = InstanceScope.INSTANCE.getNode(
                PeonConstants.PLUGIN_ID + ".temperature-test-" + UUID.randomUUID());
        try {
            var store = new EclipseLlmConfigStore(scratch);
            var expected = new AgentModelConfig(null, null, null, null, null, "0.42");

            LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.PLAN, expected);
            var loaded = LlmConfigLoader.load(store).modelConfigFor(AgentModelConfig.PLAN);

            assertEquals(expected.temperature(), loaded.temperature());
        } finally {
            scratch.removeNode();
        }
    }
}

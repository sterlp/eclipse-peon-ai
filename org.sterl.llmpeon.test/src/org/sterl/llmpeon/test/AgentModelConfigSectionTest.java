package org.sterl.llmpeon.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.LlmConfigLoader;
import org.sterl.llmpeon.ai.LlmConfigSaver;
import org.sterl.llmpeon.ai.LlmConfigStore;
import org.sterl.llmpeon.parts.config.widgets.AgentModelConfigSection;

/**
 * Workbench-display test for the per-agent {@link AgentModelConfigSection} (R-A4): the
 * think-Values field must be a native editable {@link Combo}, and the Ollama toggle must be an
 * editable combo with the fixed items {@code ""}/{@code true}/{@code false} whose value is stored
 * verbatim (ADR-0059, Issue #149). No fetch is triggered — the constructor only builds widgets,
 * so the mock server just supplies a URL. Display/Shell lifecycle and UI-thread access come from
 * {@link AbstractSwtUiTest}.
 */
public class AgentModelConfigSectionTest extends AbstractSwtUiTest {

    private AgentModelConfigSection section;
    private AgentModelConfigSection ollamaSection;

    @Before
    public void buildSection() {
        section = ui(() -> newSection(AiProvider.ANTHROPIC));
    }

    @After
    public void disposeSection() {
        if (section != null) {
            ui(() -> {
                section.dispose();
                return null;
            });
        }
        section = null;
        if (ollamaSection != null) {
            ui(() -> {
                ollamaSection.dispose();
                return null;
            });
        }
        ollamaSection = null;
    }

    @Test
    public void thinkValuesFieldIsNativeEditableCombo() {
        // GIVEN a section with a Values-form provider (Anthropic: adaptive/enabled)
        // WHEN built
        var combo = ui(this::findThinkCombo);

        // THEN the think field is a native editable Combo (not a CCombo), items [Off, Auto, adaptive, enabled], default Off
        assertTrue("think field must be a native Combo, was " + combo.getClass().getName(),
                combo instanceof Combo);
        assertTrue("think combo must be editable (no SWT.READ_ONLY)", (combo.getStyle() & SWT.READ_ONLY) == 0);
        assertArrayEquals(new String[] { "Off", "Auto", "adaptive", "enabled" }, combo.getItems());
        assertEquals("Off", combo.getText());
    }

    @Test
    public void unknownThinkValueLoadsVerbatim() {
        // GIVEN a stored think value that is not in the provider's list
        ui(() -> {
            section.load(new AgentModelConfig(null, null, "claude-x", "ultra", null, null));
            return null;
        });

        // THEN the combo shows the value verbatim
        assertEquals("ultra", ui(this::findThinkCombo).getText());
    }

    @Test
    public void thinkValueRoundtripsThroughRecord() {
        var combo = ui(this::findThinkCombo);

        // GIVEN free text in the combo
        ui(() -> {
            combo.setText("custom-level");
            return null;
        });

        // WHEN read back, the value roundtrips verbatim
        assertEquals("custom-level", ui(() -> section.getRecord().think()));

        // AND known values map to their stored forms
        ui(() -> {
            combo.select(1); // Auto
            return null;
        });
        assertEquals("true", ui(() -> section.getRecord().think()));
        ui(() -> {
            combo.select(2); // adaptive
            return null;
        });
        assertEquals("adaptive", ui(() -> section.getRecord().think()));
        ui(() -> {
            combo.select(0); // Off
            return null;
        });
        assertEquals("", ui(() -> section.getRecord().think()));
    }

    // UC-THINK-4
    @Test
    public void issue149_falseSurvivesUiEncodeAndPersistence() {
        // GIVEN an Ollama section — the think form is an editable combo, no checkbox (Issue #149)
        ollamaSection = ui(() -> newSection(AiProvider.OLLAMA));
        var combo = ui(this::findOllamaThinkCombo);
        assertTrue("think toggle combo must be editable (no SWT.READ_ONLY)",
                (combo.getStyle() & SWT.READ_ONLY) == 0);
        assertArrayEquals(new String[] { "", "true", "false" }, combo.getItems());
        ui(() -> {
            assertNoThinkCheckbox();
            return null;
        });

        // WHEN the explicit off value is selected
        ui(() -> {
            combo.setText("false");
            return null;
        });

        // THEN the record the UI produced carries "false" verbatim (stored value = displayed value)
        var rec = ui(() -> ollamaSection.getRecord());
        assertEquals("false", rec.think());

        // AND the value survives Saver → Store → Loader — no collapse to unset (Issue #149)
        var store = new MapStore();
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.DEV, rec);
        assertEquals("false", LlmConfigLoader.load(store).modelConfigFor(AgentModelConfig.DEV).think());
    }

    // UC-THINK-4
    @Test
    public void emptyToggleIsUnsetAndNotPersisted() {
        // GIVEN an Ollama section with the unset (empty) value
        ollamaSection = ui(() -> newSection(AiProvider.OLLAMA));
        ui(() -> {
            findOllamaThinkCombo().setText("");
            return null;
        });

        // WHEN read back and saved
        var rec = ui(() -> ollamaSection.getRecord());
        var store = new MapStore();
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.DEV, rec);

        // THEN nothing is persisted — unset stays unset
        assertTrue("unset think must not be persisted, was " + store.asMap(), store.asMap().isEmpty());
    }

    // --- helpers ---

    /** UI-thread only. Builds a section for the given base provider; the mock server only supplies the URL. */
    private AgentModelConfigSection newSection(AiProvider provider) {
        var base = LlmConfig.builder().providerType(provider).model("claude-x")
                .url(mockLlmServer.getUrl()).timeout(Duration.ofSeconds(30)).build();
        return new AgentModelConfigSection(shell, "test", () -> base);
    }

    /**
     * UI-thread only. The think-Values combo: the first Combo child whose items contain
     * "adaptive" (the model combo has no items before a fetch and never matches).
     */
    private Combo findThinkCombo() {
        for (var child : section.getChildren()) {
            if (child instanceof Combo c && List.of(c.getItems()).contains("adaptive")) return c;
        }
        throw new AssertionError("no think combo (Values form) in section");
    }

    /** UI-thread only. The Ollama think-toggle combo: the Combo child with the fixed items ""/true/false. */
    private Combo findOllamaThinkCombo() {
        for (var child : ollamaSection.getChildren()) {
            if (child instanceof Combo c && List.of(c.getItems()).equals(List.of("", "true", "false"))) return c;
        }
        throw new AssertionError("no think toggle combo (items \"\"/true/false) in Ollama section");
    }

    /** UI-thread only. Issue #149: the Ollama section must not contain a think checkbox (CHECK-style button). */
    private void assertNoThinkCheckbox() {
        for (var child : ollamaSection.getChildren()) {
            if (child instanceof Button b) {
                assertFalse("no think checkbox may remain in the Ollama section, found " + b,
                        (b.getStyle() & SWT.CHECK) != 0);
            }
        }
    }

    /** In-memory {@link LlmConfigStore} double (the core test double is not on the OSGi test classpath). */
    private static final class MapStore implements LlmConfigStore {
        private final Map<String, String> map = new HashMap<>();

        @Override
        public String get(String key, String defaultValue) {
            return map.getOrDefault(key, defaultValue);
        }

        @Override
        public void put(String key, String value) {
            map.put(key, value);
        }

        @Override
        public void remove(String key) {
            map.remove(key);
        }

        Map<String, String> asMap() {
            return map;
        }
    }
}

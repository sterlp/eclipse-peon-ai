package org.sterl.llmpeon.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.config.widgets.AgentModelConfigSection;

/**
 * Workbench-display test for the per-agent {@link AgentModelConfigSection} (R-A4): the
 * think-Values field must be a native editable {@link Combo}. No fetch is triggered — the
 * constructor only builds widgets, so the mock server just supplies a URL. Display/Shell
 * lifecycle and UI-thread access come from {@link AbstractSwtUiTest}.
 */
public class AgentModelConfigSectionTest extends AbstractSwtUiTest {

    private AgentModelConfigSection section;

    @Before
    public void buildSection() {
        section = ui(this::newSection);
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

    // --- helpers ---

    /** UI-thread only. Anthropic base → ThinkSupport.Values; the mock server only supplies the URL. */
    private AgentModelConfigSection newSection() {
        var base = LlmConfig.builder().providerType(AiProvider.ANTHROPIC).model("claude-x")
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
}

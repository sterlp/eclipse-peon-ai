package org.sterl.llmpeon.parts.config.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.provider.ThinkSupport;

/**
 * SWT-free think-value mapping (provider.md R5) + extra-body gate (provider.md R3). No Display
 * needed — the helper is stateless.
 */
public class ThinkValueSupportTest {

    // --- Boolean form ---

    @Test
    public void booleanOnOffMaps() {
        assertEquals("true", ThinkValueSupport.booleanValue(true));
        assertEquals("", ThinkValueSupport.booleanValue(false));
    }

    @Test
    public void booleanOnDetectsStoredValue() {
        assertTrue(ThinkValueSupport.booleanOn("true"));
        assertFalse(ThinkValueSupport.booleanOn(""));
        assertFalse(ThinkValueSupport.booleanOn(null));
        assertFalse(ThinkValueSupport.booleanOn("false"));
    }

    // --- Values form ---

    @Test
    public void valuesDropdownOffersOffAutoAndValues() {
        var values = new ThinkSupport.Values(List.of("none", "minimal", "low", "medium", "high", "xhigh"));
        assertEquals(List.of("Off", "Auto", "none", "minimal", "low", "medium", "high", "xhigh"),
                ThinkValueSupport.valuesItems(values));
    }

    @Test
    public void valuesSelectionMapsToStored() {
        assertEquals("", ThinkValueSupport.valuesStored("Off"));
        assertEquals("true", ThinkValueSupport.valuesStored("Auto"));
        assertEquals("high", ThinkValueSupport.valuesStored("high"));
    }

    @Test
    public void valuesStoredMapsToDisplay() {
        assertEquals("Off", ThinkValueSupport.valuesDisplay(""));
        assertEquals("Off", ThinkValueSupport.valuesDisplay(null));
        assertEquals("Auto", ThinkValueSupport.valuesDisplay("true"));
        assertEquals("high", ThinkValueSupport.valuesDisplay("high"));
    }

    @Test
    public void unknownValueDisplaysVerbatim() {
        assertEquals("custom-level", ThinkValueSupport.valuesDisplay("custom-level"));
    }

    // --- extra-body gate (provider.md R3) ---

    @Test
    public void extraBodyVisibleForOpenAi() {
        assertTrue(ThinkValueSupport.extraBodyVisible(AiProvider.OPEN_AI));
    }

    @Test
    public void extraBodyVisibleForAnthropic() {
        assertTrue(ThinkValueSupport.extraBodyVisible(AiProvider.ANTHROPIC));
    }

    @Test
    public void extraBodyHiddenForOllama() {
        assertFalse(ThinkValueSupport.extraBodyVisible(AiProvider.OLLAMA));
    }
}

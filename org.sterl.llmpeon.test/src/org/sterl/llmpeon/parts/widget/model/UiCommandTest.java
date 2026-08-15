package org.sterl.llmpeon.parts.widget.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson serialization of UiCommand hierarchy — verifies JSON structure consumed by chat.html.
 */
public class UiCommandTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldSerializeLiveStatusCommand() throws Exception {
        // GIVEN a live status update command
        var cmd = new LiveStatusCommand("thinking...", 30.0, "Analyzing...");

        // WHEN serialized via Jackson
        String json = mapper.writeValueAsString(cmd);

        // THEN JSON contains type field + record components
        assertTrue(json, json.contains("\"type\":\"updateLiveResponse\""));
        assertTrue(json, json.contains("\"state\":\"thinking...\""));
        assertTrue(json, json.contains("\"tokPerSec\":30.0"));
        assertTrue(json, json.contains("\"chunk\":\"Analyzing...\""));
    }

    @Test
    public void shouldSerializeSetThemeCommandLight() throws Exception {
        // GIVEN the LIGHT constant
        var cmd = SetThemeCommand.LIGHT;

        // WHEN serialized
        String json = mapper.writeValueAsString(cmd);

        // THEN type is "setTheme" and theme is "light"
        assertEquals("setTheme", cmd.type());
        assertTrue(json, json.contains("\"type\":\"setTheme\""));
        assertTrue(json, json.contains("\"theme\":\"light\""));
    }

    @Test
    public void shouldSerializeSetThemeCommandDark() throws Exception {
        // GIVEN the DARK constant
        var cmd = SetThemeCommand.DARK;

        // WHEN serialized
        String json = mapper.writeValueAsString(cmd);

        // THEN JSON contains "dark" theme
        assertTrue(json, json.contains("\"theme\":\"dark\""));
    }

    @Test
    public void shouldSerializeHideLiveStatusCommand() throws Exception {
        // GIVEN the singleton INSTANCE
        var cmd = HideLiveStatusCommand.INSTANCE;

        // WHEN serialized
        String json = mapper.writeValueAsString(cmd);

        // THEN JSON is minimal — only the type field
        assertEquals("hideLiveStatus", cmd.type());
        assertTrue(json, json.contains("\"type\":\"hideLiveStatus\""));
        // No other fields
        assertTrue("no state field", !json.contains("state"));
        assertTrue("no chunk field", !json.contains("chunk"));
        assertTrue("no theme field", !json.contains("theme"));
    }

    @Test
    public void hideLiveStatusCommandIsSingleton() {
        // GIVEN two references to INSTANCE
        var ref1 = HideLiveStatusCommand.INSTANCE;
        var ref2 = HideLiveStatusCommand.INSTANCE;

        // THEN they are the same object
        assertSame(ref1, ref2);
    }

    @Test
    public void shouldReturnCorrectTypeValues() {
        // WHEN type() is called on each command
        assertEquals("setTheme", SetThemeCommand.LIGHT.type());
        assertEquals("setTheme", SetThemeCommand.DARK.type());
        assertEquals("updateLiveResponse", new LiveStatusCommand("idle", 0, "").type());
        assertEquals("hideLiveStatus", HideLiveStatusCommand.INSTANCE.type());
    }
}

package org.sterl.llmpeon.parts.widget.model;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The chat payload exactly as chat.html receives it: the AI heading is
 * {@code content: attr(data-agent)} fed by {@code message.origin}, so that field has to survive the
 * serialization {@link ChatMarkdownWidget} performs. A renamed or dropped field would silently
 * restore the old, nameless chat.
 */
public class SimpleMessageOriginJsonTest {

    /** Same configuration as {@code ChatMarkdownWidget}'s mapper — a plain default one. */
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void stampedLineCarriesItsSpeakingAgent() throws Exception {
        // GIVEN a line the core stamped with its agent
        var msg = new SimpleMessage(Type.AI, "Erledigt.", "Dev");

        // WHEN serialized for the browser
        String json = mapper.writeValueAsString(msg);

        // THEN the browser can read the speaker out of it
        assertTrue(json, json.contains("\"role\":\"AI\""));
        assertTrue(json, json.contains("\"message\":\"Erledigt.\""));
        assertTrue(json, json.contains("\"origin\":\"Dev\""));
    }

    @Test
    public void unstampedLineSaysSoInsteadOfInventingAnAgent() throws Exception {
        // GIVEN a line produced outside an agent turn (2-arg form)
        var msg = new SimpleMessage(Type.AI, "Ohne Absender.");

        // WHEN serialized for the browser
        String json = mapper.writeValueAsString(msg);

        // THEN origin is present but null — the view shows no heading for it
        assertTrue(json, json.contains("\"origin\":null"));
    }
}

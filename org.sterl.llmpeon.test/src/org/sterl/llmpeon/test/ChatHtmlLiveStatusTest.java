package org.sterl.llmpeon.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.junit.Test;
import org.osgi.framework.FrameworkUtil;
import org.sterl.llmpeon.parts.widget.ChatMarkdownWidget;

/**
 * Rule 9 (org.sterl.llmpeon/docs/streaming-display.md): an appended chat message is not the end of
 * the work, so it must not close the live status any more — only the natural end points do (END
 * chunk, turn/lock end, and the explicit QUESTION widget). Asserted on the shipped
 * {@code resources/chat/chat.html} itself: the browser is the implementation, so the file IS the
 * behaviour, and a Java-side test could only re-state the intent.
 */
public class ChatHtmlLiveStatusTest extends AbstractUnitTest {

    // R9: appendMessage versteckt den Live-Status nicht, appendDiff und der explizite Befehl tun es weiterhin
    @Test
    public void appendMessageNoLongerHidesTheLiveStatus() throws IOException {
        var html = chatHtml();

        // THEN — appending a message leaves the status alone …
        var appendMessage = functionBody(html, "appendMessage");
        assertTrue("appendMessage must stop hiding the live status:\n" + appendMessage,
                !appendMessage.contains("hideLiveStatus"));
        // AND — a diff IS a final result, so it keeps closing the status …
        assertTrue("appendDiff must keep hiding the live status:\n" + functionBody(html, "appendDiff"),
                functionBody(html, "appendDiff").contains("hideLiveStatus()"));
        // AND — the Java-side command channel and the mechanism itself stay untouched: only the
        // call from appendMessage was removed, not the ability to hide.
        assertTrue("explicit hide command must stay:\n" + html, html.contains("case \"hideLiveStatus\":"));
        assertTrue("hideLiveStatus must stay defined:\n" + html, html.contains("function hideLiveStatus()"));
    }

    // ========== fixture ==========

    /** The shipped html of the host bundle, read the way the widget locates it. */
    private static String chatHtml() throws IOException {
        var url = FileLocator.find(FrameworkUtil.getBundle(ChatMarkdownWidget.class),
                new Path("resources/chat/chat.html"), null);
        assertNotNull("chat.html must ship inside the bundle", url);
        try (var in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * One function, from its signature to the closing brace at the file's function indent.
     * Deliberately dumb: a chat.html reformat that moves that indent fails here loudly instead of
     * quietly testing nothing.
     */
    private static String functionBody(String html, String name) {
        var start = html.indexOf("function " + name + "(");
        assertTrue("function " + name + "( not found in chat.html", start >= 0);
        var end = html.indexOf("\n        }", start);
        assertTrue("closing brace of function " + name + " not found — update the extractor to the new format",
                end > start);
        var body = html.substring(start, end);
        assertTrue("body of " + name + " looks unrealistically short:\n" + body, body.length() > 80);
        return body;
    }
}

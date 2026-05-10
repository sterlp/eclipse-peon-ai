package org.sterl.llmpeon.parts.widget;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.osgi.framework.FrameworkUtil;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.streaming.ThinkingBuffer;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.ToSimpleMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ChatMessage;

public class ChatMarkdownWidget extends Composite {

    private final Browser browser;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private final ThinkingBuffer thinkingBuffer = new ThinkingBuffer(10);
    private String chatHtml = null;

    public ChatMarkdownWidget(Composite parent, int style) {
        super(parent, style);
        setLayout(new FillLayout());

        browser = new Browser(this, SWT.NONE);
        clear();
    }
    
    public void hideLiveStatus() {
        browser.execute("hideLiveStatus();");
    }

    private String loadChatHtml() {
        if (chatHtml != null) return chatHtml;
        try (InputStream is = getClass().getResourceAsStream("/resources/chat/chat.html")) {
            if (is == null) {
                throw new RuntimeException("chat.html not found on classpath");
            }
            var loaded = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            chatHtml = resolveResourcePaths(loaded);
            return chatHtml;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load chat.html", e);
        }
    }

    /**
     * Replaces all relative {@code ./} paths in the HTML with absolute file:// URLs
     * so the embedded browser can load CSS, JS, and language files.
     */
    private String resolveResourcePaths(String html) throws IOException {
        URL chatDir = FileLocator.find(
                FrameworkUtil.getBundle(getClass()),
                new Path("resources/chat/"),
                null
        );
        if (chatDir == null) {
            throw new IOException("resources/chat/ directory not found in bundle");
        }
        String basePath = FileLocator.toFileURL(chatDir).toString();
        // all resources use ./ relative paths, so a single replace resolves everything
        return html.replace("./", basePath);
    }

    public void appendMessage(SimpleMessage msg) {
        try {
            browser.execute(
                    "appendMessage(" + mapper.writeValueAsString(msg) + ");"
                    );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void onStreamingChunk(OnPartialAiResponse r, long elapsedSeconds, double tokPerSec) {
        String state = switch (r.type()) {
            case WAITING -> { thinkingBuffer.clear(); yield "waiting for AI..."; }
            case THINK   -> "working since " + elapsedSeconds + "s | thinking...";
            case ANSWER  -> "working since " + elapsedSeconds + "s | responding...";
            case TOOL    -> "working since " + elapsedSeconds + "s | using tools...";
        };
        String thinkChunk = r.type() == OnPartialAiResponse.Type.THINK
                ? thinkingBuffer.append(r.value()) : null;

        String safeState = state == null ? "" : state.replace("'", "\\'");
        String safeChunk = thinkChunk == null ? "" : thinkChunk.replace("'", "\\'").replace("\n", "<br>");
        browser.execute("updateLiveResponse('" + safeState + "', " + tokPerSec + ", '" + safeChunk + "');");
    }

    public void showDiff(String unifiedDiff) {
        try {
            browser.execute(
                "appendDiff(" + mapper.writeValueAsString(unifiedDiff) + ");"
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Reload the while view - clean everything away ....
     */
    public void clear() {
        browser.setText(loadChatHtml());
    }

    /**
     * Just removes the messages
     */
    public void clearMessages() {
        browser.execute("clearMessages()");
    }

    public void appendMessage(ChatMessage msg) {
        var toAdd = ToSimpleMessage.INSTANCE.convert(msg);
        toAdd.forEach(this::appendMessage);
    }
}

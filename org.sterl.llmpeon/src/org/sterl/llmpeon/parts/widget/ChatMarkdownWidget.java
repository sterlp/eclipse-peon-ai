package org.sterl.llmpeon.parts.widget;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.osgi.framework.FrameworkUtil;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.OnPartialAiResponse.Type;
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
    
    private final AtomicInteger streamingTokenCount = new AtomicInteger(0);
    private final Composite parent;

    public ChatMarkdownWidget(Composite parent, int style) {
        super(parent, style);
        this.parent = parent;
        setLayout(new FillLayout());

        browser = new Browser(this, SWT.NONE);
        clear();
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
            browser.execute("appendMessage(" + mapper.writeValueAsString(msg) + ");");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void hideLiveStatus() {
        browser.execute("hideLiveStatus();");
    }

    public void onStreamingChunk(OnPartialAiResponse r) {
        int tokens = 0;
        if (r.type() == Type.START || r.type() ==  Type.END) {
            streamingTokenCount.set(0);
        } else {
            tokens = streamingTokenCount.incrementAndGet();
        }
        
        if (r.type() == Type.END) {
            EclipseUtil.runInUiThread(parent, this::hideLiveStatus);
        } else {
            updateRunningChunk(r, tokens);
        }
    }

    private void updateRunningChunk(OnPartialAiResponse r, int tokens) {
        String safeChunk = "";
        if (r.type() == Type.THINK) {
            if (r.value() != null) safeChunk = thinkingBuffer.append(r.value().replace("'", "\\'").replace("\n", "<br>"));
        } else {
            thinkingBuffer.clear();
        }
        
        long elapsed = Duration.between(r.startedAt(), Instant.now()).toSeconds();
        String state = switch (r.type()) {
            case START   -> "waiting for AI...";
            case THINK   -> "working since " + elapsed + "s | thinking...";
            case ANSWER  -> "working since " + elapsed + "s | responding...";
            case TOOL    -> "working since " + elapsed + "s | using tools...";
            case END     -> "done.";
        };
        double tokPerSec = elapsed > 0 ? tokens / (double) elapsed : 0;
        updateLiveResponseInUIThread(state, tokPerSec, safeChunk);
    }
    
    public void updateLiveResponseInUIThread(String state, double tokPerSec, String safeChunk) {
        EclipseUtil.runInUiThread(parent, () -> browser.execute("updateLiveResponse('" + state + "', " + tokPerSec + ", '" + safeChunk + "');"));
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
        hideLiveStatus();
        browser.execute("clearMessages()");
    }

    public void appendMessage(ChatMessage msg) {
        var toAdd = ToSimpleMessage.INSTANCE.convert(msg);
        toAdd.forEach(this::appendMessage);
    }
}

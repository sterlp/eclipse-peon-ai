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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

public class ChatMarkdownWidget extends Composite {

    private final Browser browser;
    private final ObjectMapper mapper = new ObjectMapper();
    
    public record SimpleChatMessage(String role, String message) {};

    public ChatMarkdownWidget(Composite parent, int style) {
        super(parent, style);
        setLayout(new FillLayout());

        browser = new Browser(this, SWT.NONE);
        try (InputStream is = getClass().getResourceAsStream("/resources/chat/chat.html")) {
            if (is == null) {
                throw new RuntimeException("chat.html not found on classpath");
            }
            var chatHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            chatHtml = resolveResourcePaths(chatHtml);
            browser.setText(chatHtml);
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

    public void appendMessage(SimpleChatMessage msg) {
        try {
            browser.execute(
                "appendMessage(" + mapper.writeValueAsString(msg) + ");"
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void clear() {
        browser.execute("clearMessages();");
    }

    public void appendMessage(ChatMessage msg) {
        appendMessage(new SimpleChatMessage(getToolTipText(), getToolTipText()));
        if (msg instanceof UserMessage um) {
            appendMessage(new SimpleChatMessage(msg.type() + "", um.singleText()));
        } else if (msg instanceof AiMessage am) {
            if (am.thinking() != null) {
                appendMessage(new SimpleChatMessage("think", am.thinking()));
            }
            appendMessage(new SimpleChatMessage(msg.type() + "", am.text()));
        }
    }
}

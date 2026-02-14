package org.sterl.llmpeon.parts.widget;

import java.io.IOException;
import java.net.URL;

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
        URL url = FileLocator.find(
                FrameworkUtil.getBundle(getClass()),
                new Path("resources/chat/chat.html"),
                null
            );

        try {
            browser.setUrl(FileLocator.toFileURL(url).toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + url, e);
        }
    }

    public void appendMessage(SimpleChatMessage msg) {
        //logger.debug(msg + "");
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

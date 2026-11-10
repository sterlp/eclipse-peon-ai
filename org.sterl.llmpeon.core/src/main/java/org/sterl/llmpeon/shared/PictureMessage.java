package org.sterl.llmpeon.shared;

import java.util.List;

import org.sterl.llmpeon.agent.model.TaggedResult;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

/**
 * Builds AND parses the picture user message (R-RP-1) — one class, so builder and parser cannot
 * drift apart. Shape: {@code TextContent(tagged head with the path)} + {@code ImageContent}.
 * {@link ImageContent} carries no path itself, the head does.
 */
public final class PictureMessage {

    private static final String HEAD_TAG = "picture: ";
    private static final String HEAD_PREFIX = TaggedResult.PREFIX + HEAD_TAG;
    private static final String UNKNOWN_PATH = "unknown";

    private PictureMessage() {}

    public static UserMessage of(Picture p) {
        var head = new TaggedResult(HEAD_TAG + p.displayPath(),
                "added by the picture tool, not typed by the user").toString();
        return UserMessage.from(List.<Content>of(TextContent.from(head), ImageContent.from(p.base64(), p.mime(), ImageContent.DetailLevel.HIGH)));
    }

    /** @return true if the text is the head of a picture message — harness state, never real user text. */
    public static boolean isHead(String userText) {
        return userText != null && userText.startsWith(HEAD_PREFIX);
    }

    /** @return the path from the first picture head of the message, {@code "unknown"} if there is none. */
    public static String pathOf(UserMessage m) {
        for (var c : m.contents()) {
            if (c instanceof TextContent tc && isHead(tc.text())) {
                return tc.text().lines().findFirst().orElse("").substring(HEAD_PREFIX.length());
            }
        }
        return UNKNOWN_PATH;
    }
}

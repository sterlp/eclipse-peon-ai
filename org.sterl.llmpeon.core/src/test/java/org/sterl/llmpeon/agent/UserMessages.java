package org.sterl.llmpeon.agent;

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

/** Test-side access to the {@link UserMessage} content blocks (context-message-concept.md layout). */
final class UserMessages {

    private UserMessages() {}

    /** The LAST TextContent — the real-user-text slot of a user message. */
    static String lastText(UserMessage um) {
        return um.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("the user message has no TextContent at all"));
    }
}

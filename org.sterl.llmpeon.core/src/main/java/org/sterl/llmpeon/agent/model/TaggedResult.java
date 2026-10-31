package org.sterl.llmpeon.agent.model;

import lombok.RequiredArgsConstructor;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Default tagged result which can be added to any tool call or comes alone with tags
 * tellings the LLM that this is a tool result an no user input. 
 */
@RequiredArgsConstructor
public class TaggedResult {

    public static final String LN = System.lineSeparator();
    public static final String PREFIX = "//-- Start ";
    public static final String SUFFIX = "//-- End ";
    public static final String TAG_QUEUED_USER_MESSAGE = "queued_user_message";

    /**
     * The short INFO for the static system prompt (R-TR-6): the example block teaches the format,
     * the last sentence is the core (addressee). 5 lines, no code fence, no trailing LN — like
     * the block itself.
     */
    public static final String INFO =
            "Harness blocks (system infos, async results, tool results) look like this:" + LN
            + PREFIX + "<head>" + LN
            + "<body>" + LN
            + SUFFIX + "<head>" + LN
            + "They are added as additional informations to tool calls or user messages." + LN
            + "A 'queued_user_message' is a real user message that arrived while work was in progress.";

    private final String head;
    private final String body;

    public String head() {
        return head;
    }

    public String body() {
        return body;
    }

    /**
     * A PURE tagged block (R-TR-2): the FIRST line starts with {@link #PREFIX} and the LAST line
     * starts with {@link #SUFFIX}. Checked line-wise — never {@code endsWith} over the whole text,
     * because the end line carries the head (R-TR-1).
     */
    public static boolean isTagged(String text) {
        if (StringUtil.hasNoValue(text)) return false;
        int first = text.indexOf('\n');
        int last = text.lastIndexOf('\n');
        if (first < 0 || last <= first) return false;          // 1 line / no end
        return text.substring(0, first).startsWith(PREFIX)      // FIRST line
                && text.substring(last + 1).startsWith(SUFFIX); // LAST line
    }

    @Override
    public String toString() {
        return PREFIX + head + LN + body + LN + SUFFIX + head;
    }
}

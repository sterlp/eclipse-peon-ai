package org.sterl.llmpeon.tool;

/**
 * Keys of the {@link dev.langchain4j.data.message.ToolExecutionResultMessage#attributes()} a tool may
 * attach to its own result (see
 * {@link org.sterl.llmpeon.tool.tools.AbstractTool#attachResultAttribute(String, Object)}). Attributes
 * never reach the model, but they do survive a history round-trip
 * ({@link ToolResultAttributesRoundTripTest}), so a consumer can read a fact back out of the existing
 * history instead of parsing model-facing text or keeping a second store.
 */
public final class ToolResultAttributes {

    /** Core id ({@code AiAgent.getName()}) of the team member that produced the delegated answer. */
    public static final String DELEGATED_AGENT = "delegatedAgent";

    /** The member's answer text, pure — without the status line the model additionally receives. */
    public static final String DELEGATED_ANSWER = "delegatedAnswer";

    private ToolResultAttributes() {}
}

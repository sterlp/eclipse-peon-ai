package org.sterl.llmpeon.agent.model;

import java.util.List;

import dev.langchain4j.data.message.Content;

/**
 * Anything that goes into a user message (R-TR-10/11). {@link #toContents()} is the only place
 * that builds {@code TextContent}/{@code ImageContent} for such input.
 */
public interface Input {

    List<Content> toContents();
}

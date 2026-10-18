package org.sterl.llmpeon.agent.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Default tagged result which can be added to any tool call or comes alone with tags
 * tellings the LLM that this is a tool result an no user input. 
 */
@RequiredArgsConstructor
@Getter
public class TaggedResult {

    public static final String LN = System.lineSeparator();
    public static final String PREFIX = "//-- Start ";
    public static final String SUFFIX = "//-- End ";
    
    public static final String INFO = 
            """
            **Explanation system infos and async results** 
            The harness uses the following tags to attach additional informations to user messages or tool results e.g. async agent responses or other informations:
            """ + LN
            + "```" + LN
            + PREFIX + "result head" + LN
            + "result body" + LN
            + SUFFIX + LN + "```" + LN;
    
    private final String head;
    private final String body;
    
    @Override
    public String toString() {
        return PREFIX + head + LN + body + LN + SUFFIX;
    }
}

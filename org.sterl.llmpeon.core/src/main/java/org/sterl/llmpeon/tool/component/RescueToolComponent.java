package org.sterl.llmpeon.tool.component;

import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * https://github.com/sterlp/eclipse-peon-ai/issues/163
 * <tool_call> <function=Read> <parameter=file_path> /Users/sterlp/dev/workset/peon-ai/aifactory/peon-plan/review-findings.md </parameter> <parameter=limit> 5 </parameter> </function> </tool_call>
 */
public class RescueToolComponent {

    public final static String TOOL_PREFIX = "<tool_call>";
    public final static String TOOL_SUFFIX = "</tool_call>";
    
    public final static UserMessage AI_INFO = UserMessage.from("Invalid tool call. Use the supported tool-call protocol and call only one of the available tools.");

    public static boolean isToolCall(AiMessage message) {
        var msg = StringUtil.trimToEmpty(message.text()).toLowerCase();
        return msg.startsWith(TOOL_PREFIX) && msg.endsWith(TOOL_SUFFIX);
    }
}

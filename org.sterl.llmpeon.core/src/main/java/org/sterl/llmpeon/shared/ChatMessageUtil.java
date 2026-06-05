package org.sterl.llmpeon.shared;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

public class ChatMessageUtil {

    public static String toString(ChatMessage msg) {
        var result = new StringBuilder();
        if (msg instanceof UserMessage m) {
            if (m.hasSingleText()) {
                result.append(m.singleText());
            } else {
                m.contents().forEach(s -> result.append(s).append("\n"));
            }
        } else if (msg instanceof AiMessage m) {
            if (StringUtil.hasValue(m.text())) {
                result.append(m.text()).append("\n");
            }
            if (StringUtil.hasValue(m.thinking())) {
                result.append("Think:\n").append(m.thinking()).append("\n");
            }
            if (m.hasToolExecutionRequests()) {
                for (var tr : m.toolExecutionRequests()) {
                    result.append("\ntool name:  ").append(tr.name())
                          .append("\ntool id:    ").append(tr.id())
                          .append("\narguments:  ").append(tr.arguments());
                }
            }
        } else if (msg instanceof ToolExecutionResultMessage tr && tr.hasSingleText()) {
            result.append("\ntool result for id: ").append(tr.id())
                  .append("\n").append(tr.text()).append("\n");
        }
        return result.toString().strip();
    }
}

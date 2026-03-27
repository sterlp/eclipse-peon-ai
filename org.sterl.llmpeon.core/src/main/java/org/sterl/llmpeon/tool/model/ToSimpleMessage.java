package org.sterl.llmpeon.tool.model;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

public enum ToSimpleMessage{

    INSTANCE;
    
    public List<SimpleMessage> convert(ChatMessage msg) {
        var result = new ArrayList<SimpleMessage>();
        var role = msg.type().toString().contains("TOOL") ? Type.TOOL : Type.AI;

        String text = "";
        if (msg instanceof UserMessage um) {
            role = Type.USER;
            text = um.singleText();
        } else if (msg instanceof AiMessage am) {
            if (StringUtil.hasValue(am.thinking())) {
                result.add(new SimpleMessage(Type.THINK, am.thinking()));
            }
            role = Type.AI;
            text = am.text();
        } else if (msg instanceof ToolExecutionResultMessage tr) {
            role = Type.TOOL;
            text = tr.text();
        }
        if (text != null) text = text.trim();
        
        if (StringUtil.hasValue(text)) result.add(new SimpleMessage(role, text));
        return result;
    }
}

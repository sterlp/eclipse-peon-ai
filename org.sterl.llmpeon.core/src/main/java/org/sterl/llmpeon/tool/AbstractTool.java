package org.sterl.llmpeon.tool;

import java.util.List;

import org.sterl.llmpeon.agent.AiMonitor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;

public class AbstractTool implements SmartTool {

    protected AiMonitor monitor;
    protected ChatModel chatModel;
    protected List<ChatMessage> memory;
    
    @Override
    public void withMonitor(AiMonitor monitor) {
        this.monitor = monitor;
    }
    
    protected boolean hasMonitor() { return monitor != null; }
    
    protected void monitorMessage(String m) {
        if (hasMonitor()) monitor.onAction(m);
    }

    protected void onProblem(String m) {
        if (hasMonitor()) monitor.onProblem(m);
    }

    @Override
    public void withChatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public void withMemory(List<ChatMessage> memory) {
        this.memory = memory;
    }

}

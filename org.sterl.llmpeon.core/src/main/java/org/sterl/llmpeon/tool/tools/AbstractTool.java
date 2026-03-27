package org.sterl.llmpeon.tool.tools;

import java.util.List;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.SmartTool;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;

public class AbstractTool implements SmartTool {

    protected AiMonitor monitor = AiMonitor.NULL_MONITOR;
    protected ChatModel chatModel;
    // TODO 27.03.2026: not sure if this was really smart here - re-think later on
    protected List<ChatMessage> memory;
    
    @Override
    public void withMonitor(AiMonitor monitor) {
        this.monitor = AiMonitor.nullSafety(monitor);
    }
    
    protected void onTool(String m) {
        monitor.onTool(m);
    }

    protected void onProblem(String m) {
        monitor.onProblem(m);
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

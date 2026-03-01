package org.sterl.llmpeon.tool;

import org.sterl.llmpeon.ChatService;
import org.sterl.llmpeon.agent.AiMonitor;

import dev.langchain4j.agent.tool.Tool;

public class CompressorAgentTool extends AbstractTool {

    private final ChatService chatService;

    public CompressorAgentTool(ChatService chatService) {
        this.chatService = chatService;
    }

    @Tool(name = "CompressorAgentTool", value = """
            Compresses the current conversation history to free up context space.
            Call this when the conversation is getting long and you want to preserve
            key information in a more compact form before continuing the task.
            Returns a summary of the compressed content.
            """)
    public String compress() {
        monitorMessage("Compressing conversation context...");
        var response = chatService.compressContext(AiMonitor.nullSafty(monitor));
        monitorMessage("Context compressed");
        return "Context compressed. Summary:\n" + response.aiMessage().text();
    }
}

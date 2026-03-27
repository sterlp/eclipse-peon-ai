package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.AiCompressorAgent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CompressorAgentTool extends AbstractTool {
    public static final String NAME = "CompressorAgentTool";

    @Tool(name = CompressorAgentTool.NAME, value = "Compress conversation history to free context.")
    public String compressorAgent(
            @P("Optional instructions to preserve in the summary.") String preserve) {
        var summary = new AiCompressorAgent(chatModel).call(memory, monitor);
        return "Context compressed. Summary:\n" 
                + summary.aiMessage().text()
                + "\nPreserved:\n" + preserve;
    }
    
    @Override
    public boolean clearMemory() {
        return true;
    }
}

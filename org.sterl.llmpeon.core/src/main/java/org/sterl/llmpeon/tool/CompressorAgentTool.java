package org.sterl.llmpeon.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CompressorAgentTool extends AbstractTool {
    public static final String NAME = "CompressorAgentTool";

    @Tool(name = CompressorAgentTool.NAME, 
            value = "Compresses conversation history to free context. Use when history is long to keep key info compact.")
    public String compressorAgent(
            @P("Optional info / instructions to include in the summary.") String preserve) {
        var summary = agentService.newCompressorAgent().call(memory, monitor);
        return "Context compressed. Summary:\n" 
                + summary.aiMessage().text()
                + "\nDon't call me again, until the context has grown a again.\n"
                + "\nPreserved:\n" + preserve;
    }
    
    @Override
    public boolean clearMemory() {
        return true;
    }
}

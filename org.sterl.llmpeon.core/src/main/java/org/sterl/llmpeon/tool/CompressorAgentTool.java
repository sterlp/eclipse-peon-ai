package org.sterl.llmpeon.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CompressorAgentTool extends AbstractTool {

    @Tool(name = "CompressorAgentTool", value = """
            Compresses the current conversation history to free up context space.
            Call this when the conversation is getting long and you want to preserve
            key information in a more compact form before continuing the task.
            Returns a summary of the compressed content and an optional string e.g. instruction how to continue back.
            """)
    public String compressorAgent(
            @P("Optional information or instructions to preserve, it will be added to the result.") String preserve) {
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

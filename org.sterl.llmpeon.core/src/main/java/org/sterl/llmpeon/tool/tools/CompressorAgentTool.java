package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CompressorAgentTool extends AbstractTool {
    public static final String NAME = "CompressorAgentTool";

    @Tool(name = CompressorAgentTool.NAME, value = "Compress conversation history to free context.")
    public String compressorAgent(
            @P(description = "instructions to preserve / echo back as next steps or command to yourself", required = false, name = "preserve") String preserve) {
        var summary = new AiCompressorAgent(chatModel).call(memory, monitor);
        return "Context compressed. Summary:\n" 
                + summary.aiMessage().text()
                + "\nPreserved:\n" + StringUtil.stripToEmpty(preserve);
    }
    
    @Override
    public boolean clearMemory() {
        return true;
    }
}

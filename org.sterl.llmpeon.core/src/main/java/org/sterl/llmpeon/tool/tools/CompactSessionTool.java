package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CompactSessionTool extends AbstractTool {
    public static final String NAME = "compactSession";
    
    private final Double temperature;
    
    public CompactSessionTool() {
        this(null);
    }
    /**
     * Not every model supports a different temperature -- leave empty to use model defaults.
     */
    public CompactSessionTool(Double temperature) {
        super();
        this.temperature = temperature;
    }


    @Tool(name = CompactSessionTool.NAME, value = "Compress/compact the current conversation history to free context while preserving key instructions.")
    public String compactSession(
            @P(description = "Short instructions or next steps to keep and echo back after compression.", required = false, name = "preserve") String preserve) {
        var summary = new AiCompressorAgent(chatModel, temperature).call(memory, monitor);
        return summary.aiMessage().text() 
                + (StringUtil.hasValue(preserve) 
                        ? "\nPreserved:\n" + StringUtil.stripToEmpty(preserve)
                        : "");
    }
    
    @Override
    public boolean clearMemory() {
        return true;
    }
}

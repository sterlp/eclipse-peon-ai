package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.UserMessage;

public class CompactSessionTool extends AbstractTool {
    public static final String NAME = "compactSession";

    @Tool(name = CompactSessionTool.NAME, value = "Compress/compact the current conversation history to free context while preserving key instructions.")
    public String compactSession(
            @P(description = "Short instructions or next steps to keep and echo back after compression.", required = false, name = "preserve") String preserve) {
        var cfg = this.request.getModel().getConfig();
        var model = this.request.getChatModel();
        var temp = cfg.getDevTemperature() < 1 ? Math.min(cfg.getDevTemperature() / 2, 0.3) : 1;
        var summary = new AiCompressorAgent(model, temp)
                .call(this.request.getMemory().messages(), monitor);

        // only if we have a result -- also ensure the first message is a user message, some LLMs need this ...
        if (summary.aiMessage().text() != null && summary.aiMessage().text().length() > 5) {
            request.getMemory().clear();
            request.getMemory().add(UserMessage.from("Session compacted. Resume the task using the preserved context."));
        }
        return summary.aiMessage().text() 
                + (StringUtil.hasValue(preserve) 
                        ? "\nPreserved:\n" + StringUtil.stripToEmpty(preserve)
                        : "");
    }
}

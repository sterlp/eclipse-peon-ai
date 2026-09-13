package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CompactSessionTool extends AbstractTool {
    public static final String NAME = "compactSession";
    public static final String TOOL_MESSAGE_PREFIX = "Da Scribe done ";

    @Tool(name = CompactSessionTool.NAME,
            value = """
            Compress/compact conversation history to free context, keeping key instructions.
            If files are also needed, batch this tool first with read tool calls afrerward.
            Loads them directly after the compact instead of preserving through the compact.
            """)
    public String compactSession(
            @P(description = "Short instructions or next steps to keep and echo back after compression.", required = false, name = "preserve") String preserve) {
        // Delegate to the owning agent — it owns the full compress+clear+restore lifecycle.
        // A missing agent is a mis-wiring (AbstractAgent.doCall always sets it) — surface it.
        AiAgent agent = request.getAgent();
        if (agent == null) {
            throw new IllegalStateException("compactSession requires an owning agent");
        }

        long startNanos = System.nanoTime();
        var compactDone = agent.compact(monitor);
        
        if (compactDone) {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            
            var result = StringUtil.hasValue(preserve)
                    ? "Preserved:" + System.lineSeparator() + StringUtil.stripToEmpty(preserve)
                    : "(nothing preserved)";
            
            onTool(TOOL_MESSAGE_PREFIX + " for " + agent.getName() 
                + ". (" + StringUtil.humanElapsed(elapsedMillis) + ")");
            return result;
        } else {
            onTool("Compact called but skipped because of small context for " + agent.getName());
            return "Not needed only " + agent.getMemory().size() + " message in context";
        }
    }
}

package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CompactSessionTool extends AbstractTool {
    public static final String NAME = "compactSession";
    public static final String EMPTY_PRESERVE = "empty preserve";
    public static final String PRESERVE = "Compacted. Preserve text is in call arguments.";

    @Tool(name = CompactSessionTool.NAME,
            value = """
            Compress/compact conversation history to free context, keeping key instructions.
            """)
    public String compactSession(
            @P(description = "Short instructions or next steps to keep and echo back after compression.", required = false, name = "preserve") 
            String preserve) {
        // Delegate to the owning agent — it owns the full compress+clear+restore lifecycle.
        // A missing agent is a mis-wiring (AbstractAgent.doCall always sets it) — surface it.
        AiAgent agent = request.getAgent();
        if (agent == null) {
            throw new IllegalStateException("compactSession requires an owning agent");
        }

        var result = agent.compact(monitor);
        return switch (result.status()) {
            case COMPACTED -> {
                // R-CC-18 (ADR-0070): the consequences of the compact hang on the runner — this
                // tool. The next addResult's provider inputTokenCount describes the PRE-compact
                // context, so the counter must be derived from the (small) post-compact memory
                request.getMemory().markProviderUsageStale();
                request.getMemory().reevaluateTokens();
                request.staticMessages(agent.buildStaticMessages(monitor));
                
                // the tool result contains only OK -- as the preserve is already in the chat history as
                // part of the argument from the LLM, don't echo, this would duplicate preserve
                yield StringUtil.hasValue(preserve) ? PRESERVE : EMPTY_PRESERVE;
            }
            case SKIPPED_SMALL -> {
                // R-CC-10: the diagnosis rides on the onTool LOG line only; the result string stays clean.
                var toolResult = "🗜 Not needed only " + agent.getMemory().size() + " message in context";
                onTool("🗜 Compact called but skipped because of small context for " + agent.getName()
                        + agent.getMemory().tokenDiagnosis() + " " + StringUtil.charsSuffix(toolResult));
                yield toolResult;
            }
            case FAILED_EMPTY -> {
                var toolResult = "🗜 Compact failed: compressor returned no summary for " + agent.getName();
                onTool(toolResult + " " + StringUtil.charsSuffix(toolResult));
                yield toolResult;
            }
        };
    }
}

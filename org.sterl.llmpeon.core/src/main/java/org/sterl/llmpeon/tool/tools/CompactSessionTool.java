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
        var result = agent.compact(monitor);
        return switch (result.status()) {
            case COMPACTED -> {
                long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
                onTool(TOOL_MESSAGE_PREFIX + " for " + agent.getName()
                    + ". (" + StringUtil.humanElapsed(elapsedMillis) + ")");
                request.markCompacted();
                // R-CIB-6: the tool result carries the same numbers as the log (resultLine), so the
                // LLM knows in the next turn what the compact did; the onTool line stays untouched.
                yield result.resultLine() + System.lineSeparator()
                        + (StringUtil.hasValue(preserve)
                                ? "Preserved:" + System.lineSeparator() + StringUtil.stripToEmpty(preserve)
                                : "(nothing preserved)");
            }
            case SKIPPED_SMALL -> {
                onTool("Compact called but skipped because of small context for " + agent.getName());
                yield "Not needed only " + agent.getMemory().size() + " message in context";
            }
            case FAILED_EMPTY -> {
                onTool("Compact failed: compressor returned no summary for " + agent.getName());
                yield "Compact failed: compressor returned no summary for " + agent.getName();
            }
        };
    }
}

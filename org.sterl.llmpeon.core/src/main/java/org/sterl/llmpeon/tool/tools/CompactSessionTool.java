package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CompactSessionTool extends AbstractTool {
    public static final String NAME = "compactSession";

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
        agent.compact(monitor);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        onTool("Da Scribe done. (" + StringUtil.humanElapsed(elapsedMillis) + ")");

        // The summary lives exclusively as an AiMessage in the agent's memory (added by compact()) —
        // the tool result carries only `preserve` (or a marker), never the summary (SOLL 2026-09-10).
        // The no-preserve marker must NOT collide with the resume UserMessage "Session compacted.
        // Resume the task using the preserved context." (AbstractAgent.compact) — otherwise the
        // compact-result text appears twice in the memory (SOLL 2026-09-11).
        return StringUtil.hasValue(preserve)
                ? "Preserved:\n" + StringUtil.stripToEmpty(preserve)
                : "(nothing preserved)";
    }
}

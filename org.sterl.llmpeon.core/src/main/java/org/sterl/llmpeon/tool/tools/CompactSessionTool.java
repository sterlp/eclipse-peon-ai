package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.UserMessage;

public class CompactSessionTool extends AbstractTool {
    public static final String NAME = "compactSession";

    @Tool(name = CompactSessionTool.NAME,
            value = """
            Compress/compact conversation history to free context, keeping key instructions.
            If files are also needed, batch this tool first with read tool calls in the same
            response — load them fresh instead of preserving through the compact.
            """)
    public String compactSession(
            @P(description = "Short instructions or next steps to keep and echo back after compression.", required = false, name = "preserve") String preserve) {
        // Delegate to owning agent when available — it owns the full compress+clear+restore lifecycle
        AiAgent agent = request.getAgent();
        if (agent != null) {
            long startNanos = System.nanoTime();
            var summary = agent.compressContext(monitor);
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            onTool("Da Scribe done. (" + StringUtil.humanElapsed(elapsedMillis) + ")");

            var aiMsg = summary.aiMessage();
            var summaryText = (aiMsg != null && aiMsg.text() != null) ? aiMsg.text() : "";
            return summaryText + (StringUtil.hasValue(preserve)
                            ? "\nPreserved:\n" + StringUtil.stripToEmpty(preserve)
                            : "");
        }

        // Legacy fallback: inline compress + clear + resume-message (standing orders survive via clearMemory())
        return compactSessionFallback(preserve);
    }

    /**
     * @deprecated Fallback path when no owning agent is available (tests/legacy).
     * Inline compress + clear + resume-message flow.
     */
    @Deprecated
    private String compactSessionFallback(String preserve) {
        var model = this.request.getChatModel();

        long startNanos = System.nanoTime();
        var summary = new AiCompressorAgent(model)
                .call(this.request.getMemory().getCopy(), monitor);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        onTool("Da Scribe done. (" + StringUtil.humanElapsed(elapsedMillis) + ")");

        // only if we have a valid result -- also ensure the first message is a user message, some LLMs need this ...
        var aiMsg = summary.aiMessage();
        if (aiMsg != null && aiMsg.text() != null && aiMsg.text().length() > 5) {
            request.clearMemory();
            request.getMemory().add(UserMessage.from("Session compacted. Resume the task using the preserved context."));
        }
        var summaryText = (aiMsg != null && aiMsg.text() != null) ? aiMsg.text() : "";
        return summaryText + (StringUtil.hasValue(preserve)
                        ? "\nPreserved:\n" + StringUtil.stripToEmpty(preserve)
                        : "");
    }
}

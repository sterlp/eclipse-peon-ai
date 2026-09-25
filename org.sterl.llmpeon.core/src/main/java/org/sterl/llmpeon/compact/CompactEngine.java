package org.sterl.llmpeon.compact;

import java.util.List;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.model.ToSimpleMessage;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * The compact component (ADR-0056): stages the input (R-CIB-4), calls the COMPACT slot exactly
 * like the legacy path (COMPRESS_SYSTEM prompt, slot routing, no tools), and logs the entry line
 * (R-CIB-3) plus the result line (R-CIB-6). Stateless per call — safe from any thread.
 */
public class CompactEngine {

    private static final SystemMessage COMPRESS_SYSTEM = SystemMessage.systemMessage(PromptLoader.load("compressor.md"));

    private final ConfiguredChatModel chatModel;
    private final CompactStager stager;
    private final CompactLog log;

    public CompactEngine(ConfiguredChatModel chatModel) {
        this(chatModel, new CompactStager(), CompactLog.slf4j());
    }

    public CompactEngine(ConfiguredChatModel chatModel, CompactLog log) {
        this(chatModel, new CompactStager(), log);
    }

    CompactEngine(ConfiguredChatModel chatModel, CompactStager stager, CompactLog log) {
        this.chatModel = chatModel;
        this.stager = stager;
        this.log = log;
    }

    /**
     * One compact attempt: entry debug log (exactly once, initial values, before any truncation),
     * staged input, COMPACT slot call, result line (log level matches the stage). With a
     * non-positive budget the entry log is the only log (R-CIB-1).
     *
     * @throws IllegalStateException when the LLM call returns null — Log OR throw: the throw stays
     *             in the call path, the result line is no exception substitute
     */
    public CompactResult compact(String agentName, List<ChatMessage> messages, int budgetTokens, AiMonitor monitor) {
        monitor = AiMonitor.nullSafety(monitor);
        var compactCfg = chatModel.getConfig().compactAgentConfig();

        // R-CIB-3: exactly one debug log with the initial values, before any truncation.
        log.debug("Compact entry: agent={}, messageCount={}, estimatedInputTokens={}, budget={}, thinkingEnabled={}",
                agentName, messages.size(), ChatMessageUtil.estimateTokens(messages), budgetTokens,
                StringUtil.hasValue(compactCfg.getThink()));

        var outcome = stager.stage(messages, budgetTokens);

        monitor.onTool("Compressing conversation " + messages.size() + " messages "
                + ChatMessageUtil.estimateTokens(messages) + " tokens"
                + (compactCfg.getModel() == null ? "" : " using " + compactCfg.getModel()));

        var request = ChatRequest.builder()
                .messages(COMPRESS_SYSTEM, UserMessage.from(outcome.input()))
                .parameters(compactCfg.newRequestParameters(null));
        monitor.onChatMessage(1, request);

        long startMillis = System.currentTimeMillis();
        var response = chatModel.callBlocking(request.build(), compactCfg, monitor);
        long millis = System.currentTimeMillis() - startMillis;
        if (response == null) {
            throw new IllegalStateException("AI call returned null — streaming failed without a response");
        }
        ToSimpleMessage.INSTANCE.convert(response.aiMessage()).forEach(monitor::onChatResponse);

        if (StringUtil.hasNoValue(response.aiMessage().text())) {
            var result = CompactResult.failedEmpty(
                    stats(messages.size(), outcome, 0, compactCfg.getModel(), millis),
                    "compressor returned no summary for " + agentName);
            if (budgetTokens > 0) log.error("Compact result: {}", result.resultLine());
            return result;
        }

        var result = CompactResult.compacted(stats(messages.size(), outcome, response.aiMessage().text().length(),
                compactCfg.getModel(), millis));
        if (budgetTokens > 0) logResult(result);
        return result;
    }

    /** R-CIB-6: the result line is logged at the stage's level (info stage 1 / warn stage 2 / error final). */
    private void logResult(CompactResult result) {
        switch (result.stats().stage()) {
            case TOOL_RESULTS -> log.warn("Compact result: {}", result.resultLine());
            case PER_MESSAGE -> log.error("Compact result: {}", result.resultLine());
            default -> log.info("Compact result: {}", result.resultLine());
        }
    }

    private static CompactResult.Stats stats(int messageCount, CompactStager.Outcome outcome, int resultChars,
                                             String model, long millis) {
        return new CompactResult.Stats(messageCount, outcome.estimateBefore(), outcome.estimateAfter(),
                outcome.stage(), outcome.droppedChars(), resultChars, model, millis);
    }
}

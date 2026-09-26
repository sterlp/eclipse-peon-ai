package org.sterl.llmpeon.compact;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.ToSimpleMessage;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The compact component (ADR-0056): stages the input (R-CIB-4), calls the COMPACT slot exactly
 * like the legacy path (COMPRESS_SYSTEM prompt, slot routing, no tools), and logs the entry line
 * (R-CIB-3) plus the result line (R-CIB-6). Monitor-free (R-CC-14): the emission belongs to the
 * callers, the compressor call's chat events go to the log only. Stateless per call — safe from
 * any thread.
 */
public class CompactService {

    private static final SystemMessage COMPRESS_SYSTEM = SystemMessage.systemMessage(PromptLoader.load("compressor.md"));

    private final ConfiguredChatModel chatModel;
    private final ContextTrimComponent stager;
    private final CompactLog log;

    /**
     * R-CC-14: the compressor call's chat events (request, response, usage) go to the log only —
     * the UI never gets a streaming preview of the internal call.
     */
    private final AiMonitor logMonitor = new AiMonitor() {
        @Override
        public void onChatMessage(int iteration, ChatRequest.Builder request) {
            log.debug("Compressor request: {} messages", request.build().messages().size());
        }

        @Override
        public void onChatResponse(SimpleMessage message) {
            log.debug("Compressor response: {}", message.message());
        }

        @Override
        public void onTokenUsage(TokenUsage usage) {
            log.debug("Compressor usage: {}", usage);
        }
    };

    public CompactService(ConfiguredChatModel chatModel) {
        this(chatModel, new ContextTrimComponent(), CompactLog.slf4j());
    }

    public CompactService(ConfiguredChatModel chatModel, CompactLog log) {
        this(chatModel, new ContextTrimComponent(), log);
    }

    CompactService(ConfiguredChatModel chatModel, ContextTrimComponent stager, CompactLog log) {
        this.chatModel = chatModel;
        this.stager = stager;
        this.log = log;
    }

    /**
     * One compact attempt: entry debug log (exactly once, initial values, before any truncation),
     * staged input, COMPACT slot call, result line (log level matches the stage). With a
     * non-positive budget the entry log is the only log (R-CIB-1). Returns the
     * {@link CompactResult} carrying the summary (R-CC-14) — the caller emits the start/result
     * lines and re-seeds the memory.
     *
     * @param requestTokens     R-CC-12: the last provider-reported input tokens, captured by the
     *                          caller BEFORE its memory clear — {@code null} when never reported
     * @param requestIsEstimate R-CC-12: true when {@code requestTokens} is not a real provider value
     * @throws IllegalStateException when the LLM call returns null — Log OR throw: the throw stays
     *             in the call path, the result line is no exception substitute
     */
    public CompactResult compact(String agentName, List<ChatMessage> messages, int budgetTokens, String tokenDiagnosis,
                                 @Nullable Integer requestTokens, boolean requestIsEstimate) {
        var compactCfg = chatModel.getConfig().compactAgentConfig();

        var outcome = stager.stage(messages, budgetTokens);

        // R-CIB-3: exactly one debug log with the initial values, before any truncation — the
        // diagnostic block (per-message caps, output summary) rides on the same call, so the
        // "exactly one debug log" contract holds.
        // R-CC-10: the token diagnosis (memory/model/estimate) is appended after thinkingEnabled,
        // before the diagnostic block — the "exactly one debug log" contract still holds.
        log.debug("Compact entry: agent={}, messageCount={}, estimatedInputTokens={}, budget={}, thinkingEnabled={}{}\n{}",
                agentName, messages.size(), ChatMessageUtil.estimateTokens(messages), budgetTokens,
                StringUtil.hasValue(compactCfg.getThink()), tokenDiagnosis, diagnosticBlock(outcome));

        var request = ChatRequest.builder()
                .messages(COMPRESS_SYSTEM, UserMessage.from(outcome.input()))
                .parameters(compactCfg.newRequestParameters(null));
        logMonitor.onChatMessage(1, request);

        long startMillis = System.currentTimeMillis();
        var response = chatModel.callBlocking(request.build(), compactCfg, logMonitor);
        long millis = System.currentTimeMillis() - startMillis;
        if (response == null) {
            throw new IllegalStateException("AI call returned null — streaming failed without a response");
        }
        ToSimpleMessage.INSTANCE.convert(response.aiMessage()).forEach(logMonitor::onChatResponse);

        if (StringUtil.hasNoValue(response.aiMessage().text())) {
            var result = CompactResult.failedEmpty(
                    stats(messages.size(), outcome, 0, compactCfg.getModel(), millis, requestTokens, requestIsEstimate),
                    "compressor returned no summary for " + agentName);
            if (budgetTokens > 0) log.error("Compact result: {}", result.resultLine());
            return result;
        }

        var summary = response.aiMessage().text();
        var result = CompactResult.compacted(
                stats(messages.size(), outcome, summary.length(), compactCfg.getModel(), millis, requestTokens, requestIsEstimate),
                summary);
        if (budgetTokens > 0) logResult(result);
        return result;
    }

    /**
     * Multi-line diagnostic for the entry log (2026-09-25, Paul): one line per rendered message
     * (index, type, tool name, raw chars, chars after caps) only when something was dropped or
     * deduplicated, plus the output summary line — always. Pure formatting of the Stager's
     * numbers; no instrumentation of the call paths.
     */
    private static String diagnosticBlock(ContextTrimComponent.Outcome outcome) {
        var nl = System.lineSeparator();
        var sb = new StringBuilder();
        if (outcome.droppedChars() > 0 || outcome.duplicatesCollapsed() > 0) {
            for (var stat : outcome.messages()) {
                sb.append("  ").append(stat.index()).append(". ").append(stat.type());
                if (!stat.toolName().isEmpty()) sb.append("(").append(stat.toolName()).append(")");
                if (stat.charsBefore() == stat.charsAfter()) {
                    sb.append(" ").append(stat.charsAfter()).append(" chars");
                } else {
                    sb.append(" ").append(stat.charsBefore()).append(" -> ").append(stat.charsAfter())
                            .append(" chars (dropped ").append(stat.charsBefore() - stat.charsAfter()).append(")");
                }
                sb.append(nl);
            }
        }
        long totalBefore = outcome.messages().stream().mapToLong(ContextTrimComponent.MessageStat::charsBefore).sum();
        // Clamped: state-only user messages drop chars that have no per-message line (not in the input).
        var dropRate = String.format(java.util.Locale.ROOT, "%.1f%%",
                totalBefore > 0 ? Math.min(100.0, outcome.droppedChars() * 100.0 / totalBefore) : 0.0);
        sb.append("  output: ").append(outcome.messages().size()).append(" rendered, ").append(totalBefore)
                .append(" chars / ~").append(outcome.estimateAfter()).append(" tokens, stage=")
                .append(outcome.stage()).append(", droppedChars=").append(outcome.droppedChars())
                .append(", dropRate=").append(dropRate);
        return sb.toString();
    }

    /** R-CIB-6: the result line is logged at the stage's level (info stage 1 / warn stage 2 / error final). */
    private void logResult(CompactResult result) {
        switch (result.stats().stage()) {
            case TOOL_RESULTS -> log.warn("Compact result: {}", result.resultLine());
            case PER_MESSAGE -> log.error("Compact result: {}", result.resultLine());
            default -> log.info("Compact result: {}", result.resultLine());
        }
    }

    private static CompactResult.Stats stats(int messageCount, ContextTrimComponent.Outcome outcome, int resultChars,
                                             String model, long millis, @Nullable Integer requestTokens,
                                             boolean requestIsEstimate) {
        return new CompactResult.Stats(messageCount, outcome.estimateBefore(), outcome.estimateAfter(),
                outcome.stage(), outcome.droppedChars(), resultChars, model, millis, requestTokens, requestIsEstimate);
    }
}

package org.sterl.llmpeon.shared;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Stamps every line that leaves one producer with that producer's core id, so a viewer can decide
 * per line what to show (docs/adr/0078-origin-in-core-owner-monitor.md). The core never filters.
 * <p>
 * <b>fill-if-null, the innermost decorator wins:</b> a nested member turn wraps its caller's
 * monitor, so several {@code OwnerMonitor}s sit in one chain. A line already carrying an origin came
 * from a producer closer to the model and keeps it — overwriting it would hand a member's thinking
 * to the agent that delegated it, which is exactly what the web must hide.
 * <p>
 * <b>{@code origin == null} stamps nothing:</b> no owner is ever invented. A literal such as
 * {@code "(none)"} would become a plugin heading and would drop the line from the web filter.
 * <p>
 * <b>{@link #onTool(String)} and {@link #onProblem(String)} build their line here</b> instead of
 * calling the delegate: {@code AiMonitor}'s defaults route straight to the receiver's own
 * {@code onChatResponse}, so delegating would emit a perfectly formed but <i>unstamped</i> line.
 * <p>
 * <b>{@link #onFileUpdate(AiFileUpdate)} passes through untouched</b> — it is a content event, not a
 * chat line, and the plugin turns it into the unified diff of the chat view. Rebuilding it here
 * would silently replace every diff block with a one-line notice.
 */
public final class OwnerMonitor implements AiMonitor {

    private final @Nullable String origin;
    private final AiMonitor delegate;

    /** {@code delegate} may be null — this wrapper is null-safe on its own. */
    public OwnerMonitor(@Nullable String origin, @Nullable AiMonitor delegate) {
        this.origin = origin;
        this.delegate = AiMonitor.nullSafety(delegate);
    }

    @Override
    public void onChatResponse(SimpleMessage message) {
        delegate.onChatResponse(origin == null || message.origin() != null ? message : message.withOrigin(origin));
    }

    @Override
    public void onStreamingChunk(OnPartialAiResponse r) {
        delegate.onStreamingChunk(origin == null || r.origin() != null ? r : r.withOrigin(origin));
    }

    @Override
    public void onTool(String message) {
        onChatResponse(new SimpleMessage(Type.TOOL, message));
    }

    @Override
    public void onProblem(String message) {
        onChatResponse(new SimpleMessage(Type.PROBLEM, message));
    }

    @Override
    public void onFileUpdate(AiFileUpdate update) {
        delegate.onFileUpdate(update);
    }

    @Override
    public void onCallStart(String userMessage) {
        delegate.onCallStart(userMessage);
    }

    @Override
    public void onChatMessage(int iteration, ChatRequest.Builder request) {
        delegate.onChatMessage(iteration, request);
    }

    @Override
    public void onCallCompleted(ChatResponse response, Duration duration) {
        delegate.onCallCompleted(response, duration);
    }

    @Override
    public void onTokenUsage(TokenUsage usage) {
        delegate.onTokenUsage(usage);
    }

    @Override
    public void onCompactCompleted(String agentName, CompactResult result) {
        delegate.onCompactCompleted(agentName, result);
    }

    @Override
    public boolean isCanceled() {
        return delegate.isCanceled();
    }
}

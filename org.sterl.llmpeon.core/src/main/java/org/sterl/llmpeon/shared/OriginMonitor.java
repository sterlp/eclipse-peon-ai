package org.sterl.llmpeon.shared;

import java.time.Duration;

import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The one place that says <b>who</b> spoke: the wrapper an agent puts around the monitor it was handed,
 * stamping its own name on every chat line and streaming chunk that passes through.
 * <p>
 * Two rules make this enough for a viewer to filter and label without guessing:
 * <ul>
 * <li><b>Only a missing origin is set.</b> The innermost wrapper (the one closest to the producer)
 * therefore wins — a nested delegation stamps its own member, and the outer wrapper cannot overwrite
 * it or leave the line unnamed.</li>
 * <li><b>Nothing is filtered here.</b> The core states the author; a customer decides visibility. The
 * Eclipse plugin hands the raw monitor to its views and goes on seeing every member line exactly as
 * before.</li>
 * </ul>
 *
 * <p>
 * The three chat-line callbacks the interface derives from {@code onChatResponse}
 * ({@code onTool}, {@code onProblem}, {@code onFileUpdate}) are stamped through this wrapper's own
 * {@code onChatResponse} whenever the listener keeps the interface default — a listener must never
 * have to derive the author from a bare string. They are forwarded <b>raw</b> in exactly one case: the
 * listener renders the callback itself (an override of its own) and is not another wrapper. That
 * listener is the Eclipse plugin's chat view, which overrides {@code onFileUpdate} for its diff view;
 * rerouting its override through {@code onChatResponse} would change plugin behaviour (PR-3).
 */
public final class OriginMonitor implements AiMonitor {

    private final AiMonitor delegate;
    private final String agentName;

    private OriginMonitor(AiMonitor delegate, String agentName) {
        this.delegate = delegate;
        this.agentName = agentName;
    }

    /**
     * The monitor for one turn of one agent. Null-safe on the way in, like
     * {@link AiMonitor#nullSafety(AiMonitor)}.
     *
     * @return {@code delegate} itself when there is nothing to stamp — a blank {@code agentName}, or a
     *         wrapper already carrying the same name (a nested call of the same agent must not stack)
     */
    public static AiMonitor of(AiMonitor delegate, String agentName) {
        AiMonitor target = AiMonitor.nullSafety(delegate);
        if (agentName == null || agentName.isBlank()) return target;
        if (target instanceof OriginMonitor stamped && stamped.agentName.equals(agentName)) return stamped;
        return new OriginMonitor(target, agentName);
    }

    /** The agent whose name this wrapper stamps. */
    public String agentName() {
        return agentName;
    }

    @Override
    public void onChatResponse(SimpleMessage message) {
        delegate.onChatResponse(message == null || message.origin() != null ? message : message.withOrigin(agentName));
    }

    @Override
    public void onStreamingChunk(OnPartialAiResponse chunk) {
        delegate.onStreamingChunk(chunk == null || chunk.origin() != null ? chunk : chunk.withOrigin(agentName));
    }

    /**
     * Derived chat lines carry the author too. Both branches keep the message the interface default
     * builds — the difference is only <b>who</b> turns it into a chat line.
     */
    @Override
    public void onTool(String message) {
        if (forwardsRaw("onTool", String.class)) {
            delegate.onTool(message);
        } else {
            // the default builds the line and hands it to this.onChatResponse — where it gets stamped
            AiMonitor.super.onTool(message);
        }
    }

    @Override
    public void onProblem(String message) {
        if (forwardsRaw("onProblem", String.class)) {
            delegate.onProblem(message);
        } else {
            AiMonitor.super.onProblem(message);
        }
    }

    @Override
    public void onFileUpdate(AiFileUpdate update) {
        if (forwardsRaw("onFileUpdate", AiFileUpdate.class)) {
            delegate.onFileUpdate(update);
        } else {
            AiMonitor.super.onFileUpdate(update);
        }
    }

    /**
     * Raw forwarding loses the stamp, so it happens only where it cannot be avoided: a listener with an
     * override of its own. A nested wrapper is never forwarded to — its name has to win, which
     * {@code AiMonitor.super} guarantees by stamping here first ({@link #onChatResponse} keeps a set
     * origin), so a member's tool line never arrives wearing its delegating agent's name.
     */
    private boolean forwardsRaw(String callback, Class<?>... parameterTypes) {
        return !(delegate instanceof OriginMonitor) && overrides(callback, parameterTypes);
    }

    private boolean overrides(String name, Class<?>... parameterTypes) {
        try {
            return delegate.getClass().getMethod(name, parameterTypes).getDeclaringClass() != AiMonitor.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    // Everything else is passed through 1:1 — an empty interface default would swallow it.

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

    /**
     * Announced by the outermost wrapper only: the completion of a delegated member reaches the inner
     * wrapper first and stops there, so a viewer that shows one conversation still sees exactly one
     * completion per answered input while every {@link #onCallCompleted} keeps passing through.
     */
    @Override
    public void onAgentCallCompleted(String agentName, ChatResponse response, Duration duration) {
        if (!(delegate instanceof OriginMonitor)) delegate.onAgentCallCompleted(agentName, response, duration);
    }

    @Override
    public void onDelegatedAnswer(String agentName, String answer) {
        delegate.onDelegatedAnswer(agentName, answer);
    }

    @Override
    public void onTokenUsage(TokenUsage usage) {
        delegate.onTokenUsage(usage);
    }

    @Override
    public void onCompactCompleted(String agentName, CompactResult result, boolean withinTurn) {
        delegate.onCompactCompleted(agentName, result, withinTurn);
    }

    @Override
    public boolean isCanceled() {
        return delegate.isCanceled();
    }
}

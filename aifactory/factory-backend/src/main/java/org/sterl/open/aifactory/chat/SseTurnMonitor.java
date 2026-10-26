package org.sterl.open.aifactory.chat;

import java.time.Duration;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * The one place that knows the core's callback language: every {@link AiMonitor} callback of a project
 * becomes a {@link ChatEvent} here, and nothing else. One instance per project, never per turn — the
 * queue drain re-uses the agent's monitor, so a per-turn monitor would lose the streaming events of the
 * second input.
 *
 * <p><b>What reaches the browser</b> (R-WP-4, UC-WP-40): the core says <em>who</em> spoke, this class
 * decides who is <em>shown</em> — the conversation's own agent, plus the lines no agent claimed. A team
 * member's thinking, tool lines and half answers are its conversation with itself and are dropped here,
 * while the delegation's own trace lines stay because Jon's tool produced them. The rule is policy and
 * therefore lives in the factory, not in the core: the Eclipse plugin hands the same callback stream to
 * its views and goes on showing every member line.</p>
 *
 * <p><b>Where {@code done} comes from</b>: {@link #onAgentCallCompleted}, never
 * {@link #onCallCompleted}. A member's completion is announced to the wrapper chain and stops in the
 * innermost wrapper, so the callback that arrives here is the outer agent's — one answered input stays
 * one {@code done} even when a member finished first.</p>
 *
 * <p>What is deliberately not forwarded: the finished answer text. It arrives in the history, and a
 * second copy on the wire would be a second truth (R-WP-11). {@code START}/{@code END} carry no value,
 * {@code TOOL} chunks duplicate the {@code tool} line that {@link #onChatResponse} already sends.</p>
 */
class SseTurnMonitor implements AiMonitor {

    private final String projectId;
    private final ChatStreamRegistry streams;
    private final AgentNames names;

    SseTurnMonitor(String projectId, ChatStreamRegistry streams, AgentNames names) {
        this.projectId = projectId;
        this.streams = streams;
        this.names = names;
    }

    @Override
    public void onStreamingChunk(OnPartialAiResponse chunk) {
        if (!isTheConversation(chunk.origin())) {
            return;
        }
        switch (chunk.type()) {
            case THINK -> streams.publish(projectId, new ChatEvent.Partial("think", chunk.value()));
            case ANSWER -> streams.publish(projectId, new ChatEvent.Partial("answer", chunk.value()));
            case START, TOOL, END -> {
                // no value to show — see the class doc
            }
        }
    }

    @Override
    public void onChatResponse(SimpleMessage message) {
        if (!isTheConversation(message.origin())) {
            return;
        }
        if (message.role() == SimpleMessage.Type.TOOL || message.role() == SimpleMessage.Type.PROBLEM) {
            streams.publish(projectId, new ChatEvent.ToolLine(message.message()));
        }
    }

    /**
     * Per answered input, not per {@code call(…)} — {@code AbstractAgent.doCall} fires this once for the
     * turn it is working on, so a queued input gets its own {@code done}.
     */
    @Override
    public void onAgentCallCompleted(String agentName, ChatResponse response, Duration duration) {
        if (response == null) {
            streams.publish(projectId, new ChatEvent.Failed("the turn ended without a response"));
        } else {
            streams.publish(projectId, new ChatEvent.Done(duration.toMillis()));
        }
    }

    /**
     * Jon's lines and the lines no agent produced belong to the conversation. An origin this factory
     * never built is never shown as one of them — neither a member's nor a stranger's.
     */
    private boolean isTheConversation(String origin) {
        return origin == null || names.isJon(origin);
    }
}

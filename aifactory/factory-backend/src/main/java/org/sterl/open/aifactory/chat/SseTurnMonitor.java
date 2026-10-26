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
 * <p>What is deliberately not forwarded: the finished answer text. It arrives in the history, and a
 * second copy on the wire would be a second truth (R-WP-11). {@code START}/{@code END} carry no value,
 * {@code TOOL} chunks duplicate the {@code tool} line that {@link #onChatResponse} already sends.</p>
 */
class SseTurnMonitor implements AiMonitor {

    private final String projectId;
    private final ChatStreamRegistry streams;

    SseTurnMonitor(String projectId, ChatStreamRegistry streams) {
        this.projectId = projectId;
        this.streams = streams;
    }

    @Override
    public void onStreamingChunk(OnPartialAiResponse chunk) {
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
        if (message.role() == SimpleMessage.Type.TOOL || message.role() == SimpleMessage.Type.PROBLEM) {
            streams.publish(projectId, new ChatEvent.ToolLine(message.message()));
        }
    }

    /**
     * Per answered input, not per {@code call(…)} — {@code AbstractAgent.doCall} fires this once for the
     * turn it is working on, so a queued input gets its own {@code done}.
     */
    @Override
    public void onCallCompleted(ChatResponse response, Duration duration) {
        if (response == null) {
            streams.publish(projectId, new ChatEvent.Failed("the turn ended without a response"));
        } else {
            streams.publish(projectId, new ChatEvent.Done(duration.toMillis()));
        }
    }
}

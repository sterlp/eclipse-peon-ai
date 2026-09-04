package org.sterl.llmpeon.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * One {@link StreamingBridge} is created per turn and reused across all tool-loop
 * iterations, so {@code startedAt} must span the whole turn — the UI "working since Xs"
 * must not jump on every tool-loop LLM call. See docs/streaming-display.md.
 */
class StreamingBridgeTest {

    static final Instant T0 = Instant.parse("2026-09-04T10:00:00Z");

    /** Fixed clock the test can advance between the two calls of one turn. */
    static final class MutableClock extends Clock {
        private Instant instant = T0;
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    /** Records the START chunk's startedAt of each call. */
    static final class StartRecorder implements AiMonitor {
        final List<Instant> starts = new ArrayList<>();
        @Override public void onChatResponse(SimpleMessage m) {}
        @Override public void onStreamingChunk(OnPartialAiResponse r) {
            if (r.type() == OnPartialAiResponse.Type.START) starts.add(r.startedAt());
        }
    }

    @Test
    void startedAtSpansWholeTurn() {
        // GIVEN a bridge whose clock is fixed at the turn start T0
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var model = completeModel();
        var monitor = new StartRecorder();
        var request = new ChatRequest.Builder().messages(UserMessage.from("hi")).build();

        // WHEN the first LLM call of the turn runs
        bridge.call(model, request, monitor);
        // THEN its START chunk carries the turn start
        assertThat(monitor.starts.get(0)).isEqualTo(T0);

        // WHEN the turn has been running for an hour and a tool-loop iteration triggers call 2
        clock.advance(Duration.ofHours(1));
        bridge.call(model, request, monitor);

        // THEN startedAt is still the turn start — it must NOT jump to call 2's start
        assertThat(monitor.starts.get(1)).isEqualTo(T0);
    }

    private StreamingChatModel completeModel() {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            inv.getArgument(1, StreamingChatResponseHandler.class)
                .onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }
}

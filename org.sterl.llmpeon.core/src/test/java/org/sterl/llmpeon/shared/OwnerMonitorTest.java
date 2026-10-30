package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The contract of the one stamp mechanism (docs/adr/0078-origin-in-core-owner-monitor.md) — every
 * case here holds without a model, so a regression is cheap to find.
 */
class OwnerMonitorTest {

    private final Capture capture = new Capture();

    /** A line of an outer agent, as a nested member turn sees it. */
    private static final SimpleMessage JONS_LINE = new SimpleMessage(Type.AI, "jon thinking", "Jon");

    @Test
    void stampsOnlyUnstampedLines() {
        var monitor = new OwnerMonitor("Dev", capture);

        // WHEN — an unstamped line and an unstamped chunk pass the wrapper
        monitor.onChatResponse(new SimpleMessage(Type.AI, "member thinking"));
        monitor.onStreamingChunk(new OnPartialAiResponse(OnPartialAiResponse.Type.ANSWER, "token", Instant.now(), 0));
        // AND — a line that already carries an owner
        monitor.onChatResponse(JONS_LINE);

        // THEN — both unstamped events carry the wrapper's id …
        assertThat(capture.lines).extracting(SimpleMessage::origin).containsExactly("Dev", "Jon");
        assertThat(capture.chunks).extracting(OnPartialAiResponse::origin).containsExactly("Dev");
        // AND — the already owned line kept its own owner instead of being renamed
        assertThat(capture.lines).extracting(SimpleMessage::message).containsExactly("member thinking", "jon thinking");
    }

    @Test
    void nestedOwnersKeepTheInnerName() {
        // GIVEN — Jon's wrapper around the view, Dev's wrapper around that: Dev is the turn running
        var dev = new OwnerMonitor("Dev", new OwnerMonitor("Jon", capture));

        // WHEN — the member turn emits
        dev.onTool("compiling");

        // THEN — the producer closest to the line wins, the outer frame never renames it
        assertThat(capture.lines).singleElement()
                .extracting(SimpleMessage::origin).isEqualTo("Dev");

        // AND — control: reversed nesting is Jon's own turn, so the outer name wins there
        var control = new Capture();
        new OwnerMonitor("Jon", new OwnerMonitor("Dev", control)).onTool("delegating");
        assertThat(control.lines).singleElement()
                .extracting(SimpleMessage::origin).isEqualTo("Jon");
    }

    @Test
    void toolAndProblemLinesAreBuiltInTheWrapper() {
        var monitor = new OwnerMonitor("Dev", capture);

        // WHEN
        monitor.onTool("a tool ran");
        monitor.onProblem("something failed");

        // THEN — both arrive as stamped chat lines …
        assertThat(capture.lines).hasSize(2);
        assertThat(capture.lines).extracting(SimpleMessage::role).containsExactly(Type.TOOL, Type.PROBLEM);
        assertThat(capture.lines).allSatisfy(m -> assertThat(m.origin()).isEqualTo("Dev"));
        // AND — the delegate's own channels stayed silent: calling them would let AiMonitor's default
        //    emit a well-formed but UNSTAMPED line (the silent null fallback this avoids)
        assertThat(capture.toolChannel).isEmpty();
        assertThat(capture.problemChannel).isEmpty();
    }

    @Test
    void fileUpdateReachesTheDelegateRaw() {
        var monitor = new OwnerMonitor("Dev", capture);
        var update = new AiMonitor.AiFileUpdate("/tmp/a.java", "old", "new");

        // WHEN
        monitor.onFileUpdate(update);

        // THEN — exactly one raw content event, no chat line: the plugin renders it as a unified diff
        assertThat(capture.fileUpdates).containsExactly(update);
        assertThat(capture.lines).isEmpty();
    }

    @Test
    void cancelAndOtherDefaultsAreTransparent() {
        var monitor = new OwnerMonitor("Dev", capture);

        // WHEN — every remaining channel is used once
        monitor.onCallStart("the question");
        monitor.onChatMessage(1, ChatRequest.builder());
        monitor.onCallCompleted(ChatResponse.builder().aiMessage(AiMessage.from("done")).build(), Duration.ofMillis(5));
        monitor.onTokenUsage(new TokenUsage(10, 20));
        monitor.onCompactCompleted("Dev", CompactResult.skippedSmall());

        // THEN — cancellation is read through, nothing is swallowed by the wrapper
        assertThat(monitor.isCanceled()).as("cancel signal").isTrue();
        assertThat(capture.channels)
                .containsExactly("callStart", "chatMessage", "callCompleted", "tokenUsage", "compactCompleted");
    }

    @Test
    void nullOriginStampsNothing() {
        var monitor = new OwnerMonitor(null, capture);

        // WHEN
        monitor.onChatResponse(new SimpleMessage(Type.AI, "no owner"));
        monitor.onStreamingChunk(new OnPartialAiResponse(OnPartialAiResponse.Type.ANSWER, "token", Instant.now(), 0));
        monitor.onTool("a tool ran");

        // THEN — no owner is invented for a producer that has none
        assertThat(capture.lines).extracting(SimpleMessage::origin).containsOnlyNulls();
        assertThat(capture.chunks).extracting(OnPartialAiResponse::origin).containsOnlyNulls();
    }

    @Test
    void aLinesOwnerIsNeverInvented() {
        var unstamped = new SimpleMessage(Type.AI, "shared line");

        new OwnerMonitor("Dev", capture).onChatResponse(unstamped);

        // THEN — the stamped line is a copy, the producer's own instance keeps its value
        assertThat(capture.lines).singleElement().extracting(SimpleMessage::origin).isEqualTo("Dev");
        assertThat(unstamped.origin()).isNull();
    }

    /** Records every channel apart, so a channel lost in the wrapper cannot hide as another one. */
    private static class Capture implements AiMonitor {
        final List<SimpleMessage> lines = new ArrayList<>();
        final List<OnPartialAiResponse> chunks = new ArrayList<>();
        final List<AiMonitor.AiFileUpdate> fileUpdates = new ArrayList<>();
        final List<String> toolChannel = new ArrayList<>();
        final List<String> problemChannel = new ArrayList<>();
        final List<String> channels = new ArrayList<>();

        @Override public void onChatResponse(SimpleMessage message) { lines.add(message); }
        @Override public void onStreamingChunk(OnPartialAiResponse r) { chunks.add(r); }
        @Override public void onFileUpdate(AiFileUpdate update) { fileUpdates.add(update); }
        @Override public void onTool(String message) { toolChannel.add(message); }
        @Override public void onProblem(String message) { problemChannel.add(message); }
        @Override public void onCallStart(String userMessage) { channels.add("callStart"); }
        @Override public void onChatMessage(int iteration, ChatRequest.Builder request) { channels.add("chatMessage"); }
        @Override public void onCallCompleted(ChatResponse response, Duration duration) { channels.add("callCompleted"); }
        @Override public void onTokenUsage(TokenUsage usage) { channels.add("tokenUsage"); }
        @Override public void onCompactCompleted(String agentName, CompactResult result) { channels.add("compactCompleted"); }
        @Override public boolean isCanceled() { return true; }
    }
}

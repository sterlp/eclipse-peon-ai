package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class OriginMonitorTest {

    private final ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from("antwort")).build();
    private final Duration duration = Duration.ofMillis(1200);

    /** Records every callback of the interface, so a silently swallowed one cannot hide. */
    private static class Recorder implements AiMonitor {
        final List<SimpleMessage> lines = new ArrayList<>();
        final List<OnPartialAiResponse> chunks = new ArrayList<>();
        final List<String> events = new ArrayList<>();

        @Override
        public void onChatResponse(SimpleMessage message) {
            lines.add(message);
        }

        @Override
        public void onStreamingChunk(OnPartialAiResponse chunk) {
            chunks.add(chunk);
        }

        @Override
        public void onCallStart(String userMessage) {
            events.add("start " + userMessage);
        }

        @Override
        public void onChatMessage(int iteration, ChatRequest.Builder request) {
            events.add("request " + iteration);
        }

        @Override
        public void onCallCompleted(ChatResponse response, Duration duration) {
            events.add("completed " + duration.toMillis());
        }

        @Override
        public void onAgentCallCompleted(String agentName, ChatResponse response, Duration duration) {
            events.add("announced " + agentName);
        }

        @Override
        public void onDelegatedAnswer(String agentName, String answer) {
            events.add("delegated " + agentName + " " + answer);
        }

        @Override
        public void onTokenUsage(TokenUsage usage) {
            events.add("tokens " + usage.totalTokenCount());
        }

        @Override
        public void onCompactCompleted(String agentName, CompactResult result, boolean withinTurn) {
            events.add("compact " + agentName + " " + withinTurn);
        }

        @Override
        public boolean isCanceled() {
            events.add("canceled");
            return true;
        }
    }

    /** A listener that renders tool and file lines itself instead of through {@code onChatResponse}. */
    private static class OwnToolLines implements AiMonitor {
        final List<SimpleMessage> lines = new ArrayList<>();
        final List<String> tools = new ArrayList<>();
        final List<AiFileUpdate> updates = new ArrayList<>();

        @Override
        public void onChatResponse(SimpleMessage message) {
            lines.add(message);
        }

        @Override
        public void onTool(String message) {
            tools.add(message);
        }

        @Override
        public void onFileUpdate(AiFileUpdate update) {
            updates.add(update);
        }
    }

    @Test
    void stampsOnlyWhenOriginIsMissing() {
        var target = new Recorder();
        AiMonitor monitor = OriginMonitor.of(target, "Peon-Dev");

        monitor.onChatResponse(new SimpleMessage(Type.AI, "von aussen"));
        monitor.onChatResponse(new SimpleMessage(Type.AI, "von innen").withOrigin("Peon-PO"));
        monitor.onChatResponse(null);

        assertThat(target.lines).hasSize(3);
        assertThat(target.lines.get(0)).satisfies(line -> {
            assertThat(line.role()).isEqualTo(Type.AI);
            assertThat(line.message()).isEqualTo("von aussen");
            assertThat(line.origin()).isEqualTo("Peon-Dev");
        });
        assertThat(target.lines.get(1).origin()).as("the innermost stamp wins").isEqualTo("Peon-PO");
        assertThat(target.lines.get(2)).as("nothing is invented and nothing is dropped").isNull();

        Instant startedAt = Instant.ofEpochMilli(1_700_000_000_000L);
        monitor.onStreamingChunk(new OnPartialAiResponse(OnPartialAiResponse.Type.THINK, "denkt", startedAt, 17));
        monitor.onStreamingChunk(new OnPartialAiResponse(OnPartialAiResponse.Type.ANSWER, "antwortet", startedAt, 17)
                .withOrigin("Peon-PO"));
        monitor.onStreamingChunk(null);

        assertThat(target.chunks).hasSize(3);
        assertThat(target.chunks.get(0)).satisfies(chunk -> {
            assertThat(chunk.type()).isEqualTo(OnPartialAiResponse.Type.THINK);
            assertThat(chunk.value()).isEqualTo("denkt");
            assertThat(chunk.startedAt()).isEqualTo(startedAt);
            assertThat(chunk.tokenPhaseStart()).isEqualTo(17);
            assertThat(chunk.origin()).isEqualTo("Peon-Dev");
        });
        assertThat(target.chunks.get(1).origin()).as("the innermost stamp wins").isEqualTo("Peon-PO");
        assertThat(target.chunks.get(2)).isNull();

        monitor.onCallStart("frage");
        monitor.onChatMessage(2, ChatRequest.builder());
        monitor.onTokenUsage(new TokenUsage(10, 6, 16));
        monitor.onCompactCompleted("Peon-Dev", CompactResult.skippedSmall(), true);
        monitor.onDelegatedAnswer("Peon-Dev", "die Antwort");
        monitor.onCallCompleted(response, duration);
        monitor.onAgentCallCompleted("Peon-Dev", response, duration);

        assertThat(monitor.isCanceled()).as("cancellation is the delegates decision").isTrue();
        assertThat(target.events).containsExactly( //
                "start frage", //
                "request 2", //
                "tokens 16", //
                "compact Peon-Dev true", //
                "delegated Peon-Dev die Antwort", //
                "completed 1200", //
                "announced Peon-Dev", //
                "canceled");
    }

    /**
     * The completion a viewer of one conversation acts on comes from the outermost wrapper only — a
     * nested member's end must not reach it, while {@code onCallCompleted} keeps its full plugin
     * behaviour.
     */
    @Test
    void outermostCompletionAnnouncesOnce() {
        var target = new Recorder();
        AiMonitor jon = OriginMonitor.of(target, "Peon-PO");
        AiMonitor member = OriginMonitor.of(jon, "Peon-Dev");

        member.onCallCompleted(response, duration);
        member.onAgentCallCompleted("Peon-Dev", response, duration);
        jon.onCallCompleted(response, duration);
        jon.onAgentCallCompleted("Peon-PO", response, duration);

        assertThat(target.events).as("nested announcement swallowed, plugin completion passed twice")
                .containsExactly("completed 1200", "completed 1200", "announced Peon-PO");
    }

    /**
     * A tool line, a problem line and a file line are chat lines too: whoever keeps the interface
     * default gets them stamped. A listener that renders them itself (the plugin's chat view does, for
     * its diff) is called on its own override and gains no second line.
     */
    @Test
    void derivedChatLinesAreStampedAndAnOverrideIsNeverBypassed() {
        var plain = new Recorder();
        AiMonitor monitor = OriginMonitor.of(plain, "Peon-Dev");

        monitor.onTool("shell: pwd");
        monitor.onProblem("etwas schief");
        monitor.onFileUpdate(new AiFileUpdate("notes/a.md", "eins", "zwei"));

        assertThat(plain.lines).as("the lines the interface default builds carry the author").hasSize(3);
        assertThat(plain.lines).allSatisfy(line -> assertThat(line.origin()).isEqualTo("Peon-Dev"));
        assertThat(plain.lines).extracting(SimpleMessage::message)
                .containsExactly("shell: pwd", "etwas schief", "Updated: notes/a.md");
        assertThat(plain.lines).extracting(SimpleMessage::role).containsExactly(Type.TOOL, Type.PROBLEM, Type.TOOL);

        var pluginView = new OwnToolLines();
        AiMonitor pluginMonitor = OriginMonitor.of(pluginView, "Peon-Dev");

        pluginMonitor.onTool("shell: pwd");
        pluginMonitor.onFileUpdate(new AiFileUpdate("notes/a.md", "eins", "zwei"));
        pluginMonitor.onProblem("etwas schief");

        assertThat(pluginView.tools).as("the override is called, not rerouted").containsExactly("shell: pwd");
        assertThat(pluginView.updates).extracting(AiFileUpdate::file).containsExactly("notes/a.md");
        assertThat(pluginView.lines).as("no second line for what the listener renders itself").singleElement()
                .satisfies(line -> {
                    assertThat(line.role()).isEqualTo(Type.PROBLEM);
                    assertThat(line.message()).isEqualTo("etwas schief");
                    assertThat(line.origin()).isEqualTo("Peon-Dev");
                });

        var nestedTarget = new Recorder();
        AiMonitor outer = OriginMonitor.of(OriginMonitor.of(nestedTarget, "Peon-PO"), "Peon-Dev");

        outer.onTool("askDev start: nenne eine Frucht");

        assertThat(nestedTarget.lines).singleElement().satisfies(line -> assertThat(line.origin())
                .as("a member line never wears its delegating agents name").isEqualTo("Peon-Dev"));
    }

    @Test
    void of_neitherInventsANameNorStacksAWrapper() {
        var target = new Recorder();

        assertThat(OriginMonitor.of(null, "Peon-Dev")).as("null-safe on the way in").isNotNull();
        assertThat(OriginMonitor.of(target, null)).isSameAs(target);
        assertThat(OriginMonitor.of(target, "  ")).isSameAs(target);

        AiMonitor stamped = OriginMonitor.of(target, "Peon-Dev");
        assertThat(OriginMonitor.of(stamped, "Peon-Dev")).as("a nested call of the same agent must not stack")
                .isSameAs(stamped);
        assertThat(((OriginMonitor) OriginMonitor.of(stamped, "Peon-PO")).agentName()).isEqualTo("Peon-PO");
    }
}

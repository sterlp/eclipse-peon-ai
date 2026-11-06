package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.CallStats;
import org.sterl.llmpeon.shared.OwnerMonitor;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.model.SimpleMessage;

/**
 * The translation of one core callback into one live line — over the real {@link ChatStreamRegistry} and
 * a recording emitter, no Spring context: nothing between {@code onFileUpdate} and the wire is a bean.
 *
 * <p>A delegation reaches this translation by the other door: not an {@code AiMonitor} callback but the
 * delegation tool's own listener pair, which hands over a member and no word of his order (R-WP-40).</p>
 *
 * <p>Delivery runs on the subscriber's own thread, so a count of received events only means "all of them"
 * once the turn is known to be over. Every test therefore closes its events with a {@code done} marker and
 * waits for <em>that</em> — a connection keeps its order, so when the marker has landed, whatever the turn
 * published before it has landed too. "Exactly one diff, no tool line beside it" is then proven and not
 * merely observed early.</p>
 */
@Timeout(30)
class SseTurnMonitorTest {

    private static final String PROJECT = "p1";
    private static final String DIFF_FILE = "src/App.java";

    private final ChatStreamRegistry registry = new ChatStreamRegistry();
    private final RecordingEmitter wire = new RecordingEmitter();
    private final SseTurnMonitor monitor = new SseTurnMonitor(PROJECT, registry, List::of, origin -> {
    });

    @BeforeEach
    void watchingBrowser() {
        registry.subscribe(PROJECT, wire);
    }

    @AfterEach
    void closeTheWire() {
        registry.closeAll();
    }

    // UC-WP-68
    @Test
    void aFileUpdateBecomesADiffOnTheWire() {
        monitor.onFileUpdate(new AiFileUpdate(DIFF_FILE, "a\n", "a\nb\n"));
        endTheTurn();

        assertThat(wire.names()).as("one diff and nothing else beside it").containsExactly("diff", "done");
        Map<String, Object> payload = wire.payload(0);
        assertThat(payload.keySet()).as("no third field reaches the browser").containsExactly("file", "diff");
        assertThat(payload).containsEntry("file", DIFF_FILE);
        assertThat((String) payload.get("diff"))
                .startsWith("--- a/" + DIFF_FILE + "\n+++ b/" + DIFF_FILE + "\n@@ -1,1 +1,2 @@\n")
                .contains("\n+b\n");
    }

    // UC-WP-68
    @Test
    void everyProducerOfTheTurnShowsItsDiff() {
        // the core stamps its lines with the producing agent and passes a file update through unstamped —
        // a member's write therefore arrives at the very same translation as Jon's
        new OwnerMonitor("Dev", monitor).onFileUpdate(new AiFileUpdate(DIFF_FILE, "a\n", "a\nb\n"));
        endTheTurn();

        assertThat(wire.names()).containsExactly("diff", "done");
        assertThat(wire.payload(0).keySet()).as("a diff names no author: R-WP-36 shows Jon and the team")
                .containsExactly("file", "diff");
        assertThat((String) wire.payload(0).get("diff")).startsWith("--- a/" + DIFF_FILE);
    }

    // UC-WP-68
    @Test
    void aCreatedFileIsAPurePlusDiffOnTheWire() {
        // the core answers a write to a missing file with oldContent = "" — the wire must not claim a
        // removal the browser has no old version of, and no context of a file that was not there
        monitor.onFileUpdate(new AiFileUpdate("new/File.java", "", "package x;\n"));
        endTheTurn();

        assertThat(wire.names()).containsExactly("diff", "done");
        Map<String, Object> payload = wire.payload(0);
        assertThat(payload.keySet()).as("no third field reaches the browser").containsExactly("file", "diff");
        assertThat(payload).containsEntry("file", "new/File.java");

        String diff = (String) payload.get("diff");
        assertThat(diff).startsWith("--- a/new/File.java\n+++ b/new/File.java\n");
        assertThat(diff.lines().skip(2).filter(line -> !line.startsWith("@@")).toList())
                .as("a create carries additions only — no removal, no context line")
                .containsExactly("+package x;");
    }

    // UC-WP-68
    @Test
    void anUnchangedFileShowsNothing() {
        String unchanged = "a\nb\n";
        monitor.onFileUpdate(new AiFileUpdate(DIFF_FILE, unchanged, unchanged));
        endTheTurn();

        // the marker is what proves the empty wire is an empty change and not a broken connection
        assertThat(wire.names()).containsExactly("done");
    }

    // UC-WP-68
    @Test
    void aHugeChangeIsAPartialDiffOnTheWire() {
        // 1200 x 1200 lines sharing nothing — over SimpleDiff's cap, so it caps the block and names
        // the whole change instead of answering with a summary
        monitor.onFileUpdate(new AiFileUpdate("src/Big.java", lines("old"), lines("new")));
        endTheTurn();

        assertThat(wire.names()).as("a huge change is still a coloured block, not a line").containsExactly("diff", "done");
        assertThat(wire.payload(0).keySet()).containsExactly("file", "diff");
        assertThat((String) wire.payload(0).get("diff"))
                .startsWith("--- a/src/Big.java")
                .endsWith(" … diff truncated — showing 500 of 2400 changed lines\n");
    }

    // UC-WP-68
    @Test
    void binaryContentIsAToolLine() {
        char nul = (char) 0;
        monitor.onFileUpdate(new AiFileUpdate("img/logo.png", "a\n" + nul + "png\n", "a\n" + nul + "png v2\n"));
        endTheTurn();

        assertThat(wire.names()).as("binary content never becomes a coloured block").containsExactly("tool", "done");
        assertThat(wire.payload(0).keySet()).containsExactly("text");
        assertThat((String) wire.payload(0).get("text"))
                .startsWith("file updated, diff skipped for img/logo.png")
                .contains("binary content");
    }

    // UC-WP-72
    @Test
    void toolLineCarriesTheStampOfItsAgent() {
        // the core stamps a tool line on its way to the monitor (OwnerMonitor.onChatResponse, core
        // OwnerMonitor.java:50) and passes an unstamped one through untouched; the factory hands the stamp
        // on and never one of its own
        new OwnerMonitor("Jon", monitor).onChatResponse(new SimpleMessage(SimpleMessage.Type.TOOL, "ls in the checkout"));
        monitor.onChatResponse(new SimpleMessage(SimpleMessage.Type.TOOL, "read it back"));
        endTheTurn();

        // one `agents` snapshot per callback is the monitor's own rule (R-WP-7) — the steps are the tool lines
        assertThat(wire.names()).containsExactly("agents", "tool", "agents", "tool", "done");
        assertThat(wire.payload(1).keySet()).as("a stamped line names the agent that worked")
                .containsExactly("text", "origin");
        assertThat(wire.payload(1)).containsEntry("origin", "Jon");
        assertThat(wire.payload(3).keySet()).as("an unstamped line keeps the payload it always had")
                .containsExactly("text");
    }

    // UC-WP-72
    @Test
    void theDelegationSpeaksItsOwnStartAndEnd() {
        // the delegation does not arrive on an AiMonitor callback but on the delegation tool's own listeners,
        // and those carry (CallStats, AiAgent): the member is all the wire gets, so he is all it says
        // (core PoDelegateTool.java:227-245, CallStats.java:34-45 — start before the run, end in its finally)
        CallStats stats = CallStats.start();
        monitor.onDelegationStart(stats, memberNamed("Dev"));
        monitor.onDelegationEnd(stats, memberNamed("Dev"));
        // both directions of a member nobody can name: a line naming nobody states nothing, so it stays out
        monitor.onDelegationStart(stats, memberNamed("   "));
        monitor.onDelegationEnd(stats, memberNamed("   "));
        endTheTurn();

        assertThat(wire.names()).as("one event per direction, and nothing for a nameless member")
                .containsExactly("delegation", "delegation", "done");
        assertThat(wire.payload(0).keySet()).as("both fields always — the receiver needs the pair")
                .containsExactly("member", "working");
        assertThat(wire.payload(0)).containsEntry("member", "Dev").containsEntry("working", true);
        assertThat(wire.payload(1)).as("the end names the member too, or no entry could be closed")
                .containsEntry("member", "Dev").containsEntry("working", false);
    }

    private static String lines(String name) {
        return IntStream.range(0, 1200).mapToObj(i -> name + "-" + i).reduce("", (a, b) -> a + b + "\n");
    }

    /** The last event of the turn: order is kept per connection, so everything before it is delivered too. */
    private void endTheTurn() {
        registry.publish(PROJECT, new ChatEvent.Done(1L));
        awaitUntil("the done marker reached the wire", () -> wire.names().contains("done"));
    }

    /** Waiting on a deadline, not on a fixed pause: delivery runs on the subscriber's own thread. */
    private static void awaitUntil(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("never reached within 5 s: " + what);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting");
            }
        }
    }

    /**
     * A member as the tool's listener hands him over. The monitor asks exactly one question of an agent —
     * his name — so this double answers that one and refuses every other instead of standing in for a
     * running agent, which is also what keeps the blank-name case reachable: no real agent answers blank.
     */
    private static AiAgent memberNamed(String name) {
        return new AiAgent() {

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getSystemPrompt() {
                throw unreadable();
            }

            @Override
            public ChatResponse call(String message, AiMonitor monitor) {
                throw unreadable();
            }

            @Override
            public CompactResult compact(AiMonitor monitor) {
                throw unreadable();
            }

            @Override
            public List<ChatMessage> buildStaticMessages(AiMonitor monitor) {
                throw unreadable();
            }

            @Override
            public ThreadSafeMemory getMemory() {
                throw unreadable();
            }

            @Override
            public int tokenContextUsedInPercent() {
                throw unreadable();
            }

            @Override
            public void clear() {
                throw unreadable();
            }

            @Override
            public boolean isToolActive(SmartToolExecutor exec) {
                throw unreadable();
            }

            @Override
            public boolean isMcpToolActive(String toolName) {
                throw unreadable();
            }
        };
    }

    private static UnsupportedOperationException unreadable() {
        return new UnsupportedOperationException("a delegation event reads a name and nothing else");
    }

    /**
     * What the socket would receive: {@code build()} is public {@link SseEmitter} API, so the test reads the
     * same pieces the registry wrote — the {@code event:<name>} prefix as text and the JSON body as the one
     * piece tagged {@code APPLICATION_JSON}.
     */
    private static final class RecordingEmitter extends SseEmitter {

        private final List<DataWithMediaType> sent = new CopyOnWriteArrayList<>();

        RecordingEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder event) {
            this.sent.addAll(event.build());
        }

        /** The event names in arrival order — one entry per delivered event. */
        List<String> names() {
            List<String> names = new ArrayList<>();
            for (DataWithMediaType piece : this.sent) {
                if (piece.getData() instanceof String text && text.startsWith("event:")) {
                    names.add(text.substring("event:".length(), text.indexOf('\n')));
                }
            }
            return names;
        }

        /** The JSON body of the n-th event, as the browser parses it. */
        Map<String, Object> payload(int index) {
            List<String> bodies = new ArrayList<>();
            for (DataWithMediaType piece : this.sent) {
                if (MediaType.APPLICATION_JSON.equals(piece.getMediaType())) {
                    bodies.add((String) piece.getData());
                }
            }
            String body = bodies.get(index);
            try {
                return new ObjectMapper().readValue(body, new TypeReference<Map<String, Object>>() {
                });
            } catch (IOException unreadable) {
                throw new IllegalStateException("payload is not JSON: " + body, unreadable);
            }
        }
    }
}

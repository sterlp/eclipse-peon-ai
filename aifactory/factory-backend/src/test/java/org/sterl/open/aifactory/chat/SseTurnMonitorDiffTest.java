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

import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.OwnerMonitor;

/**
 * The translation of one core callback into one live line — over the real {@link ChatStreamRegistry} and
 * a recording emitter, no Spring context: nothing between {@code onFileUpdate} and the wire is a bean.
 *
 * <p>Delivery runs on the subscriber's own thread, so a count of received events only means "all of them"
 * once the turn is known to be over. Every test therefore closes its events with a {@code done} marker and
 * waits for <em>that</em> — a connection keeps its order, so when the marker has landed, whatever the turn
 * published before it has landed too. "Exactly one diff, no tool line beside it" is then proven and not
 * merely observed early.</p>
 */
@Timeout(30)
class SseTurnMonitorDiffTest {

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
                .startsWith("--- a/" + DIFF_FILE + "\n+++ b/" + DIFF_FILE + "\n@@ -1,2 +1,3 @@\n")
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
    void anUnchangedFileShowsNothing() {
        String unchanged = "a\nb\n";
        monitor.onFileUpdate(new AiFileUpdate(DIFF_FILE, unchanged, unchanged));
        endTheTurn();

        // the marker is what proves the empty wire is an empty change and not a broken connection
        assertThat(wire.names()).containsExactly("done");
    }

    // UC-WP-68
    @Test
    void aFileTooLargeForADiffIsAToolLine() {
        // 1200 x 1200 cells is over SimpleDiff's own LCS bound — it answers with a summary, not a diff
        monitor.onFileUpdate(new AiFileUpdate("src/Big.java", lines("old"), lines("new")));
        endTheTurn();

        assertThat(wire.names()).as("a summary is a line, not a coloured block").containsExactly("tool", "done");
        assertThat(wire.payload(0).keySet()).containsExactly("text");
        assertThat((String) wire.payload(0).get("text"))
                .startsWith("file updated, diff skipped for src/Big.java");
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

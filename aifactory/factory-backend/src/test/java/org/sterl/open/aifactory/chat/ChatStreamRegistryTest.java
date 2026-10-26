package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The registry is the only class that owns the wire, so its cleanup rule is proven here — over real
 * {@link SseEmitter}s, not a stand-in: R-WP-5's "reload verliert nur Live-Zeilen" only holds if a gone
 * client stops being addressed and the turn survives it.
 */
@Timeout(30)
class ChatStreamRegistryTest {

    @Test
    void deadSubscriberStopsBeingAddressed() {
        ChatStreamRegistry registry = new ChatStreamRegistry();
        // Given one live and one already completed subscriber, plus a subscriber of another project
        SseEmitter alive = registry.subscribe("p1");
        SseEmitter dead = registry.subscribe("p1");
        SseEmitter otherProject = registry.subscribe("p2");
        dead.complete();
        assertThat(registry.listenerCount("p1")).isEqualTo(2);

        // When the dead one is addressed — the client of a reloaded browser tab
        // Then nothing escapes to the turn and only the dead subscriber is gone (his own thread notices)
        assertThatCode(() -> registry.publish("p1", new ChatEvent.Partial("answer", "hi")))
                .doesNotThrowAnyException();
        awaitUntil("dead subscriber dropped", () -> registry.listenerCount("p1") == 1);
        assertThat(registry.listenerCount("p2")).isOne();
        assertThatCode(() -> registry.publish("never-subscribed", new ChatEvent.Done(5L)))
                .doesNotThrowAnyException();

        // And the survivor is still addressable
        assertThatCode(() -> registry.publish("p1", new ChatEvent.Done(7L))).doesNotThrowAnyException();
        assertThat(registry.listenerCount("p1")).isOne();
        assertThat(alive).isNotNull();
        assertThat(otherProject).isNotNull();
    }

    @Test
    void twoDeadListenersLeaveNoProjectBehind() {
        ChatStreamRegistry registry = new ChatStreamRegistry();
        SseEmitter first = registry.subscribe("p1");
        SseEmitter second = registry.subscribe("p1");
        registry.subscribe("p2");
        first.complete();
        second.complete();
        assertThat(registry.projectCount()).as("two projects are watched").isEqualTo(2);

        // When the last listener of a project is gone — the next write is what notices
        registry.publish("p1", new ChatEvent.Done(1L));

        awaitUntil("project p1 left the map", () -> registry.projectCount() == 1);
        assertThat(registry.listenerCount("p1")).isZero();
        assertThat(registry.listenerCount("p2")).as("the other project is untouched").isOne();
    }

    @Test
    void subscriberIsNeverTimedOut() {
        ChatStreamRegistry registry = new ChatStreamRegistry();

        // Tomcat's async default of 30 s would otherwise kill the connection inside a long turn
        assertThat(registry.subscribe("p1").getTimeout()).isZero();
    }

    @Test
    void closedProjectAndShutdownLeaveNothingBehind() {
        ChatStreamRegistry registry = new ChatStreamRegistry();
        RecordingEmitter first = new RecordingEmitter();
        RecordingEmitter second = new RecordingEmitter();
        registry.subscribe("p1", first);
        registry.subscribe("p1", second);
        registry.subscribe("p2", new RecordingEmitter());

        registry.closeProject("p1");

        // the project is out of the map and its listeners receive nothing afterwards
        assertThat(registry.listenerCount("p1")).isZero();
        registry.publish("p1", new ChatEvent.Done(3L));
        awaitQuietly();
        assertThat(first.sent).isEmpty();
        assertThat(second.sent).isEmpty();

        registry.closeAll();
        assertThat(registry.listenerCount("p2")).isZero();
    }

    // UC-WP-41
    @Test
    void slowReaderNeverStopsTheTurn() throws Exception {
        ChatStreamRegistry registry = new ChatStreamRegistry();
        CountDownLatch stuckInHisOwnWrite = new CountDownLatch(1);
        CountDownLatch releaseSlowReader = new CountDownLatch(1);
        // Given a subscriber who blocks in his own write and a second one reading normally
        BlockingEmitter slowReader = new BlockingEmitter(stuckInHisOwnWrite, releaseSlowReader);
        registry.subscribe("p1", slowReader);
        RecordingEmitter fastReader = new RecordingEmitter();
        registry.subscribe("p1", fastReader);
        registry.publish("p1", new ChatEvent.Partial("answer", "an-1"));
        assertThat(stuckInHisOwnWrite.await(5, TimeUnit.SECONDS)).as("slow reader is stuck").isTrue();

        // When a turn keeps publishing — Then it is through, not waiting for the reader who does not read
        long startedBefore = System.nanoTime();
        for (int i = 2; i <= 6; i++) {
            registry.publish("p1", new ChatEvent.Partial("answer", "an-" + i));
        }
        long millisSpent = (System.nanoTime() - startedBefore) / 1_000_000;
        assertThat(millisSpent).as("publish must not wait for the slow reader (" + millisSpent + " ms)")
                .isLessThan(2_000L);

        // Then the reading subscriber gets every line while the other one is still stuck
        awaitUntil("all six lines at the fast reader", () -> fastReader.sent.size() == 6);
        assertThat(stuckInHisOwnWrite.getCount()).as("slow reader still stuck").isZero();
        assertThat(registry.listenerCount("p1")).as("both still subscribed").isEqualTo(2);

        // And what was still queued for the slow reader is not written after he was closed: he keeps the
        // line he was already writing and nothing behind it
        registry.publish("p1", new ChatEvent.Partial("answer", "wartend"));
        registry.closeProject("p1");
        releaseSlowReader.countDown();
        settle(300);
        assertThat(slowReader.sends())
                .as("one line in flight, nothing that was queued behind him")
                .isOne();
        assertThat(registry.listenerCount("p1")).isZero();
    }

    /** A short grace window for a negative assertion — the only fixed wait in this class. */
    private static void settle(long millis) {
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            awaitQuietly();
        }
    }

    /**
     * Blocks in {@code send} like a socket whose peer stopped reading — ten seconds, so no timing guess.
     * Overriding the {@code SseEventBuilder} overload is the one the registry writes through.
     */
    private static final class BlockingEmitter extends SseEmitter {

        private final CountDownLatch enteredSend;
        private final CountDownLatch released;
        private final AtomicInteger sends = new AtomicInteger();

        BlockingEmitter(CountDownLatch enteredSend, CountDownLatch released) {
            super(0L);
            this.enteredSend = enteredSend;
            this.released = released;
        }

        @Override
        public void send(SseEventBuilder event) {
            sends.incrementAndGet();
            enteredSend.countDown();
            try {
                released.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        int sends() {
            return sends.get();
        }
    }

    private static final class RecordingEmitter extends SseEmitter {

        private final List<Object> sent = new CopyOnWriteArrayList<>();

        RecordingEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder event) {
            this.sent.add(event);
        }
    }

    /** Waiting on a deadline, not on a fixed pause: delivery runs on the subscriber's own thread. */
    private static void awaitUntil(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("never reached within 5 s: " + what);
            }
            awaitQuietly();
        }
    }

    private static void awaitQuietly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting");
        }
    }
}

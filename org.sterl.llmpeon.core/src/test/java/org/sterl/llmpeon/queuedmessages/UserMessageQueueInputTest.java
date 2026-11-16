package org.sterl.llmpeon.queuedmessages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.NL;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.T_1432;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.file;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.picA;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.agent.model.QueuedInput;
import org.sterl.llmpeon.agent.model.UserInput;

/** The queue holds QueuedInput: text bursts batch as before, attachments never merge, urgent never carries them. */
@Timeout(1)
class UserMessageQueueInputTest {

    private final MutableClock clock = new MutableClock();

    private static List<String> texts(List<QueuedInput> entries) {
        return entries.stream().map(e -> e.input().text()).toList();
    }

    // queued-user-messages Regel 1
    // queued-user-messages Regel 9
    // queued-user-messages Regel 13
    @Test
    void pureTextBatchKeepsFirstTimeAndSlidingWindow() {
        var queue = new UserMessageQueue(100, clock);

        assertThat(queue.add("a")).isTrue();
        clock.advance(60);
        assertThat(queue.add(UserInput.of("b"))).isFalse();
        clock.advance(60); // 120 ms after "a", but only 60 after the last merge: the window slides
        assertThat(queue.add("c")).isFalse();
        clock.advance(101); // past the window: a new entry
        assertThat(queue.add(UserInput.of("d"))).isTrue();
        assertThat(queue.add("e".repeat(300))).isTrue(); // 300 chars plus the joiner exceed the cap

        var entries = queue.drainAllEntries();
        assertThat(texts(entries)).containsExactly("a" + NL + "b" + NL + "c", "d", "e".repeat(300));
        assertThat(entries.get(0).queuedAt()).isEqualTo(Instant.ofEpochMilli(T_1432));
        assertThat(entries.get(1).queuedAt()).isEqualTo(Instant.ofEpochMilli(T_1432 + 221));
    }

    // queued-user-messages Regel 1
    // queued-user-messages Regel 13
    @Test
    void attachmentEntryIsABarrierBeforeAndAfterIt() {
        var queue = new UserMessageQueue(10_000, clock);

        assertThat(queue.add("A")).isTrue();
        clock.advance(10);
        var attached = new UserInput("B", List.of(file()));
        assertThat(queue.add(attached)).isTrue();
        clock.advance(10);
        assertThat(queue.add("C")).isTrue(); // opens a new window behind the barrier
        clock.advance(10);
        assertThat(queue.add("D")).isFalse(); // … which batches again

        var entries = queue.drainAllEntries();
        assertThat(texts(entries)).containsExactly("A", "B", "C" + NL + "D");
        assertThat(entries.get(1).input().attachments()).hasSize(1);
        assertThat(entries.get(1).input().attachments().getFirst().toString())
                .isEqualTo("//-- Start file: a.txt" + NL + "hello" + NL + "//-- End file: a.txt");
        assertThat(entries.get(0).input().attachments()).isEmpty();
        assertThat(entries.get(2).input().attachments()).isEmpty();
    }

    // queued-user-messages Regel 13
    // R-TR-11
    @Test
    void attachmentOnlyInputQueuesButEmptyInputDoesNot() {
        var queue = new UserMessageQueue(10_000, clock);

        assertThat(queue.add(UserInput.of(null))).isFalse();
        assertThat(queue.add(UserInput.of("  "))).isFalse();
        assertThat(queue.size()).isZero();

        assertThat(queue.add(new UserInput(null, List.of(file())))).isTrue();
        assertThat(queue.add(new UserInput("   ", List.of(picA())))).isTrue();
        assertThat(queue.size()).isEqualTo(2);

        var entries = queue.drainAllEntries();
        assertThat(texts(entries)).containsExactly(null, "   "); // text is never rewritten
        assertThat(entries).extracting(e -> e.input().attachments().size()).containsExactly(1, 1);
        assertThat(entries).extracting(QueuedInput::urgent).containsExactly(false, false);
    }

    // queued-user-messages Regel 8
    // queued-user-messages Regel 13
    @Test
    void urgentInputRejectsAttachmentsWithoutMutatingEitherLane() {
        var queue = new UserMessageQueue(10_000, clock);
        queue.add("n1");
        queue.addUrgent("u1");

        assertThatThrownBy(() -> queue.addUrgent(new UserInput("x", List.of(file()))))
                .isInstanceOf(IllegalArgumentException.class);
        // rejected even where blank text would otherwise make it a no-op
        assertThatThrownBy(() -> queue.addUrgent(new UserInput(null, List.of(picA()))))
                .isInstanceOf(IllegalArgumentException.class);
        queue.addUrgent(UserInput.of("  ")); // R8.6 unchanged: blank is consumed, nothing queued

        assertThat(queue.size()).isEqualTo(2);
        var entries = queue.drainAllEntries();
        assertThat(texts(entries)).containsExactly("n1", "u1");
        assertThat(entries).extracting(QueuedInput::urgent).containsExactly(false, true);
        assertThat(entries).allSatisfy(e -> assertThat(e.input().attachments()).isEmpty());
    }

    // queued-user-messages Regel 2
    // queued-user-messages Regel 8
    // queued-user-messages Regel 10
    // queued-user-messages Regel 13
    @Test
    void fifoDrainsAndTimestampTiesPreserveLaneAndAllAttachments() {
        var queue = new UserMessageQueue(10_000, clock);
        queue.add("n1");                                         // t0
        queue.addUrgent("u1");                                   // t0 — tie: the normal lane goes first
        clock.advance(5);
        queue.addUrgent("u2");                                   // t0+5
        queue.add(new UserInput("n2", List.of(file(), picA()))); // t0+5 — tie: normal lane first
        clock.advance(5);
        queue.add(new UserInput(null, List.of(picA())));         // t0+10

        var polled = List.of(queue.pollNext(), queue.pollNext(), queue.pollNext(), queue.pollNext());
        assertThat(texts(polled)).containsExactly("n1", "u1", "n2", "u2");
        assertThat(polled).extracting(QueuedInput::urgent).containsExactly(false, true, false, true);
        assertThat(polled.get(2).input().attachments()).hasSize(2);

        var rest = queue.drainAllEntries();
        assertThat(texts(rest)).containsExactly((String) null);
        assertThat(rest.getFirst().input().attachments()).hasSize(1);
        assertThat(queue.pollNext()).isNull();

        queue.add("m1");
        queue.addUrgent("v1");
        queue.add(new UserInput("m2", List.of(file())));
        assertThat(texts(queue.drainUrgent())).containsExactly("v1");
        assertThat(queue.drainAllEntries()).extracting(e -> e.input().attachments().size()).containsExactly(0, 1);
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(T_1432);

        void advance(long ms) { millis.addAndGet(ms); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
        @Override public long millis() { return millis.get(); }
    }
}

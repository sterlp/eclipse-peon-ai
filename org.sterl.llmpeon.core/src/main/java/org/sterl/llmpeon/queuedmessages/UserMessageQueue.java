package org.sterl.llmpeon.queuedmessages;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

/**
 * The follow-up inputs a user sends while an agent is still working.
 * <p>
 * Every mutation is guarded by the <b>instance monitor</b> — {@code AbstractAgent} claims and hands
 * over its turn slot under that same monitor, which is what keeps an input from arriving between a
 * turn's last poll and its release. Changing the locking here (a private lock, a concurrent queue)
 * silently breaks that hand-over; the queue instance must also stay stable for a running turn.
 * <p>
 * The queue has <b>two lanes</b>: the normal one, whose rapid-fire inputs batch into one entry
 * (Rule 1), and the {@code !}-urgent one (Rule 8), which never batches. They are separate containers
 * but <b>one order</b>: every consumer that does not name a lane ({@link #pollNext()},
 * {@link #drainAll()}, {@link #drainAllEntries()}, {@link #size()}) sees both by insertion time —
 * the urgent lane only gets there earlier, it never jumps the queue (R8.3). Same millisecond is
 * resolved in favour of the normal lane; an exact sequence counter is not worth pinning
 * {@link QueuedMessage} to one.
 */
public class UserMessageQueue {

    /**
     * Prefix of the Rule 9 LLM marker — the single home of this literal. The compact stage-1
     * "last real user message" check excludes messages starting with it (org.sterl.llmpeon.core/docs/compact.md R-CIB-4).
     */
    public static final String QUEUED_MARKER_PREFIX = "[Queued Message] ";

    /** A queued message: its text plus the instant it was queued (Rule 9 Queued-At Disclosure). */
    public record QueuedMessage(String text, long queuedAt) {}

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final Deque<QueuedMessage> queue = new ArrayDeque<>();
    private final Deque<QueuedMessage> urgent = new ArrayDeque<>();
    /** Batch window of the NORMAL lane only — an urgent entry neither joins it nor resets it (R8.2). */
    private volatile long batchStartTime = 0;
    private final long batchWindowMs;
    private final Clock clock;

    public UserMessageQueue() { this(10_000); } // Production default: 10s window

    /** @param batchWindowMs configurable window for tests (e.g. 250ms) */
    public UserMessageQueue(long batchWindowMs) { this(batchWindowMs, Clock.systemDefaultZone()); }

    /**
     * @param clock injectable clock (tests) that stamps {@code queuedAt} and renders the
     *        {@code HH:mm} label — production uses the system default-zone clock.
     */
    public UserMessageQueue(long batchWindowMs, Clock clock) {
        this.batchWindowMs = batchWindowMs;
        this.clock = clock;
    }

    /**
     * Add a message to the normal lane, optionally merging with the last entry within the sliding window.
     * @return true if a new queue entry was created, false if silently merged into existing batch
     */
    public synchronized boolean add(String message) {
        if (message == null || message.isBlank()) return false;
        long now = clock.millis();

        boolean startNewBatch = queue.isEmpty() || (now - batchStartTime > batchWindowMs);
        String combined = message;
        long queuedAt = now;

        // Allow merging even for longer incoming messages, as long as capacity permits
        if (!startNewBatch) {
            QueuedMessage last = queue.removeLast();
            String sep = System.lineSeparator();
            int newLen = last.text().length() + sep.length() + message.length();
            if (newLen <= 300) {
                combined = last.text() + sep + message;
                queuedAt = last.queuedAt(); // burst-join keeps the FIRST entry's time (Rule 9)
                startNewBatch = false; // explicitly merged
            } else {
                queue.addLast(last); // cap exceeded, restore & add separate
                startNewBatch = true; // explicitly mark as new entry
            }
        }

        queue.addLast(new QueuedMessage(combined, queuedAt));
        batchStartTime = now; // sliding window reset
        return startNewBatch; // true if new entry created, false if silently joined
    }

    /**
     * Add a {@code !}-message to the urgent lane (R8): one entry per call, never joined, and the
     * normal lane's batch window is left alone — otherwise an urgent message would split a burst.
     * Blank input is consumed as a no-op (R8.6).
     */
    public synchronized void addUrgent(String message) {
        if (message == null || message.isBlank()) return;
        urgent.addLast(new QueuedMessage(message, clock.millis()));
    }

    /**
     * Take the whole urgent lane in insertion order (R11 hands all of it to one tool result).
     * The normal lane and its batch window are untouched.
     */
    public synchronized List<QueuedMessage> drainUrgent() {
        return drain(urgent);
    }

    /**
     * Take BOTH lanes as separate entries, ordered by insertion time across lanes (R12: one line per
     * entry). Contrast {@link #drainAll()}, which merges into a single payload text.
     */
    public synchronized List<QueuedMessage> drainAllEntries() {
        var entries = bothLanes().sorted(Comparator.comparingLong(QueuedMessage::queuedAt)).toList();
        queue.clear();
        urgent.clear();
        batchStartTime = 0;
        return entries;
    }

    /** Poll the earliest entry of either lane — the urgent lane never jumps the order (R8.3). */
    public synchronized QueuedMessage pollNext() {
        if (urgent.isEmpty()) return queue.pollFirst();
        if (queue.isEmpty()) return urgent.pollFirst();
        // <= keeps the normal lane first on a timestamp tie (stable order, no sequence counter)
        return queue.peekFirst().queuedAt() <= urgent.peekFirst().queuedAt()
                ? queue.pollFirst() : urgent.pollFirst();
    }

    public synchronized QueuedMessage drainAll() {
        var texts = bothLanes().sorted(Comparator.comparingLong(QueuedMessage::queuedAt)).toList();
        if (texts.isEmpty()) return null;
        // Burst-join shows the FIRST entry's time (Rule 9)
        long firstQueuedAt = texts.get(0).queuedAt();
        String combined = String.join(System.lineSeparator(),
                texts.stream().map(QueuedMessage::text).toList());
        queue.clear();
        urgent.clear();
        batchStartTime = 0;
        return new QueuedMessage(combined, firstQueuedAt);
    }

    /** Renders a stored {@code queuedAt} as {@code HH:mm} in this queue's clock zone (Rule 9). */
    public String queuedLabel(long queuedAt) {
        return Instant.ofEpochMilli(queuedAt).atZone(clock.getZone()).format(HHMM);
    }

    public synchronized int size() { return queue.size() + urgent.size(); }
    public synchronized void clear() { queue.clear(); urgent.clear(); batchStartTime = 0; }

    private Stream<QueuedMessage> bothLanes() { return Stream.concat(queue.stream(), urgent.stream()); }

    private static List<QueuedMessage> drain(Deque<QueuedMessage> lane) {
        var taken = List.copyOf(lane);
        lane.clear();
        return taken;
    }
}

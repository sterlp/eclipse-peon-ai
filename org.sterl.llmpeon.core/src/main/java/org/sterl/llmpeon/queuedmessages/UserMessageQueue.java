package org.sterl.llmpeon.queuedmessages;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

import org.sterl.llmpeon.agent.model.QueuedInput;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.shared.StringUtil;

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
 * {@link QueuedInput} to one.
 */
public class UserMessageQueue {

    /**
     * Prefix of the Rule 9 LLM marker — the single home of this literal. The compact stage-1
     * "last real user message" check excludes messages starting with it (org.sterl.llmpeon.core/docs/compact.md R-CIB-4).
     */
    public static final String QUEUED_MARKER_PREFIX = "[Queued Message] ";

    /** Legacy text view of a drained queue (joined text + first time); no attachments — see {@link #drainAll()}. */
    public record QueuedMessage(String text, long queuedAt) {}

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final Deque<QueuedInput> queue = new ArrayDeque<>();
    private final Deque<QueuedInput> urgent = new ArrayDeque<>();
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

    public synchronized boolean add(String message) {
        return add(UserInput.of(message));
    }

    /**
     * Add an input to the normal lane, optionally merging a plain-text input with the last plain-text
     * entry within the sliding window. An input with attachments is always an entry of its own and a
     * batch barrier: it joins no predecessor and no successor joins it (Rule 13).
     * @return true if a new queue entry was created, false if silently merged into existing batch
     */
    public synchronized boolean add(UserInput input) {
        boolean plain = input.attachments().isEmpty();
        if (plain && StringUtil.hasNoValue(input.text())) return false;
        long now = clock.millis();

        var last = queue.peekLast();
        boolean mayJoin = plain && last != null && last.input().attachments().isEmpty()
                && now - batchStartTime <= batchWindowMs;
        if (mayJoin) {
            String sep = System.lineSeparator();
            // Allow merging even for longer incoming messages, as long as capacity permits
            if (last.input().text().length() + sep.length() + input.text().length() <= 300) {
                queue.removeLast();
                // burst-join keeps the FIRST entry's time (Rule 9)
                queue.addLast(new QueuedInput(UserInput.of(last.input().text() + sep + input.text()),
                        last.queuedAt(), false));
                batchStartTime = now; // sliding window reset
                return false;
            }
        }
        queue.addLast(new QueuedInput(input, Instant.ofEpochMilli(now), false));
        batchStartTime = now; // sliding window reset
        return true;
    }

    public synchronized void addUrgent(String message) {
        addUrgent(UserInput.of(message));
    }

    /**
     * Add a {@code !}-message to the urgent lane (R8): one entry per call, never joined, and the
     * normal lane's batch window is left alone — otherwise an urgent message would split a burst.
     * Blank input is consumed as a no-op (R8.6). Attachments are rejected — also on blank text,
     * and before anything is mutated (Rule 13).
     */
    public synchronized void addUrgent(UserInput input) {
        var entry = new QueuedInput(input, Instant.ofEpochMilli(clock.millis()), true);
        if (StringUtil.hasNoValue(input.text())) return;
        urgent.addLast(entry);
    }

    /**
     * Take the whole urgent lane in insertion order (R11 hands all of it to one tool result).
     * The normal lane and its batch window are untouched.
     */
    public synchronized List<QueuedInput> drainUrgent() {
        return drain(urgent);
    }

    /**
     * Take BOTH lanes as separate entries, ordered by insertion time across lanes (R12: one line per
     * entry), attachments intact. Contrast {@link #drainAll()}, the text-only legacy projection.
     */
    public synchronized List<QueuedInput> drainAllEntries() {
        var entries = byTime();
        queue.clear();
        urgent.clear();
        batchStartTime = 0;
        return entries;
    }

    /** Poll the earliest entry of either lane — the urgent lane never jumps the order (R8.3). */
    public synchronized QueuedInput pollNext() {
        if (urgent.isEmpty()) return queue.pollFirst();
        if (queue.isEmpty()) return urgent.pollFirst();
        // <= keeps the normal lane first on a timestamp tie (stable order, no sequence counter)
        return queue.peekFirst().queuedAt().compareTo(urgent.peekFirst().queuedAt()) <= 0
                ? queue.pollFirst() : urgent.pollFirst();
    }

    /**
     * Legacy text projection: joins both lanes into one text. Refuses before draining when an entry
     * carries attachments — they would be lost silently; typed consumers use {@link #drainAllEntries()}.
     */
    public synchronized QueuedMessage drainAll() {
        var texts = byTime();
        if (texts.isEmpty()) return null;
        if (hasAttachments()) {
            throw new IllegalStateException("queued attachments cannot be drained as plain text");
        }
        // Burst-join shows the FIRST entry's time (Rule 9)
        long firstQueuedAt = texts.get(0).queuedAt().toEpochMilli();
        String combined = String.join(System.lineSeparator(),
                texts.stream().map(e -> e.input().text()).toList());
        queue.clear();
        urgent.clear();
        batchStartTime = 0;
        return new QueuedMessage(combined, firstQueuedAt);
    }

    public synchronized boolean hasAttachments() {
        return bothLanes().anyMatch(e -> !e.input().attachments().isEmpty());
    }

    /** Renders a stored {@code queuedAt} as {@code HH:mm} in this queue's clock zone (Rule 9). */
    public String queuedLabel(Instant queuedAt) {
        return queuedAt.atZone(clock.getZone()).format(HHMM);
    }

    public String queuedLabel(long queuedAt) {
        return queuedLabel(Instant.ofEpochMilli(queuedAt));
    }

    public synchronized int size() { return queue.size() + urgent.size(); }
    public synchronized void clear() { queue.clear(); urgent.clear(); batchStartTime = 0; }

    private List<QueuedInput> byTime() {
        return bothLanes().sorted(Comparator.comparing(QueuedInput::queuedAt)).toList();
    }

    private Stream<QueuedInput> bothLanes() { return Stream.concat(queue.stream(), urgent.stream()); }

    private static List<QueuedInput> drain(Deque<QueuedInput> lane) {
        var taken = List.copyOf(lane);
        lane.clear();
        return taken;
    }
}

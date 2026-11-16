package org.sterl.llmpeon.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.model.QueuedInput;
import org.sterl.llmpeon.agent.model.TaggedInput;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.queuedmessages.QueuedUserMessages;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;

import dev.langchain4j.data.message.UserMessage;

/**
 * askUser's queue delivery (queued-user-messages Rule 12/13, R-RP-1) and the keeper of everything taken
 * out of the queue but not yet in the memory (Rule 4/13). The state lives in the agent's instance, never
 * in a shared adapter, and is guarded by the queue's monitor.
 */
public final class QueuedMessageDelivery {

    private final UserMessageBuilder messageBuilder = new UserMessageBuilder();
    private final List<UserInput> turnInputs = new ArrayList<>();
    private final List<UserInput> roundInputs = new ArrayList<>();
    private int turnEntries;


    public String prependToAnswer(AiAgent agent, AiMonitor monitor, String answer,
            Consumer<UserMessage> afterResults) {
        var delivery = QueuedUserMessages.delivery(agent.takeAllInputs(monitor));
        if (delivery.block() == null) return answer;
        delivery.pictures().forEach(p -> afterResults.accept(messageBuilder.from(p)));
        return delivery.block() + TaggedInput.LN + answer;
    }

    /** Kept until {@link #commitTurn}. */
    UserInput startInput(UserMessageQueue queue, UserInput initial) {
        synchronized (queue) {
            var entries = queue.drainAllEntries();
            if (entries.isEmpty()) return initial;
            entries.forEach(e -> turnInputs.add(e.input()));
            turnEntries += entries.size();
            if (!messageBuilder.build(List.of(), initial).isEmpty()) turnInputs.add(initial);
            return QueuedUserMessages.startInput(queue, entries, initial);
        }
    }

    /** Also called inside the caller's monitor section (turn-slot hand-over). */
    @Nullable QueuedInput pollNext(UserMessageQueue queue) {
        synchronized (queue) {
            var entry = queue.pollNext();
            if (entry != null) {
                turnInputs.add(entry.input());
                turnEntries++;
            }
            return entry;
        }
    }

    /**
     * queued-user-messages Regel 13, see {@code ThreadSafeMemory.add(ChatMessage, Runnable)}. The add runs
     * outside the queue's monitor — it calls foreign render/monitor code.
     */
    void commitTurn(UserMessageQueue queue, Consumer<Runnable> appendTurnMessage) {
        var ram = new boolean[1];
        try {
            appendTurnMessage.accept(() -> ram[0] = true);
        } finally {
            if (ram[0]) {
                synchronized (queue) {
                    turnInputs.clear();
                    turnEntries = 0;
                }
            }
        }
    }

    /** Registered BEFORE the first disclosure: a cancel inside that callback must not lose them (Rule 13). */
    List<UserInput> takeInputs(UserMessageQueue queue, AiMonitor monitor, Supplier<List<QueuedInput>> drainLane) {
        var mon = AiMonitor.nullSafety(monitor);
        synchronized (queue) {
            var entries = drainLane.get();
            entries.forEach(e -> roundInputs.add(e.input()));
            for (var entry : entries) {
                mon.onTool(QueuedUserMessages.readingLine(queue, entry));
            }
            return entries.stream().map(entry -> QueuedUserMessages.disclosed(queue, entry)).toList();
        }
    }

    void commitRound(UserMessageQueue queue) {
        synchronized (queue) {
            roundInputs.clear();
        }
    }

    void preserveOnAbort(UserMessageQueue queue, ThreadSafeMemory memory, AiMonitor monitor) {
        List<UserInput> preserved;
        int count;
        synchronized (queue) {
            var waiting = queue.drainAllEntries();
            preserved = new ArrayList<>(turnInputs);
            preserved.addAll(roundInputs);
            waiting.stream().map(QueuedInput::input).forEach(preserved::add);
            count = turnEntries + roundInputs.size() + waiting.size();
        }
        if (preserved.isEmpty()) return;
        var ram = new boolean[1];
        try {
            messageBuilder.build(List.of(), QueuedUserMessages.combine(preserved))
                    .ifPresent(m -> memory.add(m, () -> ram[0] = true));
        } finally {
            if (ram[0]) {
                synchronized (queue) {
                    turnInputs.clear();
                    roundInputs.clear();
                    turnEntries = 0;
                }
            }
        }
        monitor.onTool(count + " queued message(s) preserved for your next request.");
    }

    void clear(UserMessageQueue queue) {
        synchronized (queue) {
            queue.clear();
            turnInputs.clear();
            roundInputs.clear();
            turnEntries = 0;
        }
    }
}

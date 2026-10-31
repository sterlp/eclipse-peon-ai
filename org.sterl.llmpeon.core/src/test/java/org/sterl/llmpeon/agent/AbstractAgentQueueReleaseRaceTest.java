package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * The window between a turn's last empty {@code pollNext()} and its release of the working flag. An
 * input arriving there is taken by nobody: the running turn has stopped polling, and the newcomer
 * cannot claim the slot either, because the flag is still held. With one {@code done} per turn
 * (aifactory {@code ProjectRuntime#submit}) it waits for an answer <em>and</em> for the end-of-turn
 * event that only a later, unrelated send would ever bring.
 */
class AbstractAgentQueueReleaseRaceTest {

    /**
     * GIVEN a turn whose queue has just run empty — the loop sits between that poll and the release of
     * the working flag — WHEN a second input is sent as a request of its own THEN the running turn
     * still answers it: nothing is left queued, and the sender gets no turn and no {@code done} of
     * its own, exactly as the queue contract for an input that arrives while the agent works.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void inputArrivingWhileTheTurnReleasesItsSlotIsAnsweredByThatTurn() throws Exception {
        var queueRanEmpty = new CountDownLatch(1);
        var releaseWindowOpen = new AtomicBoolean(false);
        var turnParkedInTheWindow = new CountDownLatch(1);
        var letTheTurnRelease = new CountDownLatch(1);
        var turnFailure = new AtomicReference<Throwable>();

        var streamMock = new StreamMock();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());
        var agent = new AiDevAgent(
                new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), mockModel),
                new ToolService());
        agent.setMessageQueue(new WindowObservingQueue(queueRanEmpty, releaseWindowOpen));

        // AND — the turn is held open inside the window: isCanceled() is the one monitor call the loop
        // makes after the queue ran empty and before it hands the slot over
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public boolean isCanceled() {
                if (releaseWindowOpen.compareAndSet(true, false)) {
                    turnParkedInTheWindow.countDown();
                    awaitQuietly(letTheTurnRelease);
                }
                return false;
            }
        };

        var runningTurn = new Thread(() -> {
            try {
                agent.call("first input", monitor);
            } catch (Throwable t) {
                turnFailure.set(t);
            }
        });
        runningTurn.start();

        assertThat(await(queueRanEmpty)).as("the loop polled the queue empty").isTrue();
        assertThat(await(turnParkedInTheWindow))
                .as("the turn really is between its last poll and the release — otherwise this test proves nothing")
                .isTrue();

        // WHEN — a second input arrives exactly there, on its own thread's request (web front-end:
        // one thread per input, one `done` per turn)
        var answerOfSecondInput = agent.call("late input", m -> {});
        letTheTurnRelease.countDown();
        runningTurn.join(10_000);

        // THEN — the turn that was already running takes it, so the input is answered
        assertThat(turnFailure.get()).as("the running turn completed").isNull();
        assertThat(answerOfSecondInput).as("the running turn owns the answer — no second turn").isNull();
        assertThat(streamMock.getCallCount()).as("the late input reaches the model").isEqualTo(2);
        assertThat(agent.getQueuedMessageCount()).as("nothing waits for a turn that has ended").isZero();
        assertThat(userTexts(agent)).anySatisfy(text ->
                assertThat(text).contains("[Queued Message]", "late input"));
        assertThat(agent.isWorking()).as("the slot is free again afterwards").isFalse();
    }

    /**
     * Observes the poll that ends a turn: after it returns empty, the next thing the loop does before
     * releasing the slot is read {@link AiMonitor#isCanceled()} — the rendezvous the test uses to land
     * its input in the window instead of racing it.
     */
    private static class WindowObservingQueue extends UserMessageQueue {

        private final CountDownLatch ranEmpty;
        private final AtomicBoolean releaseWindowOpen;
        private final AtomicBoolean armed = new AtomicBoolean(true);

        WindowObservingQueue(CountDownLatch ranEmpty, AtomicBoolean releaseWindowOpen) {
            super(10_000, Clock.fixed(Instant.parse("2026-10-30T14:32:00Z"), ZoneOffset.UTC));
            this.ranEmpty = ranEmpty;
            this.releaseWindowOpen = releaseWindowOpen;
        }

        @Override
        public QueuedMessage pollNext() {
            var entry = super.pollNext();
            if (entry == null && armed.compareAndSet(true, false)) {
                releaseWindowOpen.set(true);
                ranEmpty.countDown();
            }
            return entry;
        }
    }

    private static boolean await(CountDownLatch latch) throws InterruptedException {
        return latch.await(5, TimeUnit.SECONDS);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> userTexts(AiAgent agent) {
        return agent.getMemory().getCopy().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(u -> ChatMessageUtil.toString(u.contents()).stripTrailing())
                .toList();
    }
}

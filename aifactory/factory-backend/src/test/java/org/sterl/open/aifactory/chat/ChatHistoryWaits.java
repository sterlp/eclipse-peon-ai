package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.sterl.open.aifactory.chat.dto.ChatMessageDto;

/**
 * The wait of every chat test that sends and then reads: a turn runs on the project's own thread, so the
 * history is read over the same edge a browser reads, until it says so. The deadline fails as an
 * assertion — a wait is never a proof (Inc 5).
 *
 * <p>Both waits name <b>Jon's own line</b> and never a line count: a turn that failed writes a user line
 * and an error line as well (R-WP-34), so "two lines" only says the turn is over, not that the model was
 * ever asked. That is why three tests had outgrown their own copy of this wait.</p>
 */
final class ChatHistoryWaits {

    /** A turn is a model call, not a poll: 20 s is what every caller of this used before. */
    private static final long TIMEOUT_MS = 20_000;

    private ChatHistoryWaits() {
    }

    /** At least {@code expected} answers of Jon stand in the history. */
    static void awaitAnswers(Supplier<List<ChatMessageDto>> history, int expected, String what) {
        awaitUntil(() -> history.get().stream()
                .filter(line -> ChatLine.ROLE_JON.equals(line.role())).count() >= expected, what);
    }

    /** One answer of Jon stands in the history, carrying exactly this text. */
    static void awaitAnswer(Supplier<List<ChatMessageDto>> history, String answer) {
        awaitUntil(() -> history.get().stream()
                .anyMatch(line -> ChatLine.ROLE_JON.equals(line.role()) && answer.equals(line.text())),
                "Jon hat „" + answer + "“ in den Verlauf geschrieben");
    }

    private static void awaitUntil(BooleanSupplier condition, String what) {
        long until = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis())
                    .as("%s traf vor der Frist von %d ms ein", what, TIMEOUT_MS)
                    .isLessThan(until);
            sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("unterbrochen während des Wartens auf eine Jon-Zeile",
                    interrupted);
        }
    }
}

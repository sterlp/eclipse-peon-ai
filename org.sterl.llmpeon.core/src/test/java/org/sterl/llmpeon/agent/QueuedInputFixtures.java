package org.sterl.llmpeon.agent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.sterl.llmpeon.agent.model.PictureAttachment;
import org.sterl.llmpeon.agent.model.TextAttachment;
import org.sterl.llmpeon.shared.Picture;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

/** Shared literals and a stepping clock for the typed queue tests (queued-user-messages Regel 13). */
public final class QueuedInputFixtures {

    public static final String NL = System.lineSeparator();
    public static final Picture PIC_A = new Picture("a.png", "image/png", "QUJD", 3);
    public static final Picture PIC_B = new Picture("b.png", "image/png", "REVG", 3);

    public static final String FILE_BLOCK =
            "//-- Start file: a.txt" + NL + "hello" + NL + "//-- End file: a.txt";
    public static final String PIC_A_HEAD =
            "//-- Start picture: a.png" + NL + "attached by the user" + NL + "//-- End picture: a.png";
    public static final String PIC_B_HEAD =
            "//-- Start picture: b.png" + NL + "attached by the user" + NL + "//-- End picture: b.png";

    private QueuedInputFixtures() {}

    public static TextAttachment file() { return new TextAttachment("a.txt", "hello"); }
    public static PictureAttachment picA() { return new PictureAttachment(PIC_A); }
    public static PictureAttachment picB() { return new PictureAttachment(PIC_B); }

    public static final long T_1432 = Instant.parse("2026-09-23T14:32:00Z").toEpochMilli();

    public static Clock fixed1432() {
        return Clock.fixed(Instant.ofEpochMilli(T_1432), ZoneOffset.UTC);
    }

    /** Every read of {@code millis()} moves one minute on: 14:32, 14:33, … — one queue stamp each. */
    public static Clock steppingMinutes() {
        return new Clock() {
            private final AtomicLong millis = new AtomicLong(T_1432);
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
            @Override public long millis() { return millis.getAndAdd(60_000); }
        };
    }

    public static List<UserMessage> userMessages(AiAgent agent) {
        return agent.getMemory().getCopy().stream().filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast).toList();
    }

    public static List<String> types(List<ChatMessage> messages) {
        return messages.stream().map(m -> m.type().name()).toList();
    }
}

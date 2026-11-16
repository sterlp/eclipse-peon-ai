package org.sterl.llmpeon.agent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.shared.Picture;

@Timeout(1)
class QueuedInputModelTest {

    private static final Instant AT = Instant.ofEpochMilli(1_700_000_000_123L);
    private static final Picture PNG = new Picture("b.png", "image/png", "iVBORw0KGgoAAAANSUhEUg==", 18);

    // queued-user-messages Regel 13
    @Test
    void urgentAttachmentsAreRejectedBeforeDelivery() {
        var withFile = new UserInput("hi", List.of(new TextAttachment("a.txt", "hello")));
        var withPicture = new UserInput(null, List.of(new PictureAttachment(PNG)));

        assertThatThrownBy(() -> new QueuedInput(withFile, AT, true))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueuedInput(withPicture, AT, true))
                .isExactlyInstanceOf(IllegalArgumentException.class);
        // an urgent entry without attachments stays valid
        var urgent = new QueuedInput(UserInput.of("stop!"), AT, true);
        assertThat(urgent.urgent()).isTrue();
        assertThat(urgent.input().text()).isEqualTo("stop!");
    }

    // queued-user-messages Regel 13
    @Test
    void normalEntryKeepsInputTimeAndLane() {
        var input = new UserInput("hi", List.of(new TextAttachment("a.txt", "hello"), new PictureAttachment(PNG)));

        var entry = new QueuedInput(input, AT, false);

        assertThat(entry.input()).isSameAs(input);
        assertThat(entry.queuedAt()).isEqualTo(AT);
        assertThat(entry.urgent()).isFalse();
        assertThatThrownBy(() -> new QueuedInput(null, AT, false)).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueuedInput(input, null, false)).isExactlyInstanceOf(NullPointerException.class);
    }
}

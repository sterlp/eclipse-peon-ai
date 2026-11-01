package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The one format of <b>[UC-WP-48](../../../../docs/user.md)</b>, measured in both directions and cleaned
 * before it is written: a name that could close the bracket or end the line would change who a message is
 * from, and a name of any length would let a line be forged by its own prefix. Pure — the turn and history
 * tests may assume exactly this format.
 */
class UserNameTest {

    @Test
    void aNameIsTrimmedAndStrippedOfWhatClosesTheFormat() {
        assertThat(UserName.clean("  Anna  ")).isEqualTo("Anna");
        // ] would close the prefix early: the reader would name a sender the writer never meant
        assertThat(UserName.clean("An]na]")).isEqualTo("Anna");
        // a control character could end the line and forge a second one
        assertThat(UserName.clean("An\nna\t")).isEqualTo("Anna");
    }

    @Test
    void aNameIsCappedAtThirtyTwoCharacters() {
        String tooLong = "x".repeat(60);

        assertThat(UserName.clean(tooLong)).hasSize(UserName.MAX_LENGTH);
        assertThat(UserName.payload(tooLong, "frage"))
                .isEqualTo("[User " + "x".repeat(UserName.MAX_LENGTH) + "]: frage");
    }

    @Test
    void nothingIsAPrefixWithoutAName() {
        assertThat(UserName.clean(null)).isNull();
        assertThat(UserName.clean("   ")).isNull();
        // a name left with nothing printable is no name — R-WP-17 does not invent one
        assertThat(UserName.clean("]]]")).isNull();

        assertThat(UserName.payload(null, "frage")).isEqualTo("frage");
        assertThat(UserName.payload("   ", "frage")).isEqualTo("frage");
    }

    @Test
    void thePayloadNamesTheSenderInTheFormatOfTheDoc() {
        assertThat(UserName.payload("Anna", "bitte X")).isEqualTo("[User Anna]: bitte X");
        assertThat(UserName.promptLine()).contains("[User <Name>]");
    }

    @Test
    void thePrefixIsParsedBackIntoSenderAndRequest() {
        UserName.Parsed parsed = UserName.parse("[User Anna]: bitte X");

        assertThat(parsed.name()).isEqualTo("Anna");
        assertThat(parsed.text()).isEqualTo("bitte X");
    }

    @Test
    void thePrefixIsParsedBehindTheQueueMarkerAndTheMarkerStays() {
        // the core puts its own marker in front of what it polled from the queue, so a waited input
        // carries the prefix after "…(10:15): " and not at the start
        UserName.Parsed parsed = UserName.parse("[Queued Message] (10:15): [User Anna]: weiter");

        assertThat(parsed.name()).isEqualTo("Anna");
        assertThat(parsed.text())
                .as("the marker is the core's own statement about the line and stays visible (Inc 3, Q6)")
                .isEqualTo("[Queued Message] (10:15): weiter");
    }

    @Test
    void aPrefixQuotedInsideTheTextIsNoSender() {
        String text = "in der doku steht: [User Paul]: hallo";

        UserName.Parsed parsed = UserName.parse(text);

        assertThat(parsed.name()).isNull();
        assertThat(parsed.text()).isEqualTo(text);
    }
}

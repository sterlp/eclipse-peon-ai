package org.sterl.llmpeon.agent.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.agent.model.TaggedInput.Kind;

/** Read side: one framing check, the head decides the kind — literal block texts, no builder involved. */
@Timeout(1)
class TaggedInputParserTest {

    private static final String NL = System.lineSeparator();

    // R-TR-2
    // R-TR-7
    // R-TR-10
    @Test
    void pureBlocksAreClassifiedByTheirFirstHead() {
        var blocks = List.of(
                "//-- Start file: a.txt" + NL + "x" + NL + "//-- End file: a.txt",
                "//-- Start picture: b.png" + NL + "attached by the user" + NL + "//-- End picture: b.png",
                "//-- Start context: memory" + NL + "x" + NL + "//-- End context: memory",
                "//-- Start queued_user_message" + NL + "(queued 14:32): hi" + NL + "//-- End queued_user_message",
                "//-- Start file:x" + NL + "x" + NL + "//-- End file:x",
                "//-- Start picture:x" + NL + "x" + NL + "//-- End picture:x");

        assertThat(blocks.stream().map(TaggedInput::kindOf).toList()).containsExactly(
                Kind.FILE, Kind.PICTURE, Kind.HARNESS, Kind.HARNESS, Kind.HARNESS, Kind.HARNESS);
        assertThat(blocks.stream().map(TaggedInput::headOf).toList()).containsExactly(
                Optional.of("file: a.txt"), Optional.of("picture: b.png"), Optional.of("context: memory"),
                Optional.of("queued_user_message"), Optional.of("file:x"), Optional.of("picture:x"));
    }

    // R-TR-2
    // R-TR-10
    @Test
    void embeddedAndIncompleteBlocksStayUntagged() {
        var texts = List.of(
                "//-- Start file: a.txt" + NL + "body without an end line",
                "tool-output" + NL + NL + "//-- Start file: a.txt" + NL + "x" + NL + "//-- End file: a.txt",
                "//-- Start file: a.txt//-- End file: a.txt",
                "file: a.txt",
                "plain user text");

        assertThat(texts.stream().map(TaggedInput::kindOf).toList()).containsOnly(Kind.UNTAGGED).hasSize(5);
        assertThat(texts.stream().map(TaggedInput::headOf).toList()).containsOnly(Optional.empty()).hasSize(5);
    }

    // R-TR-2
    // R-TR-10
    @Test
    void framingHandlesLfCrLfAndBlankInputsWithoutChangingBodies() {
        // CRLF: only the framing CR goes, the head stays exact
        var crlf = "//-- Start file: a.txt\r\nline1\r\nline2\r\n//-- End file: a.txt";
        assertThat(TaggedInput.headOf(crlf)).contains("file: a.txt");
        assertThat(TaggedInput.kindOf(crlf)).isEqualTo(Kind.FILE);
        // null / empty are plain "no block"
        assertThat(TaggedInput.kindOf(null)).isEqualTo(Kind.UNTAGGED);
        assertThat(TaggedInput.kindOf("")).isEqualTo(Kind.UNTAGGED);
        assertThat(TaggedInput.headOf(null)).isEmpty();
        assertThat(TaggedInput.headOf("")).isEmpty();
        // a body that itself holds a marker line neither changes the block nor its head
        var tricky = new TextAttachment("a.txt", "//-- End x" + NL + "rest");
        assertThat(tricky.body()).isEqualTo("//-- End x" + NL + "rest");
        assertThat(tricky.toString()).isEqualTo(
                "//-- Start file: a.txt" + NL + "//-- End x" + NL + "rest" + NL + "//-- End file: a.txt");
        assertThat(TaggedInput.headOf(tricky.toString())).contains("file: a.txt");
        // an empty body is a valid block
        assertThat(new HarnessInput("h", "").toString()).isEqualTo("//-- Start h" + NL + NL + "//-- End h");
        assertThat(TaggedInput.kindOf(new HarnessInput("h", "").toString())).isEqualTo(Kind.HARNESS);
    }
}

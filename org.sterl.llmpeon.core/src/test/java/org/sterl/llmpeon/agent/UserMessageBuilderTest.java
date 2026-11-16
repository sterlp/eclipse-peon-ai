package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.agent.model.ContextInput;
import org.sterl.llmpeon.agent.model.PictureAttachment;
import org.sterl.llmpeon.agent.model.TextAttachment;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.Picture;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

@Timeout(1)
class UserMessageBuilderTest {

    private static final String NL = System.lineSeparator();
    private static final Picture PNG = new Picture("b.png", "image/png", "AQID", 3);
    private final UserMessageBuilder subject = new UserMessageBuilder();

    private static String block(String head, String body) {
        return "//-- Start " + head + NL + body + NL + "//-- End " + head;
    }

    // R-TR-11
    @Test
    void buildsOneOrderedMessageWithoutEmptyContents() {
        var context = List.of(new ContextInput("context: x", "c"));
        var input = new UserInput("question", List.of(new TextAttachment("a.txt", "data"), new PictureAttachment(PNG)));

        // THEN — context first, then the text, then the attachments' contents, all in ONE message
        assertThat(subject.build(context, input)).hasValue(UserMessage.from(List.of(
                TextContent.from(block("context: x", "c")),
                TextContent.from("question"),
                TextContent.from(block("file: a.txt", "data")),
                TextContent.from(block("picture: b.png", "attached by the user")),
                ImageContent.from("AQID", "image/png", ImageContent.DetailLevel.HIGH))));
        // AND — a context without any text is still a message
        assertThat(subject.build(context, UserInput.of(null)))
                .hasValue(UserMessage.from(List.of(TextContent.from(block("context: x", "c")))));
        // AND — nothing at all must not become an invalid empty UserMessage
        assertThat(subject.build(List.of(), UserInput.of(null))).isEmpty();
        assertThat(subject.build(List.of(), UserInput.of("  " + NL))).isEmpty();
    }

    // R-TR-3
    // R-TR-11
    @Test
    void keyedContextIsCheckedBeforeRenderAndPayloadIsNotDeduped() {
        // GIVEN — memory already holds a keyed block and the content of an unkeyed one
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from(List.of(
                TextContent.from(block("context: k", "old")),
                TextContent.from(block("context", "same body")))));
        var calls = new ArrayList<String>();
        Supplier<List<ContextItem>> supplier = () -> List.of(
                item(calls, "keyed", "//-- Start context: k", new ContextInput("context: k", "new"), null),
                item(calls, "unkeyed-known", null, new ContextInput("context", "same body"), null),
                item(calls, "fresh", null, new ContextInput("context: f", "fresh body"), "f-label"),
                item(calls, "empty", null, null, "never-loaded"));
        var loading = new ArrayList<String>();
        AiMonitor monitor = m -> loading.add(m.message());

        // WHEN — the turn context is built, and the user's payload equals an already known body
        var context = subject.contextInputs(memory, supplier, monitor);
        var message = subject.build(context, UserInput.of("same body"));

        // THEN — the keyed item is never rendered, the other three are; only the fresh one is added and announced
        assertThat(calls).containsExactly("unkeyed-known", "fresh", "empty");
        assertThat(loading).containsExactly("📋 Loading f-label");
        assertThat(message).hasValue(UserMessage.from(List.of(
                TextContent.from(block("context: f", "fresh body")),
                TextContent.from("same body"))));
    }

    private static ContextItem item(List<String> calls, String name, String key, ContextInput rendered, String label) {
        return new ContextItem() {
            @Override public ContextInput render() { calls.add(name); return rendered; }
            @Override public String dedupKey() { return key; }
            @Override public String label() { return label; }
        };
    }
}

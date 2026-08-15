package org.sterl.llmpeon.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.shared.ChatMessageUtil;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Base for pure unit tests — no Eclipse dependencies.
 * Provides assertion helpers and MockLlmServer lifecycle.
 */
public abstract class AbstractUnitTest {

    protected final MockLlmServer mockLlmServer = new MockLlmServer();

    @After
    public void after() {
        mockLlmServer.stop();
    }

    @Before
    public void before() {
        mockLlmServer.start();
    }

    // === Statische Helper ===

    public static void assertContains(String value, String expected) {
        assertNotNull("Expected to find " + expected, value);
        assertTrue("Expected:\n" + value + "\nto contain:\n" + expected, value.contains(expected));
    }

    public static void assertHasNoUserMessageWith(Collection<ChatMessage> messages, String content) {
        var textMessages = messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).singleText())
                .toList();
        assertHasNoMessageWith(textMessages, content);
    }

    public static void assertHasNoMessageWith(Collection<String> textMessages, String content) {
        var match = textMessages.stream().filter(m -> m.contains(content)).findAny();
        assertTrue("Found match: \n" + content + "\nin:\n" + match.orElse(null), match.isEmpty());
    }

    public static void assertIsEmpty(Optional<?> v) {
        assertTrue("Expected optional to be empty", v.isEmpty());
    }

    public static void assertIsPresent(Optional<?> v) {
        assertTrue("Expected optional to have a value", v.isPresent());
    }

    public static void assertHasUserMessageWith(Collection<? extends ChatMessage> messages, String content) {
        var textMessages = messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ChatMessageUtil.toString(m))
                .toList();
        assertHasMessageWith(textMessages, content);
    }

    public static void assertHasMessageWith(Collection<String> textMessages, String content) {
        var match = textMessages.stream().filter(m -> m.contains(content)).findAny();
        assertTrue("Could not find: \n" + content + "\nin:\n"
                + textMessages.stream().collect(Collectors.joining("\n")), match.isPresent());
    }
}

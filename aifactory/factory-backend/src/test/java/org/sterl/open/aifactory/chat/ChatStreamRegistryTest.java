package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The registry is the only class that owns the wire, so its cleanup rule is proven here — over real
 * {@link SseEmitter}s, not a stand-in: R-WP-5's "reload verliert nur Live-Zeilen" only holds if a gone
 * client stops being addressed and the turn survives it.
 */
class ChatStreamRegistryTest {

    @Test
    void deadSubscriberStopsBeingAddressed() {
        ChatStreamRegistry registry = new ChatStreamRegistry();
        // Given one live and one already completed subscriber, plus a subscriber of another project
        SseEmitter alive = registry.subscribe("p1");
        SseEmitter dead = registry.subscribe("p1");
        SseEmitter otherProject = registry.subscribe("p2");
        dead.complete();
        assertThat(registry.listenerCount("p1")).isEqualTo(2);

        // When the dead one is addressed — the client of a reloaded browser tab
        // Then nothing escapes to the turn and only the dead subscriber is gone
        assertThatCode(() -> registry.publish("p1", new ChatEvent.Partial("answer", "hi")))
                .doesNotThrowAnyException();
        assertThat(registry.listenerCount("p1")).isOne();
        assertThat(registry.listenerCount("p2")).isOne();
        assertThatCode(() -> registry.publish("never-subscribed", new ChatEvent.Done(5L)))
                .doesNotThrowAnyException();

        // And the survivor is still addressable
        assertThatCode(() -> registry.publish("p1", new ChatEvent.Done(7L))).doesNotThrowAnyException();
        assertThat(registry.listenerCount("p1")).isOne();
        assertThat(alive).isNotNull();
        assertThat(otherProject).isNotNull();
    }

    @Test
    void subscriberIsNeverTimedOut() {
        ChatStreamRegistry registry = new ChatStreamRegistry();

        // Tomcat's async default of 30 s would otherwise kill the connection inside a long turn
        assertThat(registry.subscribe("p1").getTimeout()).isZero();
    }
}

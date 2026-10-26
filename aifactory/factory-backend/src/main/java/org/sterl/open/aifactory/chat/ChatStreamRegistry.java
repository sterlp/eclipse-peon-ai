package org.sterl.open.aifactory.chat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The SSE wire of the chat: subscribers per project, one <b>bounded outbox per subscriber</b>, and the
 * quiet removal of a dead listener. It holds <b>no messages</b> — a reload loses only live lines because
 * there is nothing here to lose (R-WP-11, architecture doc "ChatStreamRegistry hält keine Nachrichten").
 *
 * <p>No replay and no event id: the history endpoint is the single truth, so a second subscriber cannot
 * be shown the same answer twice.</p>
 *
 * <p>Writing an emitter is blocking I/O, so it never happens on the publishing thread: a turn hands its
 * event to the outbox and is gone. A reader that stops reading therefore costs his own lines and nothing
 * else — the turn finishes and every other subscriber sees all of it (UC-WP-41, R-WP-5). The outbox is
 * bounded, because "loses only live lines" needs a limit to stay true when nobody drains it.</p>
 */
@Component
public class ChatStreamRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ChatStreamRegistry.class);

    /**
     * How many live lines may queue up for one subscriber before the next is dropped. Generous for a
     * reading browser, small enough that a stalled socket cannot grow without bound.
     */
    private static final int OUTBOX_LIMIT = 256;

    /** Ends a delivery loop. A poison pill and not a {@code ChatEvent}, because the event type is sealed. */
    private static final Object POISON = new Object();

    private final Map<String, List<Subscription>> subscribers = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();

    /** One virtual thread per subscriber — a delivery waits on a socket, and that is exactly its own business. */
    private final ExecutorService deliveries = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * @param timeout {@code 0} — never time out. Tomcat's 30 s async timeout would otherwise kill the
     *                connection in the middle of a long turn. Shutdown closes it instead, see
     *                {@link #closeAll()}: an emitter left open holds the JVM past {@code System.exit(0)}.
     */
    public SseEmitter subscribe(String projectId) {
        return subscribe(projectId, new SseEmitter(0L));
    }

    /** The seam of {@link ChatStreamRegistryTest}: a subscriber whose write blocks or fails on purpose. */
    SseEmitter subscribe(String projectId, SseEmitter emitter) {
        Subscription subscription = new Subscription(emitter);
        subscribers.computeIfAbsent(projectId, key -> new CopyOnWriteArrayList<>()).add(subscription);
        emitter.onCompletion(() -> remove(projectId, subscription));
        emitter.onTimeout(() -> remove(projectId, subscription));
        emitter.onError(failure -> remove(projectId, subscription));
        deliveries.submit(() -> deliver(projectId, subscription));
        return emitter;
    }

    /**
     * Hands the event to every subscriber of one project and returns. Never serialized, never blocking:
     * ordering is kept per connection by that connection's own delivery thread, so two turns of two
     * projects cannot interleave on one wire and a slow reader cannot hold the turn (UC-WP-41).
     */
    public void publish(String projectId, ChatEvent event) {
        for (Subscription subscription : subscribers.getOrDefault(projectId, List.of())) {
            if (!subscription.offer(event)) {
                // a live line may be lost — the history stays the truth; the turn and the others are unaffected
                LOG.debug("chat listener of project {} is not reading — live line dropped", projectId);
            }
        }
    }

    public int listenerCount(String projectId) {
        return subscribers.getOrDefault(projectId, List.of()).size();
    }

    /** The seam of the cleanup rule: a project nobody listens to any more is not in the map at all. */
    int projectCount() {
        return subscribers.size();
    }

    /** A removed project's listeners go with it: nothing may keep publishing to a project that is gone. */
    void closeProject(String projectId) {
        List<Subscription> closed = subscribers.remove(projectId);
        if (closed == null) {
            return;
        }
        for (Subscription subscription : closed) {
            subscription.close();
            complete(subscription.emitter);
        }
    }

    /**
     * An emitter with no timeout never dies on its own, so closing the context closes them. On the
     * {@link ContextClosedEvent} and not on {@code @PreDestroy}: Boot stops the web server between the
     * event and the bean destruction, and its graceful shutdown waits for a request that is still in
     * flight — an open event stream is exactly that, and the wait costs the whole
     * {@code spring.lifecycle.timeout-per-shutdown-phase} (30 s) at every exit.
     */
    @EventListener(ContextClosedEvent.class)
    void closeAll() {
        for (String projectId : List.copyOf(subscribers.keySet())) {
            closeProject(projectId);
        }
        deliveries.shutdownNow();
    }

    private void deliver(String projectId, Subscription subscription) {
        try {
            for (;;) {
                Object item = subscription.outbox.take();
                if (item == POISON) {
                    return;
                }
                subscription.emitter.send(toSse((ChatEvent) item));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException gone) {
            // a subscriber that cannot be written is a gone client, not an error: the turn keeps running
            LOG.debug("chat listener gone for project {}", projectId);
        } finally {
            remove(projectId, subscription);
        }
    }

    private void remove(String projectId, Subscription subscription) {
        subscription.close();
        List<Subscription> projectSubscribers = subscribers.get(projectId);
        if (projectSubscribers == null) {
            return;
        }
        projectSubscribers.remove(subscription);
        // an empty project leaves the map, so a closed chat leaves no trace behind
        subscribers.computeIfPresent(projectId, (key, left) -> left.isEmpty() ? null : left);
    }

    private static void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException alreadyGone) {
            LOG.debug("chat listener already closed");
        }
    }

    /**
     * Event name + JSON data, both from the architecture doc's table. The payload is built by hand so
     * the field set of a live line is visible here and cannot grow by accident — a key would reach the
     * browser through this map and nothing else (R-WP-16).
     *
     * <p>Exhaustive switch over the sealed {@link ChatEvent}, deliberately without {@code default}: a
     * fifth event type has to fail the build here. An {@code if}-chain ending in a thrown
     * IllegalStateException compiled either way, and at runtime that exception ended the delivery thread
     * of every watching connection mid-turn — from then on not even {@code done} got through (measured by
     * 3f-3's mutation battery, and the reason the chain is gone).</p>
     */
    private SseEmitter.SseEventBuilder toSse(ChatEvent event) throws IOException {
        return switch (event) {
            case ChatEvent.Partial partial -> SseEmitter.event().name("partial")
                    .data(payload("kind", partial.kind(), "text", partial.text()), MediaType.APPLICATION_JSON);
            case ChatEvent.ToolLine toolLine -> SseEmitter.event().name("tool")
                    .data(payload("text", toolLine.text()), MediaType.APPLICATION_JSON);
            case ChatEvent.Delegated delegated -> SseEmitter.event().name("delegated")
                    .data(payload("origin", delegated.origin(), "text", delegated.text()),
                            MediaType.APPLICATION_JSON);
            case ChatEvent.Done done -> SseEmitter.event().name("done")
                    .data(payload("millis", done.millis()), MediaType.APPLICATION_JSON);
            case ChatEvent.Failed failed -> SseEmitter.event().name("error")
                    .data(payload("message", failed.message()), MediaType.APPLICATION_JSON);
        };
    }

    private String payload(Object... nameAndValue) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < nameAndValue.length; i += 2) {
            fields.put((String) nameAndValue[i], nameAndValue[i + 1]);
        }
        return json.writeValueAsString(fields);
    }

    /** One subscriber: his emitter and his own bounded outbox — the border that keeps a slow reader to himself. */
    private static final class Subscription {

        private final SseEmitter emitter;
        private final BlockingQueue<Object> outbox = new ArrayBlockingQueue<>(OUTBOX_LIMIT);
        private volatile boolean closed;

        Subscription(SseEmitter emitter) {
            this.emitter = emitter;
        }

        boolean offer(ChatEvent event) {
            return !closed && outbox.offer(event);
        }

        void close() {
            closed = true;
            outbox.clear(); // what a removed listener never read is not delivered after he is gone
            outbox.offer(POISON);
        }
    }
}

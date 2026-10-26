package org.sterl.open.aifactory.chat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The SSE wire of the chat: subscribers per project, broadcast, and the quiet removal of a dead
 * listener. It holds <b>no messages</b> — a reload loses only live lines because there is nothing here
 * to lose (R-WP-11, architecture doc "ChatStreamRegistry hält keine Nachrichten").
 *
 * <p>No replay and no event id: the history endpoint is the single truth, so a second subscriber
 * cannot be shown the same answer twice.</p>
 */
@Component
public class ChatStreamRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ChatStreamRegistry.class);

    private final Map<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param timeout {@code 0} — never time out. Tomcat's 30 s async timeout would otherwise kill the
     *                connection in the middle of a long turn.
     */
    public SseEmitter subscribe(String projectId) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> projectSubscribers = subscribers.computeIfAbsent(projectId,
                key -> new CopyOnWriteArrayList<>());
        projectSubscribers.add(emitter);
        emitter.onCompletion(() -> remove(projectId, emitter));
        emitter.onTimeout(() -> remove(projectId, emitter));
        emitter.onError(failure -> remove(projectId, emitter));
        return emitter;
    }

    /**
     * Broadcasts to one project, serialized per project so two turns of two projects cannot interleave
     * their events on one connection. A subscriber that cannot be written is a gone client, not an
     * error: it is unsubscribed and the turn keeps running (architecture doc "ohne Zuhörer läuft
     * nichts ins Leere").
     */
    public void publish(String projectId, ChatEvent event) {
        List<SseEmitter> projectSubscribers = subscribers.get(projectId);
        if (projectSubscribers == null || projectSubscribers.isEmpty()) {
            return;
        }
        synchronized (locks.computeIfAbsent(projectId, key -> new Object())) {
            for (SseEmitter emitter : projectSubscribers) {
                try {
                    emitter.send(toSse(event));
                } catch (IOException | RuntimeException e) {
                    LOG.debug("chat listener gone for project {}", projectId);
                    remove(projectId, emitter);
                }
            }
        }
    }

    public int listenerCount(String projectId) {
        return subscribers.getOrDefault(projectId, List.of()).size();
    }

    private void remove(String projectId, SseEmitter emitter) {
        List<SseEmitter> projectSubscribers = subscribers.get(projectId);
        if (projectSubscribers != null) {
            projectSubscribers.remove(emitter);
        }
    }

    /**
     * Event name + JSON data, both from the architecture doc's table. The payload is built by hand so
     * the field set of a live line is visible here and cannot grow by accident — a key would reach the
     * browser through this map and nothing else (R-WP-16).
     */
    private SseEmitter.SseEventBuilder toSse(ChatEvent event) throws IOException {
        if (event instanceof ChatEvent.Partial partial) {
            return SseEmitter.event().name("partial").data(payload("kind", partial.kind(), "text", partial.text()),
                    MediaType.APPLICATION_JSON);
        }
        if (event instanceof ChatEvent.ToolLine toolLine) {
            return SseEmitter.event().name("tool").data(payload("text", toolLine.text()), MediaType.APPLICATION_JSON);
        }
        if (event instanceof ChatEvent.Done done) {
            return SseEmitter.event().name("done").data(payload("millis", done.millis()), MediaType.APPLICATION_JSON);
        }
        if (event instanceof ChatEvent.Failed failed) {
            return SseEmitter.event().name("error").data(payload("message", failed.message()),
                    MediaType.APPLICATION_JSON);
        }
        throw new IllegalStateException("unmapped chat event " + event.getClass());
    }

    private String payload(Object... nameAndValue) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < nameAndValue.length; i += 2) {
            fields.put((String) nameAndValue[i], nameAndValue[i + 1]);
        }
        return json.writeValueAsString(fields);
    }
}

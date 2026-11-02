package org.sterl.open.aifactory.project;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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

import org.sterl.open.aifactory.project.dto.ProjectWorkDto;

/**
 * The SSE wire of the work states (R-WP-24): one connection per viewer of the project list, the full
 * snapshot on it first, every change after that as a delta. It holds <b>no states</b> — a viewer that
 * connects later is answered from {@link ProjectWorkStates} at that moment, so a reconnect
 * re-synchronizes by itself and nothing here can go stale or outlive the runtime it speaks of.
 *
 * <p>The delta outbox of a subscriber holds <b>one</b> entry and the newest wins, unlike the chat's
 * queue: a work state is a snapshot, so a change a stalled reader has not read is worth replacing and not
 * worth remembering. Writing an emitter is blocking I/O, so it still never happens on the publishing
 * thread — a turn hands its state over and is gone.</p>
 */
@Component
public class ProjectStatusStream {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectStatusStream.class);

    /** The snapshot of every project, sent once per connection before any delta. */
    static final String SNAPSHOT_EVENT = "projects";

    /** The state of one project. */
    static final String DELTA_EVENT = "project";

    /** Ends a delivery loop. A poison pill and not a {@code ProjectWork}, because the queue holds one type. */
    private static final Object POISON = new Object();

    private final List<Subscription> subscribers = new CopyOnWriteArrayList<>();
    private final ObjectMapper json = new ObjectMapper();

    /** One virtual thread per subscriber — a delivery waits on a socket, and that is exactly its own business. */
    private final ExecutorService deliveries = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * @param snapshot the states as they are <b>now</b> — read before this call, so a change that lands
     *                 while it is read is delivered after the snapshot and never overwritten by it
     */
    public SseEmitter subscribe(List<ProjectWork> snapshot) {
        // the snapshot is a field and not a queue entry: a delta may overtake a queued delta, never the
        // picture a new viewer is being given
        Subscription subscription = new Subscription(new SseEmitter(0L), write(SNAPSHOT_EVENT,
                Map.of("projects", snapshot.stream().map(ProjectWorkDto::of).toList())));
        subscribers.add(subscription);
        subscription.onGone(() -> subscribers.remove(subscription));
        deliveries.submit(() -> deliver(subscription));
        return subscription.emitter;
    }

    /** Hands the state to every viewer and returns — a viewer that is not reading loses this one change. */
    public void publish(ProjectWork work) {
        for (Subscription subscription : subscribers) {
            if (!subscription.offer(write(DELTA_EVENT, ProjectWorkDto.of(work)))) {
                LOG.debug("work-state listener is not reading — change of {} dropped", work.projectId());
            }
        }
    }

    int listenerCount() {
        return subscribers.size();
    }

    /**
     * An emitter with no timeout never dies on its own, so closing the context closes them — on the same
     * event as {@code ChatStreamRegistry} and for the same reason: an open event stream is a request in
     * flight, and the graceful shutdown would wait the whole phase timeout for it.
     */
    @EventListener(ContextClosedEvent.class)
    void closeAll() {
        for (Subscription subscription : List.copyOf(subscribers)) {
            subscribers.remove(subscription);
            subscription.close();
            complete(subscription.emitter);
        }
        deliveries.shutdownNow();
    }

    /** Event name + JSON data, built by hand so the field set of a work state is visible in one place. */
    private String write(String event, Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (IOException impossible) {
            // a record of a String, a boolean and an int cannot fail
            throw new IllegalStateException("work state " + event + " is not writable", impossible);
        }
    }

    private void deliver(Subscription subscription) {
        try {
            subscription.emitter.send(SseEmitter.event().name(SNAPSHOT_EVENT)
                    .data(subscription.snapshot, MediaType.APPLICATION_JSON));
            for (;;) {
                Object item = subscription.outbox.take();
                if (item == POISON) {
                    return;
                }
                subscription.emitter.send(SseEmitter.event().name(DELTA_EVENT)
                        .data((String) item, MediaType.APPLICATION_JSON));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException gone) {
            // a viewer that cannot be written is gone, not an error: the turn and the others are unaffected
            LOG.debug("work-state listener gone");
        } finally {
            subscription.close();
            subscribers.remove(subscription);
        }
    }

    private static void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException alreadyGone) {
            LOG.debug("work-state listener already closed");
        }
    }

    /** One viewer: his emitter, the snapshot he is being shown, and his own one-place outbox. */
    private static final class Subscription {

        private final SseEmitter emitter;
        private final String snapshot;
        private final BlockingQueue<Object> outbox = new ArrayBlockingQueue<>(1);
        private volatile boolean closed;

        Subscription(SseEmitter emitter, String snapshot) {
            this.emitter = emitter;
            this.snapshot = snapshot;
        }

        void onGone(Runnable remove) {
            emitter.onCompletion(remove);
            emitter.onTimeout(remove);
            emitter.onError(failure -> remove.run());
        }

        boolean offer(String data) {
            if (closed) {
                return false;
            }
            outbox.poll(); // newest wins: what is not read yet is worth replacing, not remembering
            return outbox.offer(data);
        }

        void close() {
            closed = true;
            outbox.clear();
            outbox.offer(POISON);
        }
    }
}

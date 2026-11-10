package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

/**
 * One live connection of the chat wire, read in arrival order over real HTTP: the JDK client, because the
 * SSE edge is what the browser talks and a stubbed call would hand out a stream nobody can follow.
 *
 * <p>Every wait is a deadline on an end state, never a fixed pause. Order is kept per connection, so a
 * marker that arrived orders everything published before it — an empty wire next to it is then proven and
 * not merely observed early.</p>
 */
final class LiveStreamProbe implements Flow.Subscriber<String>, AutoCloseable {

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final List<String> names = new CopyOnWriteArrayList<>();
    private final List<String> payloads = new CopyOnWriteArrayList<>();
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final CompletableFuture<?> exchange;
    private volatile String event = "message";
    private volatile String failure;

    /**
     * @param url the absolute stream edge, {@code …/events} of the conversation or of one tile — the probe
     *            knows no route, so a test cannot prove a channel it never opened
     */
    LiveStreamProbe(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        exchange = client.sendAsync(request, HttpResponse.BodyHandlers.ofPublisher())
                .thenAccept(body -> body.body().subscribe(HttpResponse.BodySubscribers.fromLineSubscriber(this)));
        exchange.exceptionally(broken -> {
            failure = String.valueOf(broken);
            return null;
        });
    }

    @Override
    public void onSubscribe(Flow.Subscription sub) {
        subscription.set(sub);
        sub.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(String line) {
        if (line.startsWith("event:")) {
            event = line.substring("event:".length()).trim();
        } else if (line.startsWith("data:")) {
            names.add(event);
            payloads.add(line.substring("data:".length()).trim());
        }
    }

    @Override
    public void onError(Throwable broken) {
        failure = String.valueOf(broken);
    }

    @Override
    public void onComplete() {
        // the server closed the stream; nothing is buffered here, so nothing is lost either
    }

    void awaitEvent(String name, int count, String what) {
        awaitUntil(what + " — connection: " + failure, () -> countOf(name) >= count);
    }

    void awaitUntil(String what, BooleanSupplier condition) {
        long until = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis())
                    .as("%s traf auf dem Strom vor der Deadline von 30 s ein", what)
                    .isLessThan(until);
            sleep();
        }
    }

    int countOf(String name) {
        return (int) names.stream().filter(name::equals).count();
    }

    /** Every {@code event:} name seen, in arrival order — duplicates kept. */
    List<String> eventNames() {
        return List.copyOf(names);
    }

    /** The JSON bodies of one event name, in arrival order. */
    List<String> payloads(String name) {
        List<String> found = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (name.equals(names.get(i))) {
                found.add(payloads.get(i));
            }
        }
        return found;
    }

    /** Every data line of every event, concatenated — the haystack a line may or may not be in. */
    String wireText() {
        return String.join("|", payloads);
    }

    /** The {@code answer} chunks joined in order — a streamed text only ever arrives in pieces. */
    String answerText() {
        StringBuilder text = new StringBuilder();
        for (String payload : payloads("partial")) {
            if ("answer".equals(JsonPath.read(payload, "$.kind"))) {
                text.append((String) JsonPath.read(payload, "$.text"));
            }
        }
        return text.toString();
    }

    /** The {@code text} of every tool line, in arrival order. */
    List<String> toolTexts() {
        List<String> texts = new ArrayList<>();
        for (String payload : payloads("tool")) {
            texts.add((String) JsonPath.read(payload, "$.text"));
        }
        return texts;
    }

    /** The {@code origin} of every tool line, in arrival order ({@code "null"} for an unstamped line). */
    List<String> toolOrigins() {
        List<String> origins = new ArrayList<>();
        for (String payload : payloads("tool")) {
            Object origin = JsonPath.read(payload, "$.origin");
            origins.add(String.valueOf(origin));
        }
        return origins;
    }

    @Override
    public void close() {
        Flow.Subscription sub = subscription.getAndSet(null);
        if (sub != null) {
            sub.cancel();
        }
        exchange.cancel(true);
        // close(), not shutdown(): only close() releases the client's selector thread
        client.close();
    }

    private static void sleep() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the live stream", interrupted);
        }
    }
}

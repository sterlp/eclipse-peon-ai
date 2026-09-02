package org.sterl.llmpeon.ai;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.sterl.llmpeon.ai.model.AiModel;

public class SharedHttpClient {

    private static final AtomicReference<HttpClient> ref = new AtomicReference<>();
    public static final Duration MODEL_TIMEOUT = Duration.ofSeconds(30);
    
    public static HttpClient getHttpClient() {
        if (ref.get() == null) {
            synchronized (ref) {
                if (ref.get() == null) ref.set(HttpClient.newBuilder() //
                        .version(HttpClient.Version.HTTP_1_1) // LM Studio requires 1.1
                        .build());
            }
        }
        return ref.get();
    }
    
    /**
     * Sends the model list request asynchronously and blocks until it completes (bounded by
     * {@code MODEL_TIMEOUT + 10s}). Concurrent requests are independent — no cross-cancellation;
     * duplicate needs for one connection are deduplicated upstream in {@link ModelListCache}
     * (single-flight).
     */
    public static List<AiModel> getModels(HttpRequest.Builder request, 
            Function<String, List<AiModel>> handler) {
        var built = request.timeout(MODEL_TIMEOUT).GET().build();
        var future = getHttpClient().sendAsync(built, HttpResponse.BodyHandlers.ofString());
        try {
            return handler.apply(future.get(MODEL_TIMEOUT.plusSeconds(10).toMillis(), TimeUnit.MILLISECONDS).body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException e) {
            throw new ModelLoadFailedException("Failed to load models from " + built.uri(), 
                    e.getCause() == null ? e : e.getCause());
        } catch (TimeoutException e) {
            throw new ModelLoadFailedException("Timeout loading models from " + built.uri() + " after " + MODEL_TIMEOUT, 
                    e.getCause() == null ? e : e.getCause());
        }
    }
}

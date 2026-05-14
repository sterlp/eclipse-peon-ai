package org.sterl.llmpeon.ai;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    
    private static final AtomicReference<CompletableFuture<HttpResponse<String>>> pendingRequest = new AtomicReference<>();
    
    public static List<AiModel> cancelAndGet(HttpRequest.Builder request, 
            Function<String, List<AiModel>> handler) {
        return cancelAndSend(request.timeout(MODEL_TIMEOUT).GET().build(), handler);
    }
    /**
     * Sends the request asynchronously, cancelling any previously pending list request.
     * Returns null if the request was itself cancelled before completion.
     */
    public static List<AiModel> cancelAndSend(HttpRequest request, 
            Function<String, List<AiModel>> handler) {
        var future = getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var prev = pendingRequest.getAndSet(future);
        if (prev != null) prev.cancel(true);

        try {
            return handler.apply(future.get(MODEL_TIMEOUT.plusSeconds(10).toMillis(), TimeUnit.MILLISECONDS).body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (java.util.concurrent.CancellationException e) {
            return List.of();
        } catch (ExecutionException e) {
            throw new ModelLoadFailedException("Failed to load models from " + request.uri(), 
                    e.getCause() == null ? e : e.getCause());
        } catch (TimeoutException e) {
            throw new ModelLoadFailedException("Timeout loading models from " + request.uri() + " after " + MODEL_TIMEOUT, 
                    e.getCause() == null ? e : e.getCause());
        } finally {
            pendingRequest.compareAndSet(future, null);
        }
    }
}

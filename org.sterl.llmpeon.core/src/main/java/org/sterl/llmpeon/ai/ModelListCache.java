package org.sterl.llmpeon.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.ai.model.AiModel;

import lombok.extern.slf4j.Slf4j;

/**
 * Process-wide cache of the listed AI models per {@link ConnectionIdentity} (ADR-0034): the list
 * is fetched once per identity and cached <b>on success only</b> — a failed or empty fetch is not
 * cached, so the next need retries. Keyed by the full identity (provider/url/key/build-time body),
 * a config change automatically points at a different entry — no invalidation needed.
 *
 * <p>Thread-safe; the fetch itself runs in the caller's background job.</p>
 */
@Slf4j
public final class ModelListCache {

    private static final ModelListCache INSTANCE = new ModelListCache();

    private final Map<ConnectionIdentity, List<AiModel>> byIdentity = new ConcurrentHashMap<>();

    public static ModelListCache instance() {
        return INSTANCE;
    }

    /** The cached list for the identity, or {@code null} if not (yet) cached. */
    @Nullable
    public List<AiModel> cached(ConnectionIdentity identity) {
        return byIdentity.get(identity);
    }

    /**
     * The cached list, or a fetch that is cached on success. A failed or empty fetch returns
     * {@code null} without caching — the UI falls back to the configured model.
     */
    @Nullable
    public List<AiModel> getOrFetch(ConnectionIdentity identity, Supplier<List<AiModel>> fetcher) {
        var hit = byIdentity.get(identity);
        if (hit != null) return hit;
        var fetched = fetch(identity, fetcher);
        if (fetched == null) return null;
        byIdentity.put(identity, fetched);
        return fetched;
    }

    /**
     * Always fetches. A successful fetch replaces the entry; a failed one keeps the old entry.
     * Returns the fetched list, or {@code null} on failure.
     */
    @Nullable
    public List<AiModel> refresh(ConnectionIdentity identity, Supplier<List<AiModel>> fetcher) {
        var fetched = fetch(identity, fetcher);
        if (fetched == null) return null;
        byIdentity.put(identity, fetched);
        return fetched;
    }

    /** Drops all entries (tests). */
    public void clear() {
        byIdentity.clear();
    }

    /** Runs the fetcher; a failure or an empty list is an error → {@code null} (not cached). */
    @Nullable
    private static List<AiModel> fetch(ConnectionIdentity identity, Supplier<List<AiModel>> fetcher) {
        try {
            var models = fetcher.get();
            if (models == null || models.isEmpty()) {
                log.warn("Model list fetch for {} returned no models", identity);
                return null;
            }
            return List.copyOf(models);
        } catch (RuntimeException e) {
            log.warn("Model list fetch for {} failed", identity, e);
            return null;
        }
    }
}

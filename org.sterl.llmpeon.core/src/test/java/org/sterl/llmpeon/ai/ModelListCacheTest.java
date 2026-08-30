package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.model.AiModel;

class ModelListCacheTest {

    private final ModelListCache cache = ModelListCache.instance();

    private final ConnectionIdentity idA = new ConnectionIdentity(AiProvider.OPEN_AI, "http://a/v1", "key-a", null);
    private final ConnectionIdentity idB = new ConnectionIdentity(AiProvider.OPEN_AI, "http://b/v1", "key-b", null);
    private final ConnectionIdentity idBody = new ConnectionIdentity(AiProvider.ANTHROPIC, "http://a/v1", "key-a", "{\"x\":1}");

    @BeforeEach
    void clearCache() {
        cache.clear();
    }

    private static List<AiModel> models(String... ids) {
        return Arrays.stream(ids).map(id -> AiModel.builder().id(id).build()).toList();
    }

    @Test
    void sameIdentityUsesCacheNoRefetch() {
        // GIVEN a fetcher that counts its invocations
        var fetches = new AtomicInteger();
        Supplier<List<AiModel>> fetcher = () -> {
            fetches.incrementAndGet();
            return models("m1", "m2");
        };

        // WHEN the same identity is fetched twice
        var first = cache.getOrFetch(idA, fetcher);
        var second = cache.getOrFetch(idA, fetcher);

        // THEN the list is returned both times and the fetcher ran once
        assertThat(first).hasSize(2);
        assertThat(second).isSameAs(first);
        assertThat(fetches).hasValue(1);
    }

    @Test
    void fetchFailureIsNotCached() {
        // GIVEN a fetcher that fails once (exception) and once with an empty list, then succeeds
        var state = new AtomicInteger(0);
        Supplier<List<AiModel>> fetcher = () -> {
            return switch (state.getAndIncrement()) {
                case 0 -> throw new IllegalStateException("boom");
                case 1 -> List.of();
                default -> models("m1");
            };
        };

        // WHEN the fetch fails (exception, then empty list) and succeeds afterwards
        assertThat(cache.getOrFetch(idA, fetcher)).isNull();
        assertThat(cache.getOrFetch(idA, fetcher)).isNull();
        var success = cache.getOrFetch(idA, fetcher);

        // THEN the failures were not cached (each attempt re-fetched) and the success is cached
        assertThat(success).hasSize(1);
        assertThat(cache.cached(idA)).isSameAs(success);
        assertThat(state).hasValue(3);
    }

    @Test
    void refreshSuccessReplacesCache() {
        // GIVEN a cached entry
        cache.getOrFetch(idA, () -> models("m1"));

        // WHEN a refresh succeeds with a different list
        var refreshed = cache.refresh(idA, () -> models("m2", "m3"));

        // THEN the refresh result is returned and the cache holds it
        assertThat(refreshed).hasSize(2);
        assertThat(cache.cached(idA)).isSameAs(refreshed);
    }

    @Test
    void refreshFailureKeepsOldCache() {
        // GIVEN a cached entry (the cache stores its own copy)
        cache.getOrFetch(idA, () -> models("m1"));
        var old = cache.cached(idA);

        // WHEN a refresh fails
        var refreshed = cache.refresh(idA, () -> {
            throw new IllegalStateException("boom");
        });

        // THEN the refresh returns null and the old entry stays
        assertThat(refreshed).isNull();
        assertThat(cache.cached(idA)).isSameAs(old);
    }

    @Test
    void differentIdentitiesAreIndependent() {
        // GIVEN two different identities (url/key) and one differing only in the build-time body
        var listA = cache.getOrFetch(idA, () -> models("a1"));
        var listB = cache.getOrFetch(idB, () -> models("b1"));
        var listBody = cache.getOrFetch(idBody, () -> models("c1"));

        // THEN each identity has its own entry
        assertThat(listA).hasSize(1);
        assertThat(listB).hasSize(1);
        assertThat(listBody).hasSize(1);
        assertThat(cache.cached(idA)).isSameAs(listA);
        assertThat(cache.cached(idB)).isSameAs(listB);
        assertThat(cache.cached(idBody)).isSameAs(listBody);
    }

}

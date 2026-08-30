package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConnectionIdentity;
import org.sterl.llmpeon.ai.EffectiveConnection;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.provider.LlmProviders;

/**
 * Model list fetch + cache against the MockLlmServer (OpenAI provider, {@code /v1/models}).
 * Fixture is built before the SUT and the shared {@link ModelListCache} is cleared afterwards —
 * no reliance on state from earlier runs.
 */
public class ModelListFetchTest extends AbstractUnitTest {

    private ModelListCache cache;
    private LlmConfig base;
    private EffectiveConnection effective;

    @Before
    public void fixture() {
        cache = ModelListCache.instance();
        cache.clear();
        mockLlmServer.setModelIds(List.of("gpt-4o", "mock-model"));
        base = mockLlmServer.newConfig("gpt-4o");
        effective = base.effectiveConnectionFor(AgentModelConfig.empty());
    }

    @After
    public void tearDown() {
        cache.clear();
    }

    private List<AiModel> fetch(EffectiveConnection eff) {
        return LlmProviders.of(eff.identity().provider()).listAiModels(eff.buildConfig());
    }

    private static List<String> ids(List<AiModel> models) {
        return models.stream().map(AiModel::getId).toList();
    }

    @Test
    public void fetchReturnsServerModels() {
        // WHEN the list is fetched for the base identity
        var list = cache.getOrFetch(effective.identity(), () -> fetch(effective));

        // THEN the server's model ids come back
        assertNotNull(list);
        assertEquals(List.of("gpt-4o", "mock-model"), ids(list));
    }

    @Test
    public void secondGetOrFetchUsesCache() {
        // GIVEN a fetcher that counts its invocations
        var fetches = new AtomicInteger();
        Supplier<List<AiModel>> supplier = () -> {
            fetches.incrementAndGet();
            return fetch(effective);
        };

        // WHEN the same identity is fetched twice
        cache.getOrFetch(effective.identity(), supplier);
        cache.getOrFetch(effective.identity(), supplier);

        // THEN the fetch ran once (second call served from cache)
        assertEquals(1, fetches.get());
    }

    @Test
    public void failedFetchIsNotCached() {
        // GIVEN the models endpoint returns an error
        mockLlmServer.enableModelsError();

        // WHEN the fetch fails
        assertNull(cache.getOrFetch(effective.identity(), () -> fetch(effective)));

        // THEN after the endpoint recovers, the next fetch succeeds (failure was not cached)
        mockLlmServer.reset();
        var list = cache.getOrFetch(effective.identity(), () -> fetch(effective));
        assertNotNull(list);
        assertEquals(List.of("gpt-4o", "mock-model"), ids(list));
    }

    @Test
    public void refreshReplacesOnSuccessAndKeepsOldOnFailure() {
        // GIVEN a cached entry
        cache.getOrFetch(effective.identity(), () -> fetch(effective));

        // WHEN a refresh succeeds with a different server list
        mockLlmServer.setModelIds(List.of("new-model"));
        var refreshed = cache.refresh(effective.identity(), () -> fetch(effective));
        assertEquals(List.of("new-model"), ids(refreshed));

        // AND WHEN a refresh fails
        mockLlmServer.enableModelsError();
        assertNull(cache.refresh(effective.identity(), () -> fetch(effective)));

        // THEN the old (successful) entry stays
        assertEquals(List.of("new-model"), ids(cache.cached(effective.identity())));
    }

    @Test
    public void differentIdentitiesAreIndependent() {
        // GIVEN two identities differing only in the api key (same server)
        ConnectionIdentity idA = effective.identity();
        var other = base.toBuilder().apiKey("other-key").build();
        var otherEffective = other.effectiveConnectionFor(AgentModelConfig.empty());
        ConnectionIdentity idB = otherEffective.identity();
        assertNotEquals(idA, idB);

        // WHEN both are fetched
        var listA = cache.getOrFetch(idA, () -> fetch(effective));
        var listB = cache.getOrFetch(idB, () -> fetch(otherEffective));

        // THEN each identity has its own entry
        assertNotNull(listA);
        assertNotNull(listB);
        assertEquals(ids(listA), ids(cache.cached(idA)));
        assertEquals(ids(listB), ids(cache.cached(idB)));
    }
}

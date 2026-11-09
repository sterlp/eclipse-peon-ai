package org.sterl.open.aifactory.config;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConnectionIdentity;
import org.sterl.llmpeon.ai.EffectiveConnection;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.provider.LlmProviders;

/**
 * The models of one connection (R-WP-50) — listed by the core's provider and cached in the core's cache,
 * so a new provider needs no change here and a list a provider gives twice is fetched once.
 *
 * <p>The connection is the one the draft describes: draft provider and draft base URL stand in for the
 * stored values, and a slot keeps its precedence over the base — resolved by the core's
 * {@link LlmConfig#effectiveConnectionFor(AgentModelConfig)}, which is the same path a turn takes, so
 * the page lists the models of the connection its saved values will build. The key is the one value no
 * draft carries: read from the config here, never sent, never answered — which is what lets the page
 * list a connection it cannot read.</p>
 *
 * <p>Every failed or empty listing answers as an empty list (UC-WP-86 GIVEN 2): a page that cannot list
 * must still let its model field be typed, and it must not switch anything. {@link ModelListCache}
 * swallows the failed fetch and caches nothing, so the next need asks again. Only a provider name the
 * core does not offer is refused — a wrong request, not a connection that cannot answer.</p>
 */
@Service
public class ModelListService {

    private final FactoryConfigService config;

    public ModelListService(FactoryConfigService config) {
        this.config = config;
    }

    public List<String> listModels(ModelListRequest request) {
        EffectiveConnection connection = connectionOf(request);
        ConnectionIdentity identity = connection.identity();
        LlmConfig buildConfig = connection.buildConfig();
        Supplier<List<AiModel>> fetch = () -> LlmProviders.of(identity.provider()).listAiModels(buildConfig);

        List<AiModel> listed = Boolean.TRUE.equals(request.refresh())
                ? ModelListCache.instance().refresh(identity, fetch)
                : ModelListCache.instance().getOrFetch(identity, fetch);
        return listed == null ? List.of() : listed.stream().map(AiModel::getId).toList();
    }

    /** The draft's connection: provider and base URL as drafted, everything else as the config holds it. */
    private EffectiveConnection connectionOf(ModelListRequest request) {
        LlmConfig stored = config.load();
        LlmConfig drafted = stored.toBuilder()
                .providerType(providerOf(request.provider(), stored.getProviderType()))
                .url(present(request.url()) ? request.url().trim() : stored.getUrl())
                .build();
        return drafted.effectiveConnectionFor(slotRecord(drafted, request.agent(), request.agentUrl()));
    }

    /** The slot's own record, with the draft URL in front of it; no slot named is the base connection. */
    private static AgentModelConfig slotRecord(LlmConfig drafted, String agentId, String draftUrl) {
        if (!present(agentId)) {
            return AgentModelConfig.empty();
        }
        AgentModelConfig slot = drafted.modelConfigFor(agentId.trim());
        if (!present(draftUrl)) {
            return slot;
        }
        String url = draftUrl.trim();
        return new AgentModelConfig(url, slot.apiKey(), slot.model(), slot.think(), slot.extraBody(),
                slot.temperature());
    }

    /** An empty field names nothing, so the stored provider lists; a name the core lacks is the request's fault. */
    private static AiProvider providerOf(String name, AiProvider stored) {
        if (!present(name)) {
            return stored;
        }
        String draft = name.trim();
        if (!FactoryConfigService.isProviderName(draft)) {
            throw new IllegalArgumentException(FactoryConfigService.PROVIDER_HINT);
        }
        return AiProvider.valueOf(draft);
    }

    /** Absent and empty are the same here: neither is a value, so the stored one applies (R-WP-50). */
    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}

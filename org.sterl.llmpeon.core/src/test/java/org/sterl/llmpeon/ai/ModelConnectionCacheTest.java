package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * BDD 4–8 (Plan 2a §7): per-identity connection cache in {@link ConfiguredChatModel}.
 */
class ModelConnectionCacheTest {

    private static LlmConfig base(AiProvider provider, String url) {
        return LlmConfig.of(provider).model("model-x").url(url).apiKey("base-key").build();
    }

    @Test
    void sameEffectiveIdentityReusesConnection() {
        // GIVEN two agents with the same effective identity (differing only in model)
        ConfiguredChatModel sut = new ConfiguredChatModel(base(AiProvider.OPEN_AI, "http://localhost:1/v1"));
        AgentConfig a1 = agent(AiProvider.OPEN_AI, "http://agent:1/v1", "key-1", "model-a");
        AgentConfig a2 = agent(AiProvider.OPEN_AI, "http://agent:1/v1", "key-1", "model-b");
        // WHEN
        StreamingChatModel m1 = sut.modelFor(a1);
        StreamingChatModel m2 = sut.modelFor(a2);
        // THEN
        assertThat(m2).isSameAs(m1);
    }

    @Test
    void differentIdentityBuildsSeparateConnection() {
        // GIVEN agents with different url and key
        ConfiguredChatModel sut = new ConfiguredChatModel(base(AiProvider.OPEN_AI, "http://localhost:1/v1"));
        // WHEN
        StreamingChatModel byUrl = sut.modelFor(agent(AiProvider.OPEN_AI, "http://agent:1/v1", "key-1", null));
        StreamingChatModel byKey = sut.modelFor(agent(AiProvider.OPEN_AI, "http://agent:2/v1", "key-2", null));
        // THEN
        assertThat(byKey).isNotSameAs(byUrl);
    }

    @Test
    void anthropicBodyChangesIdentityOpenAiBodyDoesNot() {
        String body1 = "{\"foo\":1}";
        String body2 = "{\"foo\":2}";
        // GIVEN two Anthropic agents with different extraBody
        ConfiguredChatModel anthropic = new ConfiguredChatModel(base(AiProvider.ANTHROPIC, "https://api.anthropic.com"));
        StreamingChatModel a1 = anthropic.modelFor(agent(AiProvider.ANTHROPIC, "https://api.anthropic.com", "key-1", body1));
        StreamingChatModel a2 = anthropic.modelFor(agent(AiProvider.ANTHROPIC, "https://api.anthropic.com", "key-1", body2));
        // THEN the body is part of the identity → separate connections
        assertThat(a2).isNotSameAs(a1);
        // GIVEN two OpenAI agents with different extraBody
        ConfiguredChatModel openAi = new ConfiguredChatModel(base(AiProvider.OPEN_AI, "http://localhost:1/v1"));
        StreamingChatModel o1 = openAi.modelFor(agent(AiProvider.OPEN_AI, "http://agent:1/v1", "key-1", body1));
        StreamingChatModel o2 = openAi.modelFor(agent(AiProvider.OPEN_AI, "http://agent:1/v1", "key-1", body2));
        // THEN the body is per-request, not part of the identity → same connection
        assertThat(o2).isSameAs(o1);
    }

    @Test
    void baseConnectionIsInheritedInstance() {
        // GIVEN an agent without own url/key/body
        ConfiguredChatModel sut = new ConfiguredChatModel(base(AiProvider.OPEN_AI, "http://localhost:1/v1"));
        AgentConfig agent = AgentConfig.builder().provider(AiProvider.OPEN_AI).build();
        // WHEN
        StreamingChatModel model = sut.modelFor(agent);
        // THEN the shared base instance is returned (no double build)
        assertThat(model).isSameAs(sut.getChatModel());
        // and a null agent resolves to the same base instance
        assertThat(sut.modelFor(null)).isSameAs(sut.getChatModel());
    }

    @Test
    void configUpdateClearsAgentConnections() {
        ConfiguredChatModel sut = new ConfiguredChatModel(base(AiProvider.OPEN_AI, "http://localhost:1/v1"));
        AgentConfig agent = agent(AiProvider.OPEN_AI, "http://agent:1/v1", "key-1", null);
        // GIVEN a cached agent connection
        StreamingChatModel cached = sut.modelFor(agent);
        // WHEN the base config changes
        sut.updateConfig(base(AiProvider.OPEN_AI, "http://localhost:2/v1"));
        // THEN the next modelFor builds a fresh connection
        assertThat(sut.modelFor(agent)).isNotSameAs(cached);
        // WHEN the baked-in model changes
        StreamingChatModel cachedModel = sut.modelFor(agent);
        assertThat(sut.withModel("model-y")).isTrue();
        // THEN the next modelFor builds a fresh connection
        assertThat(sut.modelFor(agent)).isNotSameAs(cachedModel);
        // WHEN the build-time think flag changes
        StreamingChatModel cachedThink = sut.modelFor(agent);
        assertThat(sut.withThinkSupported(true)).isTrue();
        // THEN the next modelFor builds a fresh connection
        assertThat(sut.modelFor(agent)).isNotSameAs(cachedThink);
    }

    private static AgentConfig agent(AiProvider provider, String url, String apiKey, String extraBody) {
        return AgentConfig.builder().provider(provider).url(url).apiKey(apiKey).extraBody(extraBody).build();
    }
}

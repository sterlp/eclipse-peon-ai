package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class LlmConfigLoaderTest {

    @Test
    void legacyTemperatureKeysIgnored() {
        var store = new MapLlmConfigStore();
        store.put("llm.planTemperature", "0.7");
        store.put("llm.devTemperature", "0.5");

        var config = LlmConfigLoader.load(store);

        assertThat(config.devAgentConfig().getTemperature()).isNull();
        assertThat(config.poAgentConfig().getTemperature()).isNull();
        assertThat(config.planAgentConfig().getTemperature()).isNull();
        assertThat(config.searchAgentConfig().getTemperature()).isNull();
        assertThat(config.compactAgentConfig().getTemperature()).isNull();
    }

    @Test
    void loadsAgentTemperatureIntoRecord() {
        var store = new MapLlmConfigStore();
        store.put("llm.agent.plan.temperature", "0.4");

        var config = LlmConfigLoader.load(store);

        assertThat(config.modelConfigFor(AgentModelConfig.PLAN).temperature()).isEqualTo("0.4");
    }

    @Test
    void loaderRebuildsBaseConfig() {
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        store.put(LlmConfigKeys.MODEL, "gpt-4o");
        store.put(LlmConfigKeys.URL, "http://base:1234/v1");
        store.put(LlmConfigKeys.API_KEY, "base-key");
        store.put(LlmConfigKeys.TIMEOUT, "120");
        store.put(LlmConfigKeys.MAX_TOKENS, "4096");
        store.put(LlmConfigKeys.TOKEN_WINDOW, "100000");
        store.put(LlmConfigKeys.THINK_SUPPORTED, "true");
        store.put(LlmConfigKeys.SEND_THINKING_ENABLED, "false");
        store.put(LlmConfigKeys.QUERY_PARAMS, "a=1,b=2");
        store.put(LlmConfigKeys.SHELL_CONFIRMATION_ENABLED, "always");

        var config = LlmConfigLoader.load(store);

        assertThat(config.getProviderType()).isEqualTo(AiProvider.OPEN_AI);
        assertThat(config.getModel()).isEqualTo("gpt-4o");
        assertThat(config.getUrl()).isEqualTo("http://base:1234/v1");
        assertThat(config.getApiKey()).isEqualTo("base-key");
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(config.getMaxTokens()).isEqualTo(4096);
        assertThat(config.getAutoCompactAfter()).isEqualTo(100_000);
        assertThat(config.isThinkSupported()).isTrue();
        assertThat(config.shouldWeSendThinkingBackToLLM()).isFalse();
        assertThat(config.getQueryParams()).containsEntry("a", "1").containsEntry("b", "2");
        assertThat(config.isShellCommandConfirmationRequired()).isTrue();
    }

    @Test
    void loaderRebuildsPerAgentRecords() {
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.MODEL, "gpt-4o");
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_MODEL), "opus");
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_URL), "http://plan:5678/v1");
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_THINK), "high");

        var config = LlmConfigLoader.load(store);

        var plan = config.modelConfigFor(AgentModelConfig.PLAN);
        assertThat(plan.model()).isEqualTo("opus");
        assertThat(plan.url()).isEqualTo("http://plan:5678/v1");
        assertThat(plan.think()).isEqualTo("high");
        assertThat(config.getModel()).isEqualTo("gpt-4o");
    }

    @Test
    void loaderDevModelIsBaseModel() {
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.MODEL, "gpt-4o");

        var config = LlmConfigLoader.load(store);

        assertThat(config.modelConfigFor(AgentModelConfig.DEV).model()).isEqualTo("gpt-4o");
    }

    @Test
    void loaderIgnoresUnknownKeys() {
        var store = new MapLlmConfigStore();
        store.put("llm.planModel", "opus");
        store.put("llm.thinkOnString", "high");
        store.put("llm.planThinkEnabled", "true");

        var config = LlmConfigLoader.load(store);

        var plan = config.modelConfigFor(AgentModelConfig.PLAN);
        assertThat(plan.model()).isNull();
        assertThat(plan.think()).isNull();
    }

    @Test
    void loaderMissingPerAgentFieldsAreNull() {
        var config = LlmConfigLoader.load(new MapLlmConfigStore());

        for (var id : AgentModelConfig.CORE_IDS) {
            assertThat(config.modelConfigFor(id)).isEqualTo(AgentModelConfig.empty());
        }
    }

    @Test
    void loaderRebuildsPoRecord() {
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_MODEL), "claude-x");
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_URL), "http://po/v1");
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_THINK), "high");

        var po = LlmConfigLoader.load(store).modelConfigFor(AgentModelConfig.PO);

        assertThat(po.model()).isEqualTo("claude-x");
        assertThat(po.url()).isEqualTo("http://po/v1");
        assertThat(po.think()).isEqualTo("high");
    }

    @Test
    void missingPoKeysYieldEmptySlot() {
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_MODEL), "gpt-5");

        var config = LlmConfigLoader.load(store);

        assertThat(config.getModelConfigs()).containsKey(AgentModelConfig.PO);
        assertThat(config.modelConfigFor(AgentModelConfig.PO)).isEqualTo(AgentModelConfig.empty());
        assertThat(config.modelConfigFor(AgentModelConfig.PLAN).model()).isEqualTo("gpt-5");
    }

    @Test
    void loaderFallsBackToDefaultsOnMissingKeys() {
        var config = LlmConfigLoader.load(new MapLlmConfigStore());

        assertThat(config.getProviderType()).isEqualTo(AiProvider.OLLAMA);
        assertThat(config.getTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(config.getMaxTokens()).isZero();
        assertThat(config.getAutoCompactAfter()).isEqualTo(80_000);
        assertThat(config.isThinkSupported()).isFalse();
        assertThat(config.shouldWeSendThinkingBackToLLM()).isTrue();
    }

    @Test
    void loaderDefaultsShowRealtimeAiResponse() {
        var config = LlmConfigLoader.load(new MapLlmConfigStore());

        assertThat(config.isShowRealtimeAiResponse()).isTrue();
    }

    @Test
    void loaderParsesInvalidTypedValuesToFallback() {
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.TIMEOUT, "not-a-number");
        store.put(LlmConfigKeys.MAX_TOKENS, "abc");
        store.put(LlmConfigKeys.PROVIDER_TYPE, "NOT_A_PROVIDER");

        var config = LlmConfigLoader.load(store);

        assertThat(config.getTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(config.getMaxTokens()).isZero();
        assertThat(config.getProviderType()).isEqualTo(AiProvider.OLLAMA);
    }
}

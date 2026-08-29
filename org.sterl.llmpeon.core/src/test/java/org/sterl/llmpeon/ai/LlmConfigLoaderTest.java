package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class LlmConfigLoaderTest {

    @Test
    void loaderRebuildsBaseConfig() {
        // GIVEN a store carrying the base keys
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
        store.put(LlmConfigKeys.PLAN_TEMPERATURE, "0.7");
        store.put(LlmConfigKeys.DEV_TEMPERATURE, "0.5");
        store.put(LlmConfigKeys.QUERY_PARAMS, "a=1,b=2");
        store.put(LlmConfigKeys.SHELL_CONFIRMATION_ENABLED, "always");

        // WHEN
        var config = LlmConfigLoader.load(store);

        // THEN the base config carries the values (typed parsing applied)
        assertThat(config.getProviderType()).isEqualTo(AiProvider.OPEN_AI);
        assertThat(config.getModel()).isEqualTo("gpt-4o");
        assertThat(config.getUrl()).isEqualTo("http://base:1234/v1");
        assertThat(config.getApiKey()).isEqualTo("base-key");
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(config.getMaxTokens()).isEqualTo(4096);
        assertThat(config.getAutoCompactAfter()).isEqualTo(100_000);
        assertThat(config.isThinkSupported()).isTrue();
        assertThat(config.shouldWeSendThinkingBackToLLM()).isFalse();
        assertThat(config.getPlanTemperature()).isEqualTo(0.7);
        assertThat(config.getDevTemperature()).isEqualTo(0.5);
        assertThat(config.getQueryParams()).containsEntry("a", "1").containsEntry("b", "2");
        assertThat(config.isShellCommandConfirmationRequired()).isTrue();
    }

    @Test
    void loaderRebuildsPerAgentRecords() {
        // GIVEN a store with a plan agent's model + url
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.MODEL, "gpt-4o");
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_MODEL), "opus");
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_URL), "http://plan:5678/v1");
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_THINK), "high");

        // WHEN
        var config = LlmConfigLoader.load(store);

        // THEN the plan record carries the values; base keys unchanged
        var plan = config.modelConfigFor(AgentModelConfig.PLAN);
        assertThat(plan.model()).isEqualTo("opus");
        assertThat(plan.url()).isEqualTo("http://plan:5678/v1");
        assertThat(plan.think()).isEqualTo("high");
        assertThat(config.getModel()).isEqualTo("gpt-4o");
    }

    @Test
    void loaderDevModelIsBaseModel() {
        // GIVEN a store with a base model but no dev-specific model key
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.MODEL, "gpt-4o");

        // WHEN
        var config = LlmConfigLoader.load(store);

        // THEN the dev record's model is the base model
        assertThat(config.modelConfigFor(AgentModelConfig.DEV).model()).isEqualTo("gpt-4o");
    }

    @Test
    void loaderIgnoresUnknownKeys() {
        // GIVEN a store with a historic/removed key
        var store = new MapLlmConfigStore();
        store.put("llm.planModel", "opus");
        store.put("llm.thinkOnString", "high");
        store.put("llm.planThinkEnabled", "true");

        // WHEN (no migration, no exception)
        var config = LlmConfigLoader.load(store);

        // THEN the removed keys are ignored — plan record is empty
        var plan = config.modelConfigFor(AgentModelConfig.PLAN);
        assertThat(plan.model()).isNull();
        assertThat(plan.think()).isNull();
    }

    @Test
    void loaderMissingPerAgentFieldsAreNull() {
        // GIVEN an empty store (no per-agent keys)
        var store = new MapLlmConfigStore();

        // WHEN
        var config = LlmConfigLoader.load(store);

        // THEN every agent record is empty (null fields = inherit base / provider default)
        for (var id : new String[] { AgentModelConfig.DEV, AgentModelConfig.PLAN,
                AgentModelConfig.SEARCH, AgentModelConfig.COMPACT }) {
            assertThat(config.modelConfigFor(id)).isEqualTo(AgentModelConfig.empty());
        }
    }

    @Test
    void loaderFallsBackToDefaultsOnMissingKeys() {
        // GIVEN an empty store
        var store = new MapLlmConfigStore();

        // WHEN
        var config = LlmConfigLoader.load(store);

        // THEN the LlmConfig defaults apply
        assertThat(config.getProviderType()).isEqualTo(AiProvider.OLLAMA);
        assertThat(config.getTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(config.getMaxTokens()).isZero();
        assertThat(config.getAutoCompactAfter()).isEqualTo(80_000);
        assertThat(config.isThinkSupported()).isFalse();
        assertThat(config.shouldWeSendThinkingBackToLLM()).isTrue();
        assertThat(config.getPlanTemperature()).isEqualTo(1.0);
        assertThat(config.getDevTemperature()).isEqualTo(0.6);
    }

    @Test
    void loaderParsesInvalidTypedValuesToFallback() {
        // GIVEN a store with malformed typed values
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.TIMEOUT, "not-a-number");
        store.put(LlmConfigKeys.PLAN_TEMPERATURE, "abc");
        store.put(LlmConfigKeys.PROVIDER_TYPE, "NOT_A_PROVIDER");

        // WHEN
        var config = LlmConfigLoader.load(store);

        // THEN the fallbacks apply (no exception)
        assertThat(config.getTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(config.getPlanTemperature()).isEqualTo(1.0);
        assertThat(config.getProviderType()).isEqualTo(AiProvider.OLLAMA);
    }
}

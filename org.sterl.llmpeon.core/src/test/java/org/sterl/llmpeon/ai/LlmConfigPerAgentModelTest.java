package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LlmConfigPerAgentModelTest {

    @Test
    void testSearchModelFallbackToDefault() {
        var config = LlmConfig.builder()
                .model("default-model")
                .searchModel("")
                .build();
        assertEquals("default-model", config.getSearchModel());
    }

    @Test
    void testSearchModelOverridesDefault() {
        var config = LlmConfig.builder()
                .model("default-model")
                .searchModel("search-specific-model")
                .build();
        assertEquals("search-specific-model", config.getSearchModel());
    }

    @Test
    void testPlanModelFallbackToDefault() {
        var config = LlmConfig.builder()
                .model("default-model")
                .planModel(null)
                .build();
        assertEquals("default-model", config.getPlanModel());
    }

    @Test
    void testPlanModelOverridesDefault() {
        var config = LlmConfig.builder()
                .model("default-model")
                .planModel("plan-specific-model")
                .build();
        assertEquals("plan-specific-model", config.getPlanModel());
    }

    @Test
    void testDevModelFallbackToDefault() {
        var config = LlmConfig.builder()
                .model("default-model")
                .devModel("")
                .build();
        assertEquals("default-model", config.getDevModel());
    }

    @Test
    void testDevModelOverridesDefault() {
        var config = LlmConfig.builder()
                .model("default-model")
                .devModel("dev-specific-model")
                .build();
        assertEquals("dev-specific-model", config.getDevModel());
    }
}

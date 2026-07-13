package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.model.AiModel;

class ConfiguredModelTest {

    @Test
    void test_takes_model() throws Exception {
        // GIVEN
        var subject = new ConfiguredChatModel(LlmConfig.newConfig(null, "http://bar"));
        
        // WHEN
        subject.resolveModel(Arrays.asList(AiModel.builder().id("foo").build()));
        
        // THEN
        assertEquals("foo", subject.getConfig().getModel());
    }
    
    @Test
    void test_model_keeps() throws Exception {
        // GIVEN
        var subject = new ConfiguredChatModel(LlmConfig.newConfig("foo", "http://bar"));
        
        // WHEN
        subject.resolveModel(Arrays.asList(AiModel.builder().id("foo").build(),
                AiModel.builder().id("bar").build()));
        
        // THEN
        assertEquals("foo", subject.getConfig().getModel());
    }
    
    @Test
    void test_overwrites_model() throws Exception {
        // GIVEN
        var subject = new ConfiguredChatModel(LlmConfig.newConfig("bar", "http://bar"));
        
        // WHEN
        subject.resolveModel(Arrays.asList(AiModel.builder().id("foo").build()));
        
        // THEN
        assertEquals("foo", subject.getConfig().getModel());
    }

    @Test
    void test_withModel_same_model_no_change() {
        var subject = new ConfiguredChatModel(LlmConfig.newConfig("foo", "http://bar"));

        boolean changed = subject.withModel("foo");

        assertFalse(changed);
    }

    @Test
    void test_withModel_null_max_tokens_does_not_affect_auto_compact() {
        var config = LlmConfig.builder().model("old").url("http://bar").autoCompactAfter(80000).build();
        var subject = new ConfiguredChatModel(config);

        boolean changed = subject.withModel("new");

        assertTrue(changed);
        assertEquals("new", subject.getConfig().getModel());
        assertEquals(80000, subject.getConfig().getAutoCompactAfter());
    }

    @Test
    void test_withModel_null_id_does_not_throw_npe() {
        var config = LlmConfig.builder().model("old").url("http://bar").build();
        var subject = new ConfiguredChatModel(config);

        assertDoesNotThrow(() -> {
            boolean changed = subject.withModel(null);
            // Should return true since models are different (null != "old")
            assertFalse(changed);
        });
        assertEquals("old", subject.getConfig().getModel());
    }
}

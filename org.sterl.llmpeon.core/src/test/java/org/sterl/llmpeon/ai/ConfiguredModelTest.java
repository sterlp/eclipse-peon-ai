package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.model.AiModel;

class ConfiguredModelTest {

    @Test
    void test_takes_model() throws Exception {
        // GIVEN
        var subject = new ConfiguredModel(LlmConfig.newConfig(null, "http://bar"));
        
        // WHEN
        subject.resolveModel(Arrays.asList(AiModel.builder().id("foo").build()));
        
        // THEN
        assertEquals("foo", subject.getModel());
    }
    
    @Test
    void test_model_keeps() throws Exception {
        // GIVEN
        var subject = new ConfiguredModel(LlmConfig.newConfig("foo", "http://bar"));
        
        // WHEN
        subject.resolveModel(Arrays.asList(AiModel.builder().id("foo").build(),
                AiModel.builder().id("bar").build()));
        
        // THEN
        assertEquals("foo", subject.getModel());
    }
    
    @Test
    void test_overwrites_model() throws Exception {
        // GIVEN
        var subject = new ConfiguredModel(LlmConfig.newConfig("bar", "http://bar"));
        
        // WHEN
        subject.resolveModel(Arrays.asList(AiModel.builder().id("foo").build()));
        
        // THEN
        assertEquals("foo", subject.getModel());
    }

}

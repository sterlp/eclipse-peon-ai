package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.model.AiModel;

import com.fasterxml.jackson.databind.ObjectMapper;

class LlmConfigTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void test_serializtion() throws Exception {
        // GIVEN
        var subject = LlmConfig.newConfig("foo", "http://bar");
        var asString = mapper.writeValueAsString(subject);
        assertTrue(asString.contains("foo"));
        assertTrue(asString.contains("http://bar"));
        
        // WHEN
        var read = mapper.readValue(asString, LlmConfig.class);
        
        // THEN
        assertEquals(subject, read);
        assertEquals("foo", read.getModel());
    }
    
    @Test
    void test_takes_model() throws Exception {
        // GIVEN
        var subject = LlmConfig.newConfig(null, "http://bar");
        
        // WHEN
        subject = subject.resolveModel(Arrays.asList(AiModel.builder().id("foo").build()));
        
        // THEN
        assertEquals("foo", subject.getModel());
    }
    
    @Test
    void test_model_keeps() throws Exception {
        // GIVEN
        var subject = LlmConfig.newConfig("foo", "http://bar");
        
        // WHEN
        subject = subject.resolveModel(Arrays.asList(AiModel.builder().id("foo").build(),
                AiModel.builder().id("bar").build()));
        
        // THEN
        assertEquals("foo", subject.getModel());
    }
    
    @Test
    void test_overwrites_model() throws Exception {
        // GIVEN
        var subject = LlmConfig.newConfig("bar", "http://bar");
        
        // WHEN
        subject = subject.resolveModel(Arrays.asList(AiModel.builder().id("foo").build()));
        
        // THEN
        assertEquals("foo", subject.getModel());
    }

}

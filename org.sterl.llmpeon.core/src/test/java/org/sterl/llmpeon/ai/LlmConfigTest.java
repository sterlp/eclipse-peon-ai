package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
    void test_maxTokens_default_and_override() throws Exception {
        // GIVEN
        assertEquals(0, LlmConfig.newConfig("foo", "http://bar").getMaxTokens());
        var subject = LlmConfig.builder().model("foo").maxTokens(8192).build();

        // WHEN
        var read = mapper.readValue(mapper.writeValueAsString(subject), LlmConfig.class);

        // THEN
        assertEquals(8192, read.getMaxTokens());
    }
    
}

package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
    
}

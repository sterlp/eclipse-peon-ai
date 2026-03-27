package org.sterl.llmpeon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class FileTest {

    @Test
    void test() {
        System.err.println(Path.of("foo", "bar").normalize());
        System.err.println(Path.of("foo/bar").normalize());
        System.err.println(Path.of("/foo", "bar").normalize());
        System.err.println(Path.of("//foo/", "/bar").toString());
        
        System.err.println(Path.of("/java-minimal\\src\\main\\resources\\archetype-resources\\src\\main\\java\\HelloWorld.java"));
    }
    
    @ParameterizedTest
    @CsvSource({
        "foo,foo",
        "foo/bar,foo",
        "/foo/bar,foo",
        "/foo/bar,foo"
    })
    void testPathSegment(String in, String expected) {
        
        var path = Path.of(in);
        Path firstSegment = path.getName(0);
        
        assertEquals(expected, firstSegment.toString());
    }
    
    @Test
    void testFoo() throws JsonProcessingException {
        var mapper = new ObjectMapper();
        var msg = new SimpleMessage(Type.USER, "Hello");
        System.out.println("appendMessage(" + mapper.writeValueAsString(msg) + ");");
    }
}

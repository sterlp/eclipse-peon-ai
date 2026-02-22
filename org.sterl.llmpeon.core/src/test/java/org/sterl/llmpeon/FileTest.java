package org.sterl.llmpeon;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
        "/foo/bar,foo",
        "\\foo\\bar,foo"
    })
    void testPathSegment(String in, String expected) {
        var path = Path.of(in);
        Path firstSegment = path.getName(0);
        
        assertEquals(expected, firstSegment.toString());
    }

}

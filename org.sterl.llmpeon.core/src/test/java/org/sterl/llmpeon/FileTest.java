package org.sterl.llmpeon;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FileTest {

    @Test
    void test() {
        System.err.println(Path.of("foo", "bar").normalize());
        System.err.println(Path.of("foo/bar").normalize());
        System.err.println(Path.of("/foo", "bar").normalize());
        System.err.println(Path.of("//foo/", "/bar").toString());
        
        System.err.println(Path.of("/java-minimal\\src\\main\\resources\\archetype-resources\\src\\main\\java\\HelloWorld.java"));
    }

}

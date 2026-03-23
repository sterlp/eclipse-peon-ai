package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilsTest {

    @TempDir
    Path tempDir;

    /** Bug 3: second write must fully replace the file content, not leave stale bytes. */
    @Test
    void writeString_overwritesShorterContent() throws IOException {
        Path file = tempDir.resolve("test.txt");
        FileUtils.writeString(file, "long original content");
        FileUtils.writeString(file, "short");
        assertEquals("short", Files.readString(file));
    }

    @Test
    void writeString_createsNewFile() throws IOException {
        Path file = tempDir.resolve("new.txt");
        FileUtils.writeString(file, "hello");
        assertEquals("hello", Files.readString(file));
    }
}

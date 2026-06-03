package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FileUtilsTest {

    @TempDir
    Path tempDir;
    
    @ParameterizedTest
    @CsvSource({
        "\\foo\\bar    , /foo/bar",
        "**/foo        , **/foo"
    })
    void test_normalizePath(String value, String expected) {
        assertThat(FileUtils.normalizePath(value)).isEqualTo(expected);
    }

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

    @Test
    void applyEdit_lfFileWithLfOldString() {
        var result = FileUtils.applyEdit("test.txt", "one\ntwo\nthree", "two\nthree", "2\n3");

        assertEquals("one\n2\n3", result);
    }

    @Test
    void applyEdit_crlfFileWithCrlfOldString() {
        var result = FileUtils.applyEdit("test.txt", "one\r\ntwo\r\nthree", "two\r\nthree", "2\r\n3");

        assertEquals("one\r\n2\r\n3", result);
    }

    @Test
    void applyEdit_crlfFileWithCrlfOldStringNormalizesLfNewString() {
        var result = FileUtils.applyEdit("test.txt", "one\r\ntwo\r\nthree", "two\r\nthree", "2\n3");

        assertEquals("one\r\n2\r\n3", result);
    }

    @Test
    void applyEdit_crlfFileWithLfOldStringKeepsCrlf() {
        var result = FileUtils.applyEdit("test.txt", "one\r\ntwo\r\nthree", "two\nthree", "2\n3");

        assertEquals("one\r\n2\r\n3", result);
    }

    @Test
    void applyEdit_lfFileWithCrlfOldStringKeepsLf() {
        var result = FileUtils.applyEdit("test.txt", "one\ntwo\nthree", "two\r\nthree", "2\r\n3");

        assertEquals("one\n2\n3", result);
    }

    @Test
    void applyEdit_duplicateAfterLineEndingNormalizationFails() {
        var error = assertThrows(IllegalArgumentException.class, () ->
                FileUtils.applyEdit("test.txt", "one\r\ntwo\r\none\r\ntwo", "one\ntwo", "1\n2"));

        assertThat(error.getMessage()).contains("old_string found 2 times");
    }

    @Test
    void applyEdit_missingOldStringStillFails() {
        var error = assertThrows(IllegalArgumentException.class, () ->
                FileUtils.applyEdit("test.txt", "one\r\ntwo\r\nthree", "two\nmissing", "2\n3"));

        assertThat(error.getMessage()).contains("old_string: 'two\nmissing' not found");
    }
}

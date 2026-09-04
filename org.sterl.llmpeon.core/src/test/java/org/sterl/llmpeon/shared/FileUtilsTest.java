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

    @ParameterizedTest
    @CsvSource({
        "docs/../../x           , ../x",
        "a/b/../c               , a/c",
        "./x                    , x",
        "/abs/docs/../x.md      , /abs/x.md",
        "a/docs/../../secret.txt, secret.txt",
        "a/docs/..\\..\\secret.txt, secret.txt"
    })
    void test_normalizeSegments(String value, String expected) {
        assertThat(FileUtils.normalizeSegments(value)).isEqualTo(expected);
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

        assertEquals("one\n2\n3", result.content());
        assertThat(result.count()).isEqualTo(1);
    }

    @Test
    void applyEdit_crlfFileWithCrlfOldString() {
        var result = FileUtils.applyEdit("test.txt", "one\r\ntwo\r\nthree", "two\r\nthree", "2\r\n3");

        assertEquals("one\r\n2\r\n3", result.content());
        assertThat(result.count()).isEqualTo(1);
    }

    @Test
    void applyEdit_crlfFileWithCrlfOldStringNormalizesLfNewString() {
        var result = FileUtils.applyEdit("test.txt", "one\r\ntwo\r\nthree", "two\r\nthree", "2\n3");

        assertEquals("one\r\n2\r\n3", result.content());
        assertThat(result.count()).isEqualTo(1);
    }

    @Test
    void applyEdit_crlfFileWithLfOldStringKeepsCrlf() {
        var result = FileUtils.applyEdit("test.txt", "one\r\ntwo\r\nthree", "two\nthree", "2\n3");

        assertEquals("one\r\n2\r\n3", result.content());
        assertThat(result.count()).isEqualTo(1);
    }

    @Test
    void applyEdit_lfFileWithCrlfOldStringKeepsLf() {
        var result = FileUtils.applyEdit("test.txt", "one\ntwo\nthree", "two\r\nthree", "2\r\n3");

        assertEquals("one\n2\n3", result.content());
        assertThat(result.count()).isEqualTo(1);
    }

    @Test
    void applyEdit_replacesMultipleOccurrences() {
        // GIVEN
        String content = "one\r\ntwo\r\none\r\ntwo";
        // WHEN
        var result = FileUtils.applyEdit("test.txt", content, "one\ntwo", "1\n2");
        // THEN
        assertThat(result.content()).isEqualTo("1\r\n2\r\n1\r\n2");
        assertThat(result.count()).isEqualTo(2);
    }

    @Test
    void applyEdit_deleteReportsCount() {
        // GIVEN
        String content = "gone\nkeep\ngone\n";
        // WHEN
        var result = FileUtils.applyEdit("test.txt", content, "gone\n", "");
        // THEN
        assertThat(result.content()).isEqualTo("keep\n");
        assertThat(result.count()).isEqualTo(2);
    }

    @Test
    void applyEdit_missingOldStringStillFails() {
        // WHEN
        var error = assertThrows(IllegalArgumentException.class, () ->
                FileUtils.applyEdit("test.txt", "one\r\ntwo\r\nthree", "two\nmissing", "2\n3"));
        // THEN
        assertThat(error.getMessage()).contains("test.txt");
    }
}

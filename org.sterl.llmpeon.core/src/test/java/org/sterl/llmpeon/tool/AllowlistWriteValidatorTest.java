package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AllowlistWriteValidatorTest {

    final WriteValidator docs = WriteValidator.DOCS;

    @Test
    void allows_markdown_in_docs() {
        assertDoesNotThrow(() -> docs.validate("MyProject/docs/feature.md"));
        assertDoesNotThrow(() -> docs.validate("docs/feature.md")); // via *.md
    }

    @Test
    void allows_any_markdown_anywhere() {
        assertDoesNotThrow(() -> docs.validate("README.md"));
    }

    @Test
    void allows_non_markdown_inside_a_docs_path() {
        assertDoesNotThrow(() -> docs.validate("proj/docs/img/logo.png"));
    }

    @Test
    void rejects_source_file() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> docs.validate("src/main/java/Foo.java"));
        assertTrue(ex.getMessage().contains("Write denied"));
    }

    @Test
    void allowAll_allows_everything() {
        assertDoesNotThrow(() -> WriteValidator.ALLOW_ALL.validate("anything/at/all.bin"));
    }
}

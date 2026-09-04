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
    void rejectsTraversalThroughDocs() {
        // Bug-Hunt #9: raw glob match lets a/docs/../../secret.txt through (matches */docs/*);
        // the normalized path (secret.txt) is outside the allowlist → must throw.
        var ex = assertThrows(IllegalArgumentException.class,
                () -> docs.validate("a/docs/../../secret.txt"));
        assertTrue(ex.getMessage().contains("Write denied"));
        // The LLM sees the original path it sent, not the normalized one.
        assertTrue(ex.getMessage().contains("a/docs/../../secret.txt"),
                "error should show the original path, was: " + ex.getMessage());
    }

    @Test
    void rejectsBackslashTraversalThroughDocs() {
        // Bug-Hunt #9f: mixed-separator traversal a/docs/..\..\secret.txt —
        // raw string contains /docs/ so the glob */docs/* matches before segment resolution;
        // after \→/ conversion the path resolves to secret.txt → outside the allowlist → must throw.
        var ex = assertThrows(IllegalArgumentException.class,
                () -> docs.validate("a/docs/..\\..\\secret.txt"));
        assertTrue(ex.getMessage().contains("Write denied"));
        assertTrue(ex.getMessage().contains("a/docs/..\\..\\secret.txt"),
                "error should show the original path, was: " + ex.getMessage());
    }

    @Test
    void rejectsBddTraversalExample() {
        // Regression pin for the original BDD string: docs/../../secret.txt has no /docs/ substring,
        // so it never matched */docs/* — already rejected, keep it that way.
        assertThrows(IllegalArgumentException.class,
                () -> docs.validate("docs/../../secret.txt"));
    }

    @Test
    void allowAll_allows_everything() {
        assertDoesNotThrow(() -> WriteValidator.ALLOW_ALL.validate("anything/at/all.bin"));
    }
}

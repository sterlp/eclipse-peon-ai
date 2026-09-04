package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextFileTypesTest {

    @Test
    void textExtensionsAreShared() {
        assertThat(TextFileTypes.EXTENSIONS).contains("java", "csv", "bnd", "prefs");
    }

    @Test
    void filenamesWithoutExtensionAreText() {
        assertThat(TextFileTypes.isTextFile("Dockerfile")).isTrue();
        assertThat(TextFileTypes.isTextFile("Makefile")).isTrue();
        assertThat(TextFileTypes.isTextFile("notes.peonx")).isFalse();
    }
}

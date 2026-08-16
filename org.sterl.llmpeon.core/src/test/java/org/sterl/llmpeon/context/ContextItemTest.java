package org.sterl.llmpeon.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextItemTest {

    @TempDir
    Path tempDir;

    @Test
    void render_readsFileContentWithHeader() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        DiskFileContextItem item = new DiskFileContextItem(file);
        String rendered = item.render();

        assertThat(rendered)
            .startsWith(file.toAbsolutePath().normalize() + ":" + System.lineSeparator() + "---" + System.lineSeparator())
            .endsWith("hello world");
    }

    @Test
    void render_readsFreshContentWhenFileModified() throws IOException {
        Path file = tempDir.resolve("modified.txt");
        Files.writeString(file, "v1");

        DiskFileContextItem item = new DiskFileContextItem(file);
        String first = item.render();

        Files.writeString(file, "v2");
        String second = item.render();

        assertThat(first).contains("v1");
        assertThat(second).contains("v2");
    }

    @Test
    void render_returnsNullWhenFileNotFound() {
        Path missing = tempDir.resolve("nonexistent.txt");

        DiskFileContextItem item = new DiskFileContextItem(missing);

        assertThat(item.render()).isNull();
    }

    @Test
    void label_returnsAbsolutePath() throws IOException {
        Path file = tempDir.resolve("labeled.txt");
        Files.writeString(file, "content");

        DiskFileContextItem item = new DiskFileContextItem(file);

        assertThat(item.label()).isEqualTo(file.toAbsolutePath().normalize().toString());
    }

    @Test
    void dedupKey_returnsHeader() throws IOException {
        Path file = tempDir.resolve("keyed.txt");
        Files.writeString(file, "content");

        DiskFileContextItem item = new DiskFileContextItem(file);

        assertThat(item.dedupKey()).isEqualTo(
            item.label() + ":" + System.lineSeparator() + "---" + System.lineSeparator());
    }

    @Test
    void dedupKey_defaultIsNull() {
        ContextItem item = new SimpleContextItem("content");

        assertThat(item.dedupKey()).isNull();
    }

    @Test
    void label_defaultReturnsNull() {
        ContextItem item = () -> "content";
        assertThat(item.label()).isNull();
    }

    @Test
    void label_overrideReturnsCustomValue() {
        ContextItem item = new ContextItem() {
            @Override public String render() { return "content"; }
            @Override public String label() { return "my-label"; }
        };
        assertThat(item.label()).isEqualTo("my-label");
    }
}

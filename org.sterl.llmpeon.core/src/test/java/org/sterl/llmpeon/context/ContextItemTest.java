package org.sterl.llmpeon.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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
            .startsWith("Static loaded file " + file + ":\n---\n")
            .endsWith("hello world");
    }

    @Test
    void render_cachesContentWhenFileUnchanged() throws IOException, InterruptedException {
        Path file = tempDir.resolve("cache.txt");
        Files.writeString(file, "initial");

        DiskFileContextItem item = new DiskFileContextItem(file);
        String first = item.render();

        // Wait to ensure any potential timestamp difference
        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(10));
        String second = item.render();

        assertThat(second).isSameAs(first);
    }

    @Test
    void render_invalidatesCacheWhenFileModified() throws IOException, InterruptedException {
        Path file = tempDir.resolve("modified.txt");
        Files.writeString(file, "v1");

        DiskFileContextItem item = new DiskFileContextItem(file);
        String first = item.render();

        // Ensure next modification has a different timestamp
        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(10));
        Files.writeString(file, "v2");

        String second = item.render();

        assertThat(first).contains("v1");
        assertThat(second).contains("v2");
        assertThat(second).isNotSameAs(first);
    }

    @Test
    void render_throwsWhenFileNotFound() {
        Path missing = tempDir.resolve("nonexistent.txt");

        DiskFileContextItem item = new DiskFileContextItem(missing);

        assertThatThrownBy(item::render)
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to read file");
    }

    @Test
    void label_defaultReturnsEmpty() {
        ContextItem item = () -> "content";
        assertThat(item.label()).isEmpty();
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

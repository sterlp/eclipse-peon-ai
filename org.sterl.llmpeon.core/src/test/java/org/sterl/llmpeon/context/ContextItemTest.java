package org.sterl.llmpeon.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.agent.model.TaggedResult;

class ContextItemTest {

    @TempDir
    Path tempDir;

    @Test
    void render_readsFileContentWithHeader() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        DiskFileContextItem item = new DiskFileContextItem(file);
        var rendered = item.render();

        assertThat(rendered.body()).contains("hello world");
    }

    @Test
    void render_readsFreshContentWhenFileModified() throws IOException {
        Path file = tempDir.resolve("modified.txt");
        Files.writeString(file, "v1");

        DiskFileContextItem item = new DiskFileContextItem(file);
        var first = item.render();

        Files.writeString(file, "v2");
        var second = item.render();

        assertThat(first.body()).contains("v1");
        assertThat(second.body()).contains("v2");
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
    void dedupKey_returnsBlockStartLine() throws IOException {
        Path file = tempDir.resolve("keyed.txt");
        Files.writeString(file, "content");

        DiskFileContextItem item = new DiskFileContextItem(file);

        assertThat(item.dedupKey()).isEqualTo(TaggedResult.PREFIX + "context: " + item.label());
    }

    @Test
    void dedupKey_defaultIsNull() {
        ContextItem item = new SimpleContextItem("content");

        assertThat(item.dedupKey()).isNull();
    }

    @Test
    void label_defaultReturnsNull() {
        ContextItem item = () -> new TaggedResult("context", "content");
        assertThat(item.label()).isNull();
    }

    @Test
    void label_overrideReturnsCustomValue() {
        ContextItem item = new ContextItem() {
            @Override public TaggedResult render() { return new TaggedResult("context", "content"); }
            @Override public String label() { return "my-label"; }
        };
        assertThat(item.label()).isEqualTo("my-label");
    }

    // R-TR-3
    @Test
    void of_rendersBlockWithContextHead() {
        ContextItem item = ContextItem.of("plain text");

        var rendered = item.render();

        assertThat(rendered.head()).isEqualTo("context");
        assertThat(rendered.body()).isEqualTo("plain text");
        assertThat(item.dedupKey()).isNull();
        assertThat(item.label()).isNull();
    }
}

package org.sterl.llmpeon.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.agent.model.ContextInput;

@Timeout(1)
class ContextInputRenderingTest {

    @TempDir
    Path tmp;

    // R-TR-3
    // R-TR-10
    @Test
    void allCoreContextItemsRenderContextInput() throws IOException {
        // GIVEN — a real file and the three ways core builds a context item
        var file = tmp.resolve("notes.md");
        Files.writeString(file, "hello");
        var abs = file.toAbsolutePath().normalize().toString();
        var disk = new DiskFileContextItem(file);
        var labelled = new SimpleContextItem("lbl", "body");
        var plain = ContextItem.of("plain");

        // THEN — each renders a ContextInput with its exact head/body; the dedup key contract is unchanged
        var rendered = disk.render();
        assertThat(rendered).isExactlyInstanceOf(ContextInput.class);
        assertThat(rendered.head()).isEqualTo("context: " + abs);
        assertThat(rendered.body()).isEqualTo("hello");
        assertThat(disk.dedupKey()).isEqualTo("//-- Start context: " + abs);

        assertThat(labelled.render()).isExactlyInstanceOf(ContextInput.class);
        assertThat(labelled.render().head()).isEqualTo("context: lbl");
        assertThat(labelled.render().body()).isEqualTo("body");
        assertThat(labelled.dedupKey()).isNull();

        assertThat(plain.render()).isExactlyInstanceOf(ContextInput.class);
        assertThat(plain.render().head()).isEqualTo("context");
        assertThat(plain.render().body()).isEqualTo("plain");

        // AND — a file that is not there still means "nothing to inject"
        assertThat(new DiskFileContextItem(tmp.resolve("missing.md")).render()).isNull();
    }
}

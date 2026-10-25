package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link FileLlmConfigStore}: whole-file rewrite, deterministic bytes, atomic and owner-only write.
 *
 * <p><b>No {@code UC-WP-*} marker on any test here — deliberately.</b> This is a core component and
 * {@code aifactory/docs/web-prototype.md} is a factory feature doc, so the {@code UC-WP-*} use cases
 * are carried by the factory tests that use this store, not by this class; an invented marker would
 * be a false anchor ({@code AGENTS-DEV.md} marker rule).</p>
 *
 * <p>Every path lives in a {@link TempDir} — nothing here may read or write {@code ~/.peon},
 * {@code ~/.aifactory}, {@code $HOME} or {@code /}.</p>
 */
class FileLlmConfigStoreTest {

    @TempDir
    Path dir;

    @Test
    void valueSurvivesAReopenedStore() {
        // GIVEN a store that wrote one value
        var file = dir.resolve("config.properties");
        new FileLlmConfigStore(file).put(LlmConfigKeys.MODEL, "qwen3.6-27b-i1");

        // WHEN the file is opened again by a fresh store
        var reopened = new FileLlmConfigStore(file);

        // THEN the value comes back out of the file, not out of memory
        assertThat(reopened.get(LlmConfigKeys.MODEL, null)).isEqualTo("qwen3.6-27b-i1");
    }

    @Test
    void absentKeyReturnsTheDefault() {
        // GIVEN a store over a file that does not exist
        var store = new FileLlmConfigStore(dir.resolve("config.properties"));

        // WHEN / THEN the default answers, and reading created nothing
        assertThat(store.get(LlmConfigKeys.MODEL, "fallback")).isEqualTo("fallback");
        assertThat(Files.exists(dir.resolve("config.properties"))).isFalse();
    }

    @Test
    void blankValueIsTreatedAsAbsentWhenReading() throws IOException {
        // GIVEN a hand-written file whose values are blank (empty and whitespace-only)
        var file = dir.resolve("config.properties");
        Files.writeString(file, "llm.url=\nllm.model=\\ \n");

        // WHEN
        var store = new FileLlmConfigStore(file);

        // THEN both read as absent — the default wins over a blank
        assertThat(store.get(LlmConfigKeys.URL, "fallback")).isEqualTo("fallback");
        assertThat(store.get(LlmConfigKeys.MODEL, "fallback")).isEqualTo("fallback");
    }

    @Test
    void blankValueRemovesTheKeyLikeTheConfigSaverDoes() throws IOException {
        // GIVEN a set key
        var file = dir.resolve("config.properties");
        var store = new FileLlmConfigStore(file);
        store.put(LlmConfigKeys.MODEL, "gpt-x");

        // WHEN the same key is written blank (the API never sends a blank; the saver removes instead)
        store.put(LlmConfigKeys.MODEL, "   ");

        // THEN the key is gone from the file and from the reopened store
        assertThat(Files.readString(file)).doesNotContain(LlmConfigKeys.MODEL);
        assertThat(new FileLlmConfigStore(file).get(LlmConfigKeys.MODEL, "fallback")).isEqualTo("fallback");
    }

    @Test
    void removeOfAnAbsentKeyNeverCreatesTheFile() {
        // GIVEN a store over a file that does not exist
        var file = dir.resolve("config.properties");
        var store = new FileLlmConfigStore(file);

        // WHEN an absent key is removed (and a blank put takes the same path)
        store.remove(LlmConfigKeys.MODEL);
        store.put(LlmConfigKeys.API_KEY, "");

        // THEN no file was written — removing nothing is not a write
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void unknownKeysSurviveAWrite() throws IOException {
        // GIVEN a hand-written file holding keys this store does not know
        var file = dir.resolve("config.properties");
        Files.writeString(file, """
                llm.agent.plan.model=plan-model
                custom.key=custom-value
                llm.model=old-model
                """);

        // WHEN one known value is written through the store
        new FileLlmConfigStore(file).put(LlmConfigKeys.MODEL, "new-model");

        // THEN the unknown keys survive the whole-file rewrite
        var reopened = new FileLlmConfigStore(file);
        assertThat(reopened.get(LlmConfigKeys.agentKey("plan", LlmConfigKeys.AGENT_FIELD_MODEL), null))
                .isEqualTo("plan-model");
        assertThat(reopened.get("custom.key", null)).isEqualTo("custom-value");
        assertThat(reopened.get(LlmConfigKeys.MODEL, null)).isEqualTo("new-model");
    }

    @Test
    void valuesWithSeparatorsAndUmlautsRoundTrip() throws IOException {
        // GIVEN values holding both separators, a comment start, umlauts and a tab
        var file = dir.resolve("config.properties");
        var store = new FileLlmConfigStore(file);
        store.put(LlmConfigKeys.URL, "https://x.example/a:b=c#d");
        store.put(LlmConfigKeys.MODEL, "ÄÖü Modell");
        store.put(LlmConfigKeys.HEADER_PARAMS, "a\tb");

        // WHEN the file is read back through a fresh store
        var reopened = new FileLlmConfigStore(file);
        var raw = Files.readString(file);

        // THEN every value comes back exactly as it went in
        assertThat(reopened.get(LlmConfigKeys.URL, null)).isEqualTo("https://x.example/a:b=c#d");
        assertThat(reopened.get(LlmConfigKeys.MODEL, null)).isEqualTo("ÄÖü Modell");
        assertThat(reopened.get(LlmConfigKeys.HEADER_PARAMS, null)).isEqualTo("a\tb");

        // THEN the file stays one LF-terminated line per key, pure ASCII, with no raw separator
        // inside a value — which is what makes it readable by Properties.load again
        assertThat(raw).doesNotContain("\r");
        assertThat(raw.split("\n")).hasSize(3);
        assertThat(raw).contains("https\\://x.example/a\\:b\\=c\\#d").contains("a\\tb").contains("\\u00c4");
        for (var line : raw.lines().toList()) {
            assertThat(bareSeparators(line))
                    .as("only the separator itself is unescaped in %s", line)
                    .isEqualTo(1);
        }
    }

    @Test
    void rewriteIsByteIdenticalWhenNothingChanged() throws IOException {
        // GIVEN a written file
        var file = dir.resolve("config.properties");
        var store = new FileLlmConfigStore(file);
        store.put(LlmConfigKeys.MODEL, "gpt-x");
        store.put(LlmConfigKeys.URL, "https://x.example");
        var first = Files.readAllBytes(file);

        // WHEN the same content is written again through a reopened store
        new FileLlmConfigStore(file).put(LlmConfigKeys.MODEL, "gpt-x");

        // THEN the bytes are identical: no date comment, no timestamp, no reordering — a no-op save
        // must stay invisible, or every save would be a diff in a versioned home dir
        var second = Files.readAllBytes(file);
        assertThat(second).isEqualTo(first);

        // AND the file carries no comment line and no CR — the two things Properties.store would add
        // (its "#<date>" line makes every write a change, its lineSeparator() is platform-bound)
        var raw = new String(second, StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain("\r");
        assertThat(raw.lines().noneMatch(line -> line.startsWith("#") || line.startsWith("!")))
                .as("no comment line, so no timestamp: %s", raw)
                .isTrue();
    }

    @Test
    void fileIsCreatedWithOwnerOnlyPermissions() throws IOException {
        assumePosix(dir);
        var file = dir.resolve("config.properties");

        // WHEN the first write happens (the file holds the provider key from the first byte on)
        new FileLlmConfigStore(file).put(LlmConfigKeys.API_KEY, "sk-value");

        // THEN it is 600, not the umask default
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    @Test
    void rewriteTightensAPreExistingWorldReadableFile() throws IOException {
        assumePosix(dir);
        // GIVEN a config file that is world-readable, e.g. created by hand before the factory wrote it
        var file = dir.resolve("config.properties");
        new FileLlmConfigStore(file).put(LlmConfigKeys.MODEL, "gpt-x");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

        // WHEN the store writes again
        new FileLlmConfigStore(file).put(LlmConfigKeys.MODEL, "gpt-y");

        // THEN the rewrite tightens the rights instead of keeping the open ones
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    @Test
    void noTempFileIsLeftBehindAfterWrites() throws IOException {
        var file = dir.resolve("config.properties");
        var store = new FileLlmConfigStore(file);

        // WHEN several writes happen, including a removal
        store.put(LlmConfigKeys.MODEL, "gpt-x");
        store.put(LlmConfigKeys.URL, "https://x.example");
        store.remove(LlmConfigKeys.URL);
        store.put(LlmConfigKeys.MODEL, "gpt-y");

        // THEN the directory holds exactly the config file — every temp was moved or deleted
        assertThat(entryNames(dir)).containsExactly("config.properties");
    }

    @Test
    void afailedWriteLeavesNoTempFileAndKeepsTheOldContent() throws IOException {
        // GIVEN a written file whose path is then blocked by a directory: the temp is created and
        // only the move fails, so the cleanup inside the catch is the line under test
        var file = dir.resolve("config.properties");
        var store = new FileLlmConfigStore(file);
        store.put(LlmConfigKeys.MODEL, "old-model");
        Files.delete(file);
        Files.createDirectories(file);
        Files.writeString(file.resolve("keep.txt"), "untouched");

        // WHEN / THEN the write fails loudly, naming the path and never a value
        assertThatThrownBy(() -> store.put(LlmConfigKeys.API_KEY, "sk-secret-value"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("config.properties")
                .hasMessageNotContaining("sk-secret-value");

        // THEN no temp survived and nothing below the blocked target was destroyed
        assertThat(entryNames(dir)).containsExactly("config.properties");
        assertThat(Files.readString(file.resolve("keep.txt"))).isEqualTo("untouched");
    }

    @Test
    void writeCreatesTheMissingParentDirectory() throws IOException {
        // GIVEN a config file below a directory that does not exist yet — a fresh factory home
        var file = dir.resolve("home/config.properties");

        // WHEN
        new FileLlmConfigStore(file).put(LlmConfigKeys.MODEL, "gpt-x");

        // THEN the write creates its parent instead of failing
        assertThat(Files.readString(file)).contains("llm.model=gpt-x");
    }

    /** Counts the {@code =} and {@code :} not protected by a backslash — exactly one per line: the separator. */
    private static int bareSeparators(String line) {
        var bare = 0;
        for (var i = 0; i < line.length(); i++) {
            var c = line.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '=' || c == ':') {
                bare++;
            }
        }
        return bare;
    }

    private static void assumePosix(Path dir) {
        Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"),
                "file system has no posix permissions — nothing to assert about owner-only rights");
    }

    private static List<String> entryNames(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

}

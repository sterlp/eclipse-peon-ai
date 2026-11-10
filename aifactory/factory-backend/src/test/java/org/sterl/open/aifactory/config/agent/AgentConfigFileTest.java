package org.sterl.open.aifactory.config.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.sterl.open.aifactory.config.agent.AgentConfigFile.Changes;
import org.sterl.open.aifactory.config.agent.AgentConfigFile.Values;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * One slot's file as its whole storage (R-WP-23): what the page shows is what the file carries, and what
 * the page saves is what the file keeps — nothing else in it. Every test works on a throwaway home and
 * reads the file back as text, because the prompt body and the untouched keys are the point.
 */
class AgentConfigFileTest {

    private static final String BODY = """

            This is the slot's own prompt.
            Never rewrite it.
            """;

    /** A single round can miss the race window by luck; the per-file lock has to hold in every one. */
    private static final int ROUNDS = 24;

    @TempDir
    Path home;

    private FactoryHome factoryHome;

    @BeforeEach
    void emptyHome() {
        this.factoryHome = FactoryHome.of(home);
    }

    /** UC-WP-44 GIVEN 1: a slot with no file carries nothing, and reading it creates neither file nor folder. */
    @Test
    // UC-WP-44
    void aSlotWithoutAFileCarriesNothingAndIsNotCreated() {
        Values values = file("dev").read();

        assertThat(values.overridden()).isFalse();
        assertThat(values.model()).isNull();
        assertThat(values.url()).isNull();
        assertThat(values.apiKey()).isNull();
        assertThat(values.think()).isNull();
        assertThat(values.temperature()).isNull();
        // the config page is opened far more often than an override is saved
        assertThat(home.resolve("agents")).doesNotExist();
    }

    /** UC-WP-44 GIVEN 2: the file carries exactly the one field that was entered, no other line. */
    @Test
    // UC-WP-44
    void onlyTheSubmittedFieldIsWritten() {
        file("dev").write(new Changes(false, "gpt-5", null, null, null, null, null, null));

        assertThat(text("dev")).isEqualTo("""
                ---
                model: gpt-5
                ---
                """);
        assertThat(file("dev").read().overridden()).isTrue();
    }

    /** UC-WP-44: an empty field is the value "delete" — the key disappears, its neighbours stay. */
    @Test
    // UC-WP-44
    void aClearedAgentFieldIsRemoved() {
        write("dev", """
                ---
                model: gpt-5
                url: https://dev.example
                temperature: 0.2
                ---
                """);

        file("dev").write(new Changes(false, "", null, null, null, null, null, null));

        Values values = file("dev").read();
        assertThat(values.model()).isNull();
        assertThat(values.url()).isEqualTo("https://dev.example");
        assertThat(values.temperature()).isEqualTo("0.2");
        assertThat(text("dev")).doesNotContain("model").contains("url: https://dev.example");
    }

    /**
     * UC-WP-45: the unchecked box is not "seven empty strings" — it reaches every config value of the
     * file, leaves the prompt body alone, and keeps only what the page never shows.
     */
    @Test
    // UC-WP-45
    void uncheckRemovesTheConfigAndKeepsTheBody() {
        write("review", """
                ---
                model: gpt-5
                url: https://review.example
                api_key: sk-secret-0123456789
                think: high
                temperature: 0.1
                tools: read, grep
                read-only: true
                ---
                """ + BODY);

        file("review").write(Changes.clearing());

        assertThat(file("review").read().overridden()).isFalse();
        String stored = text("review");
        assertThat(stored).doesNotContain("model", "url", "api_key", "think", "temperature");
        // R-WP-52 made tools a page field, so a clear removes it too; what the page still never shows —
        // read-only and the body — stays verbatim, the file is only rewritten in its head
        assertThat(stored).doesNotContain("tools").contains("read-only: true")
                .contains("This is the slot's own prompt.")
                .contains("Never rewrite it.");
        // parseYml names a nameless file after its directory — writing that back would add a field
        assertThat(stored).doesNotContain("name:");
    }

    /** UC-WP-45: the checkbox is no stored value — one value in the file is enough to set it, none clears it. */
    @Test
    // UC-WP-45
    void theCheckboxIsRebuiltFromTheFile() {
        write("po", "---\nthink: medium\n---\n" + BODY);
        assertThat(file("po").read().overridden()).isTrue();

        file("po").write(Changes.clearing());

        assertThat(file("po").read().overridden()).isFalse();
        assertThat(text("po")).contains("This is the slot's own prompt.");
    }

    /** UC-WP-46 GIVEN 2: clearing one field of a slot leaves the other values of the same entry. */
    @Test
    // UC-WP-46
    void aClearedCompactFieldLeavesTheOthers() {
        write("compact", "---\nmodel: gpt-5\nthink: medium\n---\n");

        file("compact").write(new Changes(false, "", null, null, null, null, null, null));

        Values values = file("compact").read();
        assertThat(values.model()).isNull();
        assertThat(values.think()).isEqualTo("medium");
        assertThat(text("compact")).isEqualTo("""
                ---
                think: medium
                ---
                """);
    }

    /** A clear of a slot that never had anything writes no file — so an untouched home stays untouched. */
    @Test
    void clearingASlotWithoutAFileWritesNothing() {
        file("plan").write(Changes.clearing());

        assertThat(home.resolve("agents")).doesNotExist();
    }

    /** A save that ends with nothing to store is the same case: no file, no folder (UC-WP-45's opposite). */
    @Test
    void anEmptySaveWritesNoFileAndNoFolder() {
        file("search").write(new Changes(false, null, "", null, null, null, null, null));

        assertThat(home.resolve("agents")).doesNotExist();
    }

    /** The file may carry a key, so it gets the mode the config file got (R-WP-16 "Rechte 600"). */
    @Test
    void aWrittenFileIsOwnerOnly() throws IOException {
        Assumptions.assumeTrue(home.getFileSystem().supportedFileAttributeViews().contains("posix"),
                "no posix view on this file system — the mode is the operating system's promise there");

        file("po").write(new Changes(false, null, null, "sk-secret-0123456789", null, null, null, null));

        assertThat(Files.getPosixFilePermissions(file("po").file()))
                .isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    /**
     * A save reads, mutates and rewrites the whole head, so two saves of one slot may not interleave —
     * both fields are in the file afterwards, in every round. Without the per-file lock the second writer
     * parses before the first has written, and one of the two fields is simply lost.
     */
    @Test
    // UC-WP-44
    void twoConcurrentSavesBothLeaveTheirField() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            race(
                    () -> file("dev").write(new Changes(false, "gpt-5", null, null, null, null, null, null)),
                    () -> file("dev").write(new Changes(false, null, null, null, "high", null, null, null)));

            Values values = file("dev").read();
            assertThat(values.model()).as("round " + round + " kept the model").isEqualTo("gpt-5");
            assertThat(values.think()).as("round " + round + " kept the think level").isEqualTo("high");
        }
    }

    /**
     * The other half of the same lock: a read arriving while the slot is written must never land in the
     * moment the file is truncated. An empty head answers "no override", and the turn of whoever loaded
     * it would run on the default config while the file says otherwise.
     */
    @Test
    // UC-WP-44
    void aSlotBeingWrittenAlwaysReadsAsAWholeHead() throws Exception {
        write("dev", "---\nmodel: gpt-5\n---\n");
        AtomicBoolean sawAnEmptyHead = new AtomicBoolean();

        for (int round = 0; round < ROUNDS; round++) {
            race(
                    () -> file("dev").write(new Changes(false, "gpt-4", null, null, null, null, null, null)),
                    () -> file("dev").write(new Changes(false, null, null, null, "high", null, null, null)),
                    () -> {
                        if (!file("dev").read().overridden()) {
                            sawAnEmptyHead.set(true);
                        }
                    });
        }

        assertThat(sawAnEmptyHead).isFalse();
    }

    private AgentConfigFile file(String slot) {
        return AgentConfigFile.forSlot(factoryHome, slot);
    }

    /**
     * Runs the tasks against one slot as close together as the machine allows — the interleaving is the
     * point, so they start on a barrier and the first failure surfaces as the test's own failure.
     */
    private static void race(Runnable... tasks) throws Exception {
        CyclicBarrier together = new CyclicBarrier(tasks.length);
        try (ExecutorService threads = Executors.newFixedThreadPool(tasks.length)) {
            var running = Arrays.stream(tasks).map(task -> threads.submit(() -> {
                try {
                    together.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                } catch (BrokenBarrierException unstarted) {
                    throw new IllegalStateException(unstarted);
                }
                task.run();
            })).toList();
            for (var done : running) {
                done.get(30, TimeUnit.SECONDS);
            }
        }
    }

    private String text(String slot) {
        try {
            return Files.readString(file(slot).file());
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private void write(String slot, String content) {
        try {
            Path file = file(slot).file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}

package org.sterl.open.aifactory.config.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

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
        file("dev").write(new Changes(false, "gpt-5", null, null, null, null));

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

        file("dev").write(new Changes(false, "", null, null, null, null));

        Values values = file("dev").read();
        assertThat(values.model()).isNull();
        assertThat(values.url()).isEqualTo("https://dev.example");
        assertThat(values.temperature()).isEqualTo("0.2");
        assertThat(text("dev")).doesNotContain("model").contains("url: https://dev.example");
    }

    /**
     * UC-WP-45: the unchecked box is not "five empty strings" — it reaches every config value of the file,
     * including keys this page never shows, and leaves the prompt body and the agent's own keys alone.
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
                ---
                """ + BODY);

        file("review").write(Changes.clearing());

        assertThat(file("review").read().overridden()).isFalse();
        String stored = text("review");
        assertThat(stored).doesNotContain("model", "url", "api_key", "think", "temperature");
        // the body and what another tool wrote stay verbatim; the file is only rewritten in its head
        assertThat(stored).contains("tools: read, grep").contains("This is the slot's own prompt.")
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

        file("compact").write(new Changes(false, "", null, null, null, null));

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
        file("search").write(new Changes(false, null, "", null, null, null));

        assertThat(home.resolve("agents")).doesNotExist();
    }

    /** The file may carry a key, so it gets the mode the config file got (R-WP-16 "Rechte 600"). */
    @Test
    void aWrittenFileIsOwnerOnly() throws IOException {
        Assumptions.assumeTrue(home.getFileSystem().supportedFileAttributeViews().contains("posix"),
                "no posix view on this file system — the mode is the operating system's promise there");

        file("po").write(new Changes(false, null, null, "sk-secret-0123456789", null, null));

        assertThat(Files.getPosixFilePermissions(file("po").file()))
                .isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    private AgentConfigFile file(String slot) {
        return AgentConfigFile.forSlot(factoryHome, slot);
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

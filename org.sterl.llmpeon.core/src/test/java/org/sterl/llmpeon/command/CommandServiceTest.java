package org.sterl.llmpeon.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.shared.model.SimplePromptFile;

class CommandServiceTest {

    @Test
    void emptyDirectoryReturnsZeroCommands(@TempDir Path tmp) throws Exception {
        // GIVEN
        var service = new CommandService();

        // WHEN
        service.refresh(tmp);

        // THEN
        assertThat(service.getCommands()).isEmpty();
        assertThat(service.loadedCommandCount()).isZero();
    }

    @Test
    void nonExistingDirectoryReturnsZeroCommands(@TempDir Path tmp) throws Exception {
        // GIVEN
        var service = new CommandService();

        // WHEN
        service.refresh(tmp.resolve("does-not-exist"));

        // THEN
        assertThat(service.getCommands()).isEmpty();
    }

    @Test
    void readsPlainMarkdownWithoutFrontmatter(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("review.md"),
                "Review the code carefully and report findings.");
        var service = new CommandService();

        // WHEN
        service.refresh(tmp);

        // THEN
        assertThat(service.getCommands()).hasSize(1);
        var cmd = service.getCommands().get(0);
        assertThat(cmd.name()).isEqualTo("review");
        assertThat(cmd.description()).isNull();
        assertThat(cmd.readFullContent()).contains("Review the code");
        assertThat(cmd.readBody()).isEqualTo(cmd.readFullContent());
    }

    @Test
    void parsesOptionalDescriptionFromFrontmatter(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("plan.md"), """
                ---
                description: Build a high-level implementation plan
                ---

                # Plan

                Body content here.
                """);
        var service = new CommandService();

        // WHEN
        service.refresh(tmp);

        // THEN
        var cmd = service.get("plan").orElseThrow();
        assertThat(cmd.name()).isEqualTo("plan");
        assertThat(cmd.description()).isEqualTo("Build a high-level implementation plan");
        assertThat(cmd.readBody().strip()).startsWith("# Plan");
    }

    @Test
    void readBodyStripsFrontmatterWhenPresent(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("doc.md"), """
                ---
                description: docs
                ---
                Hello body
                """);
        var service = new CommandService();

        // WHEN
        service.refresh(tmp);

        // THEN
        var cmd = service.get("doc").orElseThrow();
        assertThat(cmd.readBody().strip()).isEqualTo("Hello body");
    }

    @Test
    void caseInsensitiveLookup(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("Review.md"), "x");
        var service = new CommandService();

        // WHEN
        service.refresh(tmp);

        // THEN
        assertThat(service.get("review")).isPresent();
        assertThat(service.get("REVIEW")).isPresent();
    }

    @Test
    void ignoresHiddenAndNonMarkdownFiles(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve(".secret.md"), "x");
        Files.writeString(tmp.resolve("notes.txt"), "x");
        Files.writeString(tmp.resolve("ok.md"), "y");
        var service = new CommandService();

        // WHEN
        service.refresh(tmp);

        // THEN
        assertThat(service.getCommands()).hasSize(1);
        assertThat(service.get("ok")).isPresent();
    }

    @Test
    void ignoresFilesInSubdirectories(@TempDir Path tmp) throws Exception {
        // GIVEN
        var sub = Files.createDirectory(tmp.resolve("sub"));
        Files.writeString(sub.resolve("nested.md"), "x");
        Files.writeString(tmp.resolve("flat.md"), "y");
        var service = new CommandService();

        // WHEN
        service.refresh(tmp);

        // THEN
        assertThat(service.getCommands()).hasSize(1);
        assertThat(service.get("flat")).isPresent();
        assertThat(service.get("nested")).isEmpty();
    }

    @Test
    void refreshWithSameDirectoryReloadsContent(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("a.md"), "x");
        var service = new CommandService();
        service.refresh(tmp);
        assertThat(service.loadedCommandCount()).isEqualTo(1);

        // WHEN
        Files.writeString(tmp.resolve("b.md"), "y");
        service.refresh(tmp);

        // THEN
        assertThat(service.loadedCommandCount()).isEqualTo(2);
    }

    @Test
    void commandNamesAreSortedCaseInsensitive(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("zebra.md"), "x");
        Files.writeString(tmp.resolve("Alpha.md"), "x");
        Files.writeString(tmp.resolve("beta.md"), "x");
        var service = new CommandService();

        // WHEN
        service.refresh(tmp);

        // THEN
        assertThat(service.getCommands())
                .extracting(SimplePromptFile::name)
                .containsExactly("Alpha", "beta", "zebra");
    }

    @Test
    void clearingDirectoryRemovesAllCommands(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("a.md"), "x");
        var service = new CommandService();
        service.refresh(tmp);
        assertThat(service.loadedCommandCount()).isEqualTo(1);

        // WHEN
        service.refresh((String) null);

        // THEN
        assertThat(service.loadedCommandCount()).isZero();
    }
}
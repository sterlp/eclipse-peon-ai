package org.sterl.llmpeon.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.memory.StateMigration.MigrationResult;

class StateMigrationTest extends AbstractMemoryFileTest {

    private Path sourceDir;
    private Path targetDir;

    @BeforeEach
    void before() throws IOException {
        var root = Files.createDirectory(fs.getPath("/" + UUID.randomUUID()));
        sourceDir = Files.createDirectory(root.resolve("peon-state"));
        targetDir = root.resolve("metadata-state"); // not created yet — migration creates it
    }

    @Test
    void movesHistoryFileToTargetState() throws IOException {
        // GIVEN a legacy history file in the source
        Files.writeString(sourceDir.resolve("Dev-history.jsonl"), "line1");

        // WHEN
        var result = StateMigration.migrate(sourceDir, targetDir);

        // THEN the file lives in the target, the source file is gone, counted as moved
        var moved = targetDir.resolve("Dev-history.jsonl");
        assertThat(Files.isRegularFile(moved)).isTrue();
        assertThat(Files.readString(moved)).isEqualTo("line1");
        assertThat(Files.exists(sourceDir.resolve("Dev-history.jsonl"))).isFalse();
        assertThat(result.moved()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void secondRunIsNoOp() throws IOException {
        // GIVEN one file already migrated on the first run
        Files.writeString(sourceDir.resolve("Plan-history.jsonl"), "x");
        StateMigration.migrate(sourceDir, targetDir);

        // WHEN a second run happens
        var result = StateMigration.migrate(sourceDir, targetDir);

        // THEN nothing moves again (idempotent)
        assertThat(result.moved()).isZero();
        assertThat(result.isNoOp()).isTrue();
    }

    @Test
    void targetExistsSkipsAndKeepsSource() throws IOException {
        // GIVEN a source file AND an already-present target with the same name
        var src = sourceDir.resolve("Po-history.jsonl");
        Files.writeString(src, "legacy");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("Po-history.jsonl"), "current");

        // WHEN
        var result = StateMigration.migrate(sourceDir, targetDir);

        // THEN the target wins (unchanged), the source is kept, counted as skipped
        assertThat(Files.readString(targetDir.resolve("Po-history.jsonl"))).isEqualTo("current");
        assertThat(Files.isRegularFile(src)).isTrue();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.moved()).isZero();
        assertThat(result.messages()).anySatisfy(msg -> assertThat(msg).contains("Po-history.jsonl"));
    }

    @Test
    void missingSourceDirIsSilentNoOp() {
        // GIVEN the source directory does not exist
        var missing = fs.getPath("/" + UUID.randomUUID() + "/state");

        // WHEN / THEN no exception, nothing moved, source still absent
        var result = StateMigration.migrate(missing, targetDir);
        assertThat(result.moved()).isZero();
        assertThat(result.isNoOp()).isTrue();
        assertThat(Files.exists(missing)).isFalse();
    }

    @Test
    void emptySourceDirRemovedAfterMigration() throws IOException {
        // GIVEN a source dir with a single file
        Files.writeString(sourceDir.resolve("Search-history.jsonl"), "s");

        // WHEN
        StateMigration.migrate(sourceDir, targetDir);

        // THEN the file moved and the (now empty) source dir is removed
        assertThat(Files.isRegularFile(targetDir.resolve("Search-history.jsonl"))).isTrue();
        assertThat(Files.exists(sourceDir)).isFalse();
    }

    @Test
    void failingMoveIsLoggedNotThrown() throws IOException {
        // GIVEN two legacy files and a mover that fails only for the "a-" file
        Files.writeString(sourceDir.resolve("a-history.jsonl"), "a");
        Files.writeString(sourceDir.resolve("b-history.jsonl"), "b");

        // WHEN the migration runs with a mover that throws for "a-" (must not propagate)
        MigrationResult result = StateMigration.migrate(sourceDir, targetDir, (source, target) -> {
            if (source.getFileName().toString().startsWith("a-")) {
                throw new IOException("simulated IO failure");
            }
            return Files.move(source, target);
        });

        // THEN the failed file stays + is counted; the other file still migrates
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.moved()).isEqualTo(1);
        assertThat(Files.isRegularFile(sourceDir.resolve("a-history.jsonl"))).isTrue();  // kept
        assertThat(Files.isRegularFile(targetDir.resolve("b-history.jsonl"))).isTrue(); // moved
        assertThat(result.messages()).anySatisfy(msg -> assertThat(msg).contains("a-history.jsonl"));
    }
}

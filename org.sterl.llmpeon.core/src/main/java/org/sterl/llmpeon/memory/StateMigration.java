package org.sterl.llmpeon.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * One-shot migration of legacy runtime state out of the shared {@code ~/.peon/state} directory
 * into a workspace-scoped target (ADR-0041 R3).
 *
 * <p>Every entry in {@code sourceDir} is moved into {@code targetDir}. An entry whose target
 * already exists is skipped (source kept, target wins) so a partial migration or a conflict never
 * loses data. Per-entry failures are counted and logged — the migration never throws and never
 * blocks the start. Once empty, {@code sourceDir} is removed so {@code .peon} is truly
 * config-only afterwards. Idempotent: a second run finds a missing/empty source and is a no-op.
 */
public final class StateMigration {

    private static final Logger LOG = Logger.getLogger(StateMigration.class.getName());

    private StateMigration() {
    }

    /**
     * Migrates every file in {@code sourceDir} into {@code targetDir} using {@link Files#move}.
     *
     * @param sourceDir legacy state directory (e.g. {@code ~/.peon/state}); may be absent
     * @param targetDir workspace-scoped state directory (created if needed)
     * @return the outcome (moved/skipped/failed counts + per-entry messages); never null
     */
    public static MigrationResult migrate(Path sourceDir, Path targetDir) {
        return migrate(sourceDir, targetDir, Files::move);
    }

    static MigrationResult migrate(Path sourceDir, Path targetDir, Mover mover) {
        // GIVEN sourceDir does not exist → silent No-Op (nothing to migrate)
        if (!Files.isDirectory(sourceDir)) {
            LOG.fine("State migration skipped — source not a directory: " + sourceDir);
            return MigrationResult.noOp();
        }

        List<Path> entries;
        try (Stream<Path> stream = Files.list(sourceDir)) {
            entries = stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        } catch (IOException e) {
            LOG.warning("State migration aborted — cannot list " + sourceDir + ": " + e.getMessage());
            return MigrationResult.noOp();
        }

        if (!entries.isEmpty()) {
            try {
                Files.createDirectories(targetDir);
            } catch (IOException e) {
                LOG.warning("State migration aborted — cannot create target " + targetDir + ": " + e.getMessage());
                return MigrationResult.noOp();
            }
        }

        List<String> messages = new ArrayList<>();
        int moved = 0, skipped = 0, failed = 0;
        for (Path source : entries) {
            String name = source.getFileName().toString();
            Path target = targetDir.resolve(name);
            // GIVEN target already exists → Skip (source kept, target wins, no silent data loss)
            if (Files.exists(target)) {
                skipped++;
                messages.add("Skipped migration of " + name + " — target already exists");
                LOG.warning("State migration of " + name + " skipped — target already exists: " + target);
                continue;
            }
            try {
                mover.move(source, target);
                moved++;
            } catch (Exception e) {
                failed++;
                messages.add("Failed to migrate " + name + " — " + e.getMessage());
                LOG.warning("State migration of " + name + " failed: " + e.getMessage());
            }
        }

        removeIfEmpty(sourceDir);
        LOG.info("State migration " + sourceDir + " → " + targetDir + ": moved=" + moved
                + ", skipped=" + skipped + ", failed=" + failed);
        return new MigrationResult(moved, skipped, failed, messages);
    }

    private static void removeIfEmpty(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            if (stream.findFirst().isEmpty()) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException ignored) {
            // best-effort — leaving an empty directory behind is harmless
        }
    }

    /**
     * Moves one source entry to its target. {@link Files#move} without {@code ATOMIC_MOVE} so the
     * JDK itself performs the cross-volume fallback (copy + delete).
     */
    interface Mover {
        Path move(Path source, Path target) throws IOException;
    }

    /**
     * Outcome of a one-shot state migration.
     *
     * @param moved    entries moved into the target
     * @param skipped  entries skipped because the target already existed (source kept)
     * @param failed   entries whose move threw (logged, never propagated)
     * @param messages per-entry skip/failure messages
     */
    public record MigrationResult(int moved, int skipped, int failed, List<String> messages) {

        public static MigrationResult noOp() {
            return new MigrationResult(0, 0, 0, List.of());
        }

        public boolean isNoOp() {
            return moved == 0 && skipped == 0 && failed == 0;
        }
    }
}

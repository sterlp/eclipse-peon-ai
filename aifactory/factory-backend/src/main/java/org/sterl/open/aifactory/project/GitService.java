package org.sterl.open.aifactory.project;

import java.nio.file.Path;

/**
 * The factory's git access. Kept as a seam so the registry logic is testable without git and the
 * clone tests can drive the very same {@link CommandRunner} path a real clone uses.
 */
public interface GitService {

    /**
     * Clones {@code gitUrl} into {@code target} (D12 — synchronous, the caller only stores the
     * project after this returned).
     *
     * @return {@code true} when a fresh clone was created, {@code false} when {@code target} already
     *         held a clone and was reused untouched (R-WP-3)
     * @throws CloneFailedException when git failed; {@code target} is left non-existent
     * @throws IllegalStateException when {@code target} exists but is not a git clone
     */
    boolean clone(String gitUrl, Path target);
}

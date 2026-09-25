package org.sterl.llmpeon;

import org.sterl.llmpeon.compact.CompactResult;

/**
 * Shared test fixtures across the core test tree.
 */
public final class CoreTestFixtures {

    /** A COMPACTED result with neutral stats — for stubs that only need the status. */
    public static CompactResult compactedResult() {
        return CompactResult.compacted(new CompactResult.Stats(0, 0, 0, CompactResult.Stage.NONE, 0, 0, null, 0L));
    }

    private CoreTestFixtures() {}
}

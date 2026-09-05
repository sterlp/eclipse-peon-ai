package org.sterl.llmpeon.shared;

import java.util.function.Predicate;

/**
 * Resolves a collision-free file name for plan archives (R-PI1).
 * <p>
 * Given a stem (e.g. {@code overview-done-2026-09-05-12-59}), returns the first free name
 * by appending a counter suffix ({@code -1}, {@code -2}, …) until a non-existing name is found.
 * Never throws — the returned name is guaranteed free per the {@code exists} predicate.
 */
public final class ArchiveName {

    private ArchiveName() {
    }

    /**
     * @param stem   the base name without extension, e.g. {@code overview-done-2026-09-05-12-59}
     * @param exists predicate that tests whether a candidate file name (with {@code .md}) already exists
     * @return the first free name, e.g. {@code stem.md} or {@code stem-1.md}
     */
    public static String firstFreeName(String stem, Predicate<String> exists) {
        String candidate = stem + ".md";
        for (int counter = 1; exists.test(candidate); counter++) {
            candidate = stem + "-" + counter + ".md";
        }
        return candidate;
    }
}

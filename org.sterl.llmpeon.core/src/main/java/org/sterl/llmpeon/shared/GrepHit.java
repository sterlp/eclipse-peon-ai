package org.sterl.llmpeon.shared;

import java.util.List;

/** One grep match: the file, the 1-based line, and the raw line text. */
public record GrepHit(String file, int line, String text) {

    /** Number of distinct files represented in {@code hits}. */
    public static int fileCount(List<GrepHit> hits) {
        return (int) hits.stream().map(GrepHit::file).distinct().count();
    }
}

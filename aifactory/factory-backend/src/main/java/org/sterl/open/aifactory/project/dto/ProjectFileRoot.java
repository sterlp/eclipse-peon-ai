package org.sterl.open.aifactory.project.dto;

import java.nio.file.Path;

import org.sterl.open.aifactory.shared.TmpRoots;

/**
 * The two read roots of the file area (R-WP-14) and nothing else: the project checkout and the system
 * temp dir — the same pair the write pinning of R-WP-6 grants an agent, so the area never shows more
 * than Jon could write. An unknown value is rejected, not defaulted: a typo in {@code root} that
 * silently listed the checkout would answer a request the caller never sent.
 */
public enum ProjectFileRoot {

    CHECKOUT("checkout"),
    TMP("tmp");

    private final String wireName;

    ProjectFileRoot(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static ProjectFileRoot from(String value) {
        String wanted = value == null ? "" : value.trim();
        for (ProjectFileRoot candidate : values()) {
            if (candidate.wireName.equalsIgnoreCase(wanted)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown file root \"" + value + "\" — use checkout or tmp");
    }

    /** The directory behind this root for one project. */
    public Path resolve(Path checkout) {
        return this == CHECKOUT ? checkout : TmpRoots.systemTmp();
    }
}

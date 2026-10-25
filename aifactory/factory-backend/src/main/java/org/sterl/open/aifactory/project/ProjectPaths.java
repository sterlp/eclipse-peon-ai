package org.sterl.open.aifactory.project;

import java.nio.file.Path;

/**
 * The one containment rule of this component — R-WP-19 / UC-WP-29: a checkout lives below
 * {@code <home>/projects} and nowhere else, so a path read from the registry file is never a
 * deletion mandate.
 *
 * <p>It lives here and not in {@code shared} because it knows the projects dir, which is component
 * knowledge ({@code shared} knows JDK and framework only —
 * {@code aifactory/docs/aifactory-architektur.md}).</p>
 */
final class ProjectPaths {

    private ProjectPaths() {
    }

    /** A checkout is always {@code <home>/projects/<name>} — never that folder itself, never anything above. */
    static Path requireInsideProjectsDir(Path projectsDir, Path target) {
        Path root = projectsDir.toAbsolutePath().normalize();
        Path absolute = target.toAbsolutePath().normalize();
        if (!absolute.startsWith(root) || absolute.equals(root)) {
            throw new IllegalStateException("refusing to touch " + absolute
                    + " — checkouts live below " + root);
        }
        return absolute;
    }
}

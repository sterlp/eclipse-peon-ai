package org.sterl.open.aifactory.project;

import java.nio.file.Path;

/**
 * One project of the factory: a git checkout below the factory home plus the URL it came from.
 *
 * <p>{@code id} is derived from the normalized URL (slug + hash), so the same repository added
 * again resolves to the same entry (R-WP-3). {@code localPath} is absolute at runtime; the registry
 * file stores it relative to the home — see {@link StoredProject}.</p>
 */
public record Project(String id, String name, String gitUrl, Path localPath, ProjectState state) {
}

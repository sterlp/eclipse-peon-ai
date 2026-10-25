package org.sterl.open.aifactory.project;

/**
 * What {@link ProjectService#add(String)} did. Registering an already known normalized url is not an
 * error: the existing project comes back with {@code created = false} and git is never called again
 * (R-WP-2 / UC-WP-3), which is what lets the facade answer {@code 200} instead of {@code 201}.
 */
public record AddProjectResult(Project project, boolean created) {
}

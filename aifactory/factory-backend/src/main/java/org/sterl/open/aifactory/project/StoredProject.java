package org.sterl.open.aifactory.project;

import java.nio.file.Path;

/**
 * Registry wire format. Kept separate from {@link Project} so {@code localPath} can be stored
 * <em>relative</em> to the factory home — an absolute path there would break as soon as the home is
 * moved, and a {@link Path} field would make Jackson resolve it against the working dir.
 *
 * <p>Reading is a trust check, not a lookup: the file is user-editable runtime state, so an absolute
 * path or one leaving {@code <home>/projects} is refused (R-WP-19 / UC-WP-29). Consequence: a
 * hand-poisoned registry answers {@code 409} on <em>every</em> project call until the file is fixed —
 * an entry is never silently dropped from the list, because a row missing without a message is the
 * failure nobody would notice.</p>
 */
record StoredProject(String id, String name, String gitUrl, String localPath, ProjectState state) {

    static StoredProject of(Project project, Path homeRoot) {
        return new StoredProject(project.id(), project.name(), project.gitUrl(),
                homeRoot.relativize(project.localPath().toAbsolutePath().normalize()).toString(),
                project.state());
    }

    Project toProject(Path homeRoot, Path projectsDir) {
        Path stored = Path.of(localPath);
        if (stored.isAbsolute()) {
            throw new IllegalStateException("project \"" + id + "\" stores the absolute path \""
                    + localPath + "\" — checkouts are stored relative to " + homeRoot);
        }
        return new Project(id, name, gitUrl, contain(homeRoot.resolve(stored), projectsDir), state);
    }

    private Path contain(Path resolved, Path projectsDir) {
        try {
            return ProjectPaths.requireInsideProjectsDir(projectsDir, resolved);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("project \"" + id + "\": " + e.getMessage(), e);
        }
    }
}

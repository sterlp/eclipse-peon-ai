package org.sterl.open.aifactory.project;

import java.nio.file.Path;

/**
 * Registry wire format. Kept separate from {@link Project} so {@code localPath} can be stored
 * <em>relative</em> to the factory home — an absolute path there would break as soon as the home is
 * moved, and a {@link Path} field would make Jackson resolve it against the working dir.
 */
record StoredProject(String id, String name, String gitUrl, String localPath, ProjectState state) {

    static StoredProject of(Project project, Path homeRoot) {
        return new StoredProject(project.id(), project.name(), project.gitUrl(),
                homeRoot.relativize(project.localPath().toAbsolutePath().normalize()).toString(),
                project.state());
    }

    Project toProject(Path homeRoot) {
        Path stored = Path.of(localPath);
        Path absolute = stored.isAbsolute() ? stored.normalize()
                : homeRoot.resolve(stored).normalize();
        return new Project(id, name, gitUrl, absolute, state);
    }
}

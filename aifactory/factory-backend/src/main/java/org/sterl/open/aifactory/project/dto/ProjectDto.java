package org.sterl.open.aifactory.project.dto;

import org.sterl.open.aifactory.project.Project;

/** Wire shape of a registered project. */
public record ProjectDto(String id, String name, String gitUrl, String localPath, String state) {

    public static ProjectDto of(Project project) {
        return new ProjectDto(project.id(), project.name(), project.gitUrl(),
                project.localPath().toString(), project.state().name());
    }
}

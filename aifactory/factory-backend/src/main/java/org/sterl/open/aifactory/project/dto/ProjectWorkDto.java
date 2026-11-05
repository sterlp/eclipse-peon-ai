package org.sterl.open.aifactory.project.dto;

import org.sterl.open.aifactory.project.ProjectWork;

/**
 * Wire shape of one project's work state (R-WP-24). The id is carried with every delta because the work
 * states of all projects share one connection.
 */
public record ProjectWorkDto(String projectId, boolean working, int queued) {

    public static ProjectWorkDto of(ProjectWork work) {
        return new ProjectWorkDto(work.projectId(), work.working(), work.queued());
    }
}

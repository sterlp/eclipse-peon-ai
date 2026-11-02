package org.sterl.open.aifactory.chat;

import java.util.List;

import org.springframework.stereotype.Component;

import org.sterl.open.aifactory.project.Project;
import org.sterl.open.aifactory.project.ProjectService;
import org.sterl.open.aifactory.project.ProjectWork;
import org.sterl.open.aifactory.project.ProjectWorkStates;

/**
 * The work states {@code project} shows (D-6): the registry says which projects there are, a runtime of
 * this component says what one of them is doing.
 *
 * <p>An entry without a runtime answers {@link ProjectWork#free(String)} and is <b>not</b> asked for one:
 * building a runtime means Jon and his team and their memory, and the project list is opened to look at
 * the registry, not to start conversations (UC-WP-47).</p>
 */
@Component
class ChatProjectWorkStates implements ProjectWorkStates {

    private final ProjectService projects;
    private final ProjectRuntimeRegistry runtimes;

    ChatProjectWorkStates(ProjectService projects, ProjectRuntimeRegistry runtimes) {
        this.projects = projects;
        this.runtimes = runtimes;
    }

    @Override
    public List<ProjectWork> current() {
        return projects.projects().stream()
                .map(this::stateOf)
                .toList();
    }

    private ProjectWork stateOf(Project project) {
        ProjectRuntime runtime = runtimes.peek(project.id());
        return runtime == null ? ProjectWork.free(project.id()) : runtime.workState();
    }
}

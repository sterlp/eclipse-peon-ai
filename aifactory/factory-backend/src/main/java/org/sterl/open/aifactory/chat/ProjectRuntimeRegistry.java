package org.sterl.open.aifactory.chat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.sterl.open.aifactory.config.FactoryConfigService;
import org.sterl.open.aifactory.project.ProjectNotFoundException;
import org.sterl.open.aifactory.project.ProjectRemoved;
import org.sterl.open.aifactory.project.ProjectService;
import org.sterl.open.aifactory.project.Project;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The lifecycle of the runtimes: one per project, created on demand, and the one place allowed to drop
 * one. It knows nothing about a turn — who is building Jon is a different question than how a turn runs
 * (§3.3.1), so a change to the turn mechanics never touches this class.
 *
 * <p>Every entry point answers for an unknown project with {@link ProjectNotFoundException} — the 404
 * half of all three edges, and the shared advice of {@code project} answers it.</p>
 */
@Component
public class ProjectRuntimeRegistry {

    private final ProjectService projects;
    private final FactoryConfigService config;
    private final FactoryHome home;
    private final ChatStreamRegistry streams;

    private final Map<String, ProjectRuntime> runtimes = new ConcurrentHashMap<>();

    /** One virtual thread per turn — a turn is a blocking model call, not a pooled worker. */
    private final ExecutorService turns = Executors.newVirtualThreadPerTaskExecutor();

    public ProjectRuntimeRegistry(ProjectService projects, FactoryConfigService config, FactoryHome home,
            ChatStreamRegistry streams) {
        this.projects = projects;
        this.config = config;
        this.home = home;
        this.streams = streams;
    }

    /**
     * The listener is registered for the project, not for a turn: the turn belongs to the project, so a
     * client may connect before, during, or never (R-WP-5).
     */
    public SseEmitter subscribe(String projectId) {
        requireProject(projectId);
        return streams.subscribe(projectId);
    }

    /**
     * Fire and forget — the caller's thread is never used for the turn, which is what lets the POST answer
     * 202 while Jon is still working, and what lets a second input reach the queue of the same Jon (D2).
     */
    public void submit(String projectId, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        runtime(projectId).submit(text);
    }

    public List<ChatLine> history(String projectId) {
        return runtime(projectId).history();
    }

    /**
     * A removed project loses its runtime and its listeners: an agent of a project that no longer exists
     * would keep his memory and his queue alive for nothing, and a stream of a gone project would keep
     * publishing. Only the registration goes — {@code state/<id>} stays, because R-WP-19 names exactly two
     * things a removal deletes, and the chat of a project added again is still that one project's chat
     * (R-WP-11).
     */
    @EventListener
    void onProjectRemoved(ProjectRemoved removed) {
        runtimes.remove(removed.projectId());
        streams.closeProject(removed.projectId());
    }

    /**
     * The registry owns the executor, so it closes it. On the same event as {@code ChatStreamRegistry}
     * (see the note there about {@code @PreDestroy} being too late): a turn that is still running when
     * the factory stops is cut short, not waited for — there is nobody left to read its answer.
     */
    @EventListener(ContextClosedEvent.class)
    void shutdownTurns() {
        turns.shutdownNow();
    }

    /** The runtime of one project, built on first use; for tests and the team view of Inc 5. */
    ProjectRuntime runtime(String projectId) {
        ProjectRuntime existing = runtimes.get(projectId);
        if (existing != null) {
            return existing;
        }
        Project project = requireProject(projectId);
        // built outside the map, never in computeIfAbsent: building an agent must not hold a map bin, and
        // the first writer wins — two concurrent POSTs must not get two Jons on one history
        ProjectRuntime built = new ProjectRuntime(projectId, project.localPath(), home.stateDir(projectId),
                config::load, config::loadForTurn, streams, turns);
        ProjectRuntime raced = runtimes.putIfAbsent(projectId, built);
        return raced != null ? raced : built;
    }

    int runtimeCount() {
        return runtimes.size();
    }

    private Project requireProject(String projectId) {
        return projects.find(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
    }
}

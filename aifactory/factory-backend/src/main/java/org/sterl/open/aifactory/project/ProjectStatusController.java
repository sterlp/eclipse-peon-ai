package org.sterl.open.aifactory.project;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The work states of every project over one connection (R-WP-24 / UC-WP-47): the event {@code projects}
 * carries the whole snapshot — {@code {"projects":[{projectId, working, queued}, …]}} — and every change
 * after it arrives as {@code project} with one such object. Nothing here is remembered: a viewer that
 * connects later simply gets the snapshot of that moment.
 *
 * <p>The edge belongs to {@code project} and not to {@code chat} (D-6): the list of projects is what
 * shows the state, and a second route in {@code chat} would be a second owner of the conversation. What
 * runs is read through {@link ProjectWorkStates}.</p>
 *
 * <p>Free because it is the only path of its kind: {@code GET /api/projects/{id}} does not exist, so no
 * id can be read out of {@code events}.</p>
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectStatusController {

    private final ProjectWorkStates workStates;
    private final ProjectStatusStream stream;

    public ProjectStatusController(ProjectWorkStates workStates, ProjectStatusStream stream) {
        this.workStates = workStates;
        this.stream = stream;
    }

    /**
     * The snapshot is read <b>before</b> the viewer is registered, so a change that lands during the read
     * is delivered after it instead of being replaced by an older picture.
     *
     * <p>{@code no-cache} and {@code X-Accel-Buffering: no} for the same reason as the chat's wire: a
     * proxy that holds the stream back hides a state the user is waiting for.</p>
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(HttpServletResponse response) {
        List<ProjectWork> snapshot = workStates.current();
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return stream.subscribe(snapshot);
    }
}

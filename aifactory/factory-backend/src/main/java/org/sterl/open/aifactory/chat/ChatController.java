package org.sterl.open.aifactory.chat;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The three HTTP edges of the chat — the whole API of this component, and the only class here that knows
 * HTTP and JSON. There is no fourth edge and no second way to start a conversation (R-WP-11, UC-WP-16).
 *
 * <p>It answers nothing about the turn: a POST is accepted and the work happens on the project's runtime,
 * so a browser that goes away takes no turn with it (architecture doc "prüft nichts über den Turn
 * hinaus"). Errors therefore arrive as live events, not as status codes — a blank input is the one
 * exception, because it never was a turn.</p>
 */
@RestController
public class ChatController {

    private final ProjectRuntimeRegistry runtimes;

    public ChatController(ProjectRuntimeRegistry runtimes) {
        this.runtimes = runtimes;
    }

    /** The conversation as the core's memory has it — the single truth, no live line in it (D4). */
    @GetMapping("/api/projects/{id}/messages")
    public List<ChatMessageDto> messages(@PathVariable("id") String projectId) {
        return runtimes.history(projectId).stream()
                .map(line -> new ChatMessageDto(line.role(), line.text()))
                .toList();
    }

    /**
     * Always 202 — also when Jon is busy, because his queue takes the input (D2). A turn that cannot
     * start for a missing setting is answered here as accepted and fails as an {@code error} event (Q1).
     */
    @PostMapping("/api/projects/{id}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@PathVariable("id") String projectId, @RequestBody SendMessageRequest request) {
        runtimes.submit(projectId, request == null ? null : request.text());
    }

    /**
     * The live wire. The project is checked <b>before</b> the emitter exists, so an unknown project gets a
     * plain 404 instead of an event stream that never says anything.
     *
     * <p>{@code no-cache} and {@code X-Accel-Buffering: no} keep a proxy from holding back a partial
     * line — the point of this edge is that the text grows while Jon writes it (R-WP-5).</p>
     */
    @GetMapping(value = "/api/projects/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable("id") String projectId, HttpServletResponse response) {
        SseEmitter emitter = runtimes.subscribe(projectId);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return emitter;
    }
}

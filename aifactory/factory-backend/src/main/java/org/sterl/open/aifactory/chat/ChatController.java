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

import org.sterl.open.aifactory.chat.dto.AgentAction;
import org.sterl.open.aifactory.chat.dto.AgentActionRequest;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The HTTP edges of the chat, and the only class here that knows HTTP and JSON. <b>Three conversation
 * edges</b> — {@code GET/POST …/messages} and {@code GET …/events} — and the <b>tile family</b>
 * {@code GET/POST …/agents} (architecture doc "Die drei Gesprächs-Kanten bleiben drei").
 *
 * <p>The family is deliberately narrower than it looks: it addresses a tile of this project's own team,
 * it starts neither a turn nor a second conversation, and it can neither create nor select an agent —
 * which is why there is still no second way to open a conversation (R-WP-11, UC-WP-16).</p>
 *
 * <p>It answers nothing about the turn: a POST is accepted and the work happens on the project's runtime,
 * so a browser that goes away takes no turn with it (architecture doc "prüft nichts über den Turn
 * hinaus"). Errors therefore arrive as live events, not as status codes — a blank input is the one
 * exception, because it never was a turn. A tile action is the other exception: a name that is no tile
 * and a busy project are answered as 400 and 409 before anything runs.</p>
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
                .map(line -> new ChatMessageDto(line.role(), line.text(), line.origin()))
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

    /**
     * The team's tiles — a snapshot of live state, read again on every mount and reconnect. Never stored,
     * never a history (R-WP-7, R-WP-11).
     */
    @GetMapping("/api/projects/{id}/agents")
    public List<AgentTileDto> agents(@PathVariable("id") String projectId) {
        return runtimes.agents(projectId);
    }

    /**
     * Clear or Compact one tile: 202 means it was taken, not done — the tiles and the chat are re-read
     * afterwards, which is also how a clear of Jon rebuilds the conversation (UC-WP-12, UC-WP-13).
     * 400 for a name that is no tile or an action it does not offer, 409 while a turn or another action
     * runs; nothing is queued.
     */
    @PostMapping("/api/projects/{id}/agents")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void act(@PathVariable("id") String projectId, @RequestBody AgentActionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("name and action are required");
        }
        runtimes.act(projectId, request.name(), AgentAction.from(request.action()));
    }
}

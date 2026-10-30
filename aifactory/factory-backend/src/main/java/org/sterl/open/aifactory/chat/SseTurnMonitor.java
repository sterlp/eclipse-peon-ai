package org.sterl.open.aifactory.chat;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.tool.model.SimpleMessage;

/**
 * The one place that knows the core's callback language: every {@link AiMonitor} callback of a project
 * becomes a {@link ChatEvent} here, and nothing else. One instance per project, never per turn — the
 * queue drain re-uses the agent's monitor, so a per-turn monitor would lose the streaming events of the
 * second input.
 *
 * <p><b>What reaches the browser</b> (R-WP-4): the core stamps every callback with the agent that produced
 * it, and this class decides who is <em>shown</em> — the lines stamped with the conversation's own agent,
 * plus the lines carrying no stamp. A team member's thinking, tool lines and half answers are its
 * conversation with itself and are dropped here <b>by their stamp</b>, while the delegation's own trace
 * lines stay because Jon's tool emitted them under Jon's name. The rule is policy and therefore lives in
 * the factory, not in the core: the Eclipse plugin hands the same callback stream to its views and goes on
 * showing every member line.</p>
 *
 * <p><b>The member's final answer is not a live line.</b> It has no callback of its own — it is the
 * result of Jon's delegation tool, and the browser reads it as a history line when {@code done} makes it
 * read the history (ADR-0078 Decision 6). So the two channels are disjoint by construction: live is what
 * the conversation's own agent streamed, finished text of anybody is the history's and only the
 * history's.</p>
 *
 * <p><b>Where {@code done} comes from</b>: not here — {@link ProjectRuntime#submit} measures the turn it
 * started and answers it. A callback is the wrong place for the end of a turn: a member's completion is
 * announced to the wrapper chain and stops in the innermost wrapper, so nothing that arrives here says
 * when the caller's turn was over.</p>
 *
 * <p>What is deliberately not forwarded: the conversation agent's own finished answer text. It arrives in
 * the history, and a second copy on the wire would be a second truth (R-WP-11). {@code START}/{@code END}
 * carry no value, {@code TOOL} chunks duplicate the {@code tool} line that {@link #onChatResponse}
 * already sends.</p>
 */
class SseTurnMonitor implements AiMonitor {

    private final String projectId;
    private final ChatStreamRegistry streams;
    private final AgentNames names;

    SseTurnMonitor(String projectId, ChatStreamRegistry streams, AgentNames names) {
        this.projectId = projectId;
        this.streams = streams;
        this.names = names;
    }

    @Override
    public void onStreamingChunk(OnPartialAiResponse chunk) {
        if (!isJon(chunk.origin())) {
            return;
        }
        switch (chunk.type()) {
            case THINK -> streams.publish(projectId, new ChatEvent.Partial("think", chunk.value()));
            case ANSWER -> streams.publish(projectId, new ChatEvent.Partial("answer", chunk.value()));
            case START, TOOL, END -> {
                // no value to show — see the class doc
            }
        }
    }

    @Override
    public void onChatResponse(SimpleMessage message) {
        if (!isJon(message.origin())) {
            return;
        }
        if (message.role() == SimpleMessage.Type.TOOL || message.role() == SimpleMessage.Type.PROBLEM) {
            streams.publish(projectId, new ChatEvent.ToolLine(message.message()));
        }
    }

    /**
     * The stamp is the whole decision: shown is what the conversation's own agent said, and an origin that
     * is not his never becomes one of his lines — neither a member's nor a stranger's. No book over open
     * delegations, no tool-call id, nothing remembered from the line before (ADR-0078 Decision 6), so two
     * members delegating in one {@code AiMessage} cannot be told apart by this class and do not have to be.
     *
     * <p>An unstamped line stays visible instead of vanishing: it names no author, so showing it attributes
     * nothing, while dropping it would hide a fact the user should have seen (R-WP-4). Every line of an
     * agent turn carries its stamp, so this branch is the honest fallback and not the rule.</p>
     */
    private boolean isJon(String origin) {
        return origin == null || names.isJon(origin);
    }
}

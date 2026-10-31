package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.junit.Test;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.tools.AskUserTool;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.LoopOwner;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

/**
 * Rule 12 (org.sterl.llmpeon.core/docs/queued-user-messages.md): the {@code askUser} answer carries
 * every queued entry of the running turn with it — ONE tool result, block prepended, the answer as
 * the last line. Headless: a fake presenter answers straight away, {@link AiPoAgent} is the real
 * queue owner.
 *
 * <p>Scope note: this fragment has no clock seam into the agent's queue, so the assertions pin the
 * composition and the <b>format</b> of the queue time; the exact {@code (queued 14:32)} value and the
 * cross-lane order of three entries are pinned in core (AbstractAgentTest#
 * takeAllMessagesDrainsBothLanesInInsertionOrder).</p>
 */
public class AskUserToolQueuedMessagesTest extends AbstractUnitTest {

    private static final String LN = TaggedResult.LN;
    private static final String HEAD = TaggedResult.PREFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE;
    private static final String FOOT = TaggedResult.SUFFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE;

    private final List<String> toolLines = new ArrayList<>();
    private final AiMonitor monitor = new AiMonitor() {
        @Override public void onChatResponse(SimpleMessage m) { if (m.role() == Type.TOOL) toolLines.add(m.message()); }
    };

    /** The UI: answers straight away — with {@link AskUserTool#CANCEL} for the Stop-button path. */
    private record FakePresenter(String answer) implements AskUserTool.QuestionPresenter {
        @Override public void show(String question, List<String> answers, Consumer<String> onAnswer) {
            onAnswer.accept(answer);
        }
    }

    // R12: alle Queue-Einträge reisen mit der Antwort, die Antwort bleibt die letzte Zeile
    @Test
    public void askUserCarriesAllQueuedMessagesBeforeTheAnswer() {
        // GIVEN — a normal and an urgent entry waiting, the user answering "yes"
        var agent = agent();
        agent.queueMessage("normale nachricht");
        agent.queueUrgentMessage("bitte abbrechen");
        var tool = new AskUserTool(new FakePresenter("yes"));
        tool.withToolRequest(request(agent));

        // WHEN
        String result = tool.askUser("Weiter mit Plan B?", List.of("yes", "no"));

        // THEN — one block carrying BOTH lanes, head first, one timed line per entry …
        assertTrue("block must be prepended:\n" + result, result.startsWith(HEAD + LN));
        assertTrue("timed line for the normal lane:\n" + result, hasTimedLine(result, "normale nachricht"));
        assertTrue("timed line for the urgent lane:\n" + result, hasTimedLine(result, "bitte abbrechen"));
        // AND — the answer is the LAST line, so the LLM reads it as its answer and not as noise …
        assertTrue("answer must close the result:\n" + result, result.endsWith(LN + "yes"));
        assertTrue("block foot must sit directly above it:\n" + result, result.contains(FOOT + LN + "yes"));
        // AND — the whole payload is ONE text: the render/compact path reads tool results via
        // text(), which throws for multi-content (ChatMessageUtil#extractMessages) …
        String rendered = ChatMessageUtil.toString(toolResult(result));
        assertTrue("rendered payload:\n" + rendered, rendered.contains(HEAD)
                && rendered.contains("bitte abbrechen") && rendered.endsWith("yes" + LN));
        // AND — consumed finally: nothing is left that could start a follow-up turn …
        assertEquals(0, agent.getQueuedMessageCount());
        // AND — and every delivery was disclosed once, with its queue time (Rule 9).
        assertEquals(toolLines.toString(), 2, toolLines.size());
        assertTrue("disclosure lines: " + toolLines,
                toolLines.get(0).startsWith("Reading queued User message: normale nachricht (queued ")
                && toolLines.get(1).startsWith("Reading queued User message: bitte abbrechen (queued "));
    }

    // R12 Leere Queue: unverändertes Verhalten — nur die Antwort
    @Test
    public void emptyQueueKeepsThePlainAnswer() {
        // GIVEN — a question, nothing queued
        var agent = agent();
        var tool = new AskUserTool(new FakePresenter("yes"));
        tool.withToolRequest(request(agent));

        // WHEN
        String result = tool.askUser("Weiter mit Plan B?", List.of());

        // THEN — plain answer: no block, no disclosure line for a delivery that never happened
        assertEquals("yes", result);
        assertEquals(toolLines.toString(), List.of(), toolLines);
    }

    // R12 Cancel: Regel 4 bleibt zuständig — die Einträge überleben den Abbruch
    @Test
    public void canceledQuestionKeepsTheQueueForTheAbortDrain() {
        // GIVEN — entries waiting, and the Stop button answering as UserQuestionResponseWidget#cancel does
        var agent = agent();
        agent.queueMessage("normale nachricht");
        agent.queueUrgentMessage("bitte abbrechen");
        var tool = new AskUserTool(new FakePresenter(AskUserTool.CANCEL));
        tool.withToolRequest(request(agent));

        // WHEN
        try {
            tool.askUser("Weiter mit Plan B?", List.of());
            fail("a canceled question must surface as CancellationException");
        } catch (CancellationException expected) {
            // THEN — no Huckepack and no loss: rule 4's abort drain still finds both entries …
            assertEquals(2, agent.getQueuedMessageCount());
            String drained = agent.drainQueue();
            assertNotNull("the abort drain must still have a payload", drained);
            assertTrue(drained, drained.contains("normale nachricht") && drained.contains("bitte abbrechen"));
        }
        // AND — nothing was disclosed, because nothing was delivered.
        assertEquals(toolLines.toString(), List.of(), toolLines);
    }

    // R12 Grenze: ein agent-loser Loop hat keine Queue — die Einträge bleiben dem Parent erhalten
    @Test
    public void agentLessLoopKeepsThePlainAnswerAndTheQueue() {
        // GIVEN — a search-style sub-loop: it names an owner but has no agent (LoopOwner.named)
        var agent = agent();
        agent.queueUrgentMessage("bitte abbrechen");
        var tool = new AskUserTool(new FakePresenter("yes"));
        tool.withToolRequest(request(agent).owner(LoopOwner.named("Search", LlmConfig::searchAgentConfig)));

        // WHEN
        String result = tool.askUser("Weiter mit Plan B?", List.of());

        // THEN — plain answer, and the parent's entry is untouched for its own next round …
        assertEquals("yes", result);
        assertEquals(1, agent.getQueuedMessageCount());
        assertEquals(toolLines.toString(), List.of(), toolLines);
        // AND — still intact as an entry, not just as a count (the parent's next round must read it).
        var drained = agent.drainQueue();
        assertNotNull("the urgent entry must survive an agent-less askUser", drained);
        assertTrue(drained, drained.contains("bitte abbrechen"));
    }

    // ========== fixture ==========

    private static AiPoAgent agent() {
        return new AiPoAgent(LlmConfig.newOllama("x").build(), new ToolService(false));
    }

    /** The wiring a real turn gives the tool: its loop owner is the agent that owns the queue. */
    private ToolLoopRequest request(AiPoAgent agent) {
        return ToolLoopRequest.builder()
                .memory(agent.getMemory())
                .chatModel(LlmConfig.newOllama("x").build())
                .monitor(monitor)
                .owner(LoopOwner.of(agent, base -> agent.getConfig()))
                .build();
    }

    /** As the tool loop hands it over: {@code ToolExecutionResultMessage.from(request, result)}. */
    private static ToolExecutionResultMessage toolResult(String result) {
        return ToolExecutionResultMessage.from(
                ToolExecutionRequest.builder().id("1").name("askUser").arguments("{}").build(), result);
    }

    /** A block body line {@code (queued HH:mm): <text>} — the queue clock is the system one here. */
    private static boolean hasTimedLine(String result, String text) {
        var line = Pattern.compile("\\(queued \\d{2}:\\d{2}\\): " + Pattern.quote(text));
        return result.lines().anyMatch(line.asMatchPredicate());
    }
}

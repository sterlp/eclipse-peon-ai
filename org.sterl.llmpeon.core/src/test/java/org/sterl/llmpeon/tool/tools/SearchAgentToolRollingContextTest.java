package org.sterl.llmpeon.tool.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.streaming.ApiRetry;
import org.sterl.llmpeon.tool.LoopOwner;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * R-LSR-1/2/5/6 (org.sterl.llmpeon.core/docs/search-agent-tool.md § Rolling Search Context): the wiring —
 * every sub-loop gets the block of the <b>previous</b> searches as a static message, and only a complete,
 * uncanceled search adds its own pair. One fixture per test: the block lives on the tool instance, which
 * is one per chat view (R-LSR-6), so a shared fixture would leak pairs between tests.
 */
class SearchAgentToolRollingContextTest {

    /** Framing comes from {@link TaggedResult}. The disclosure needle keeps its R-LSR-4 text without the
     *  leading <tt>//</tt>: a shorter needle is the stronger contains-check. */
    private static final String HEAD = "last-search-rolling";
    private static final String DISCLOSURE = "(older context dropped to fit cap)";

    /** Records every request handed to executeLoop — the sub-loop request carries the static messages. */
    static class RecordingToolService extends ToolService {
        final List<ToolLoopRequest> loops = new ArrayList<>();
        @Override public ChatResponse executeLoop(ToolLoopRequest req) {
            loops.add(req);
            return super.executeLoop(req);
        }
    }

    /** Captures the monitor lines and can switch the cancel flag on demand (R-LSR-2). */
    static class CapturingMonitor implements AiMonitor {
        final List<String> toolLines = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        boolean canceled;
        @Override public void onChatResponse(SimpleMessage m) {
            // a stamped loop builds TOOL/PROBLEM lines as chat messages — OwnerMonitor (ADR-0078)
            if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
            else if (m.role() == SimpleMessage.Type.PROBLEM) problems.add(m.message());
        }
        @Override public void onTool(String message) { toolLines.add(message); }
        @Override public void onProblem(String message) { problems.add(message); }
        @Override public boolean isCanceled() { return canceled; }
    }

    /** The search sub-agent on a fresh view, with the sent static messages captured per prompt. */
    static class Fixture {
        final RecordingToolService service = new RecordingToolService();
        final CapturingMonitor monitor = new CapturingMonitor();
        /** the merged SystemMessage of a search, keyed by its prompt (a retry re-sends the same one) */
        final Map<String, String> sentSystemText = new LinkedHashMap<>();
        final StreamMock streamMock = new StreamMock();
        final SearchAgentTool sniffa;

        private final StreamingChatModel model;
        private Function<ChatRequest, ChatResponse> answer;

        Fixture() {
            model = streamMock.buildMock(request -> {
                sentSystemText.put(lastUserText(request), systemText(request));
                return answer.apply(request);
            });
            sniffa = service.getTool(SearchAgentTool.class).get();
            under("Peon-PO");
        }

        /** The next searches run as a turn of this agent, over its own memory. */
        void under(String agentName) {
            under(agentName, new ThreadSafeMemory());
        }

        void under(String agentName, ThreadSafeMemory parentMemory) {
            var parent = mock(AiAgent.class);
            when(parent.getName()).thenReturn(agentName);
            sniffa.withToolRequest(ToolLoopRequest.builder()
                    .memory(parentMemory)
                    .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), model))
                    .monitor(monitor)
                    // the real backoff would wait 10s per retry — the error case must stay fast
                    .retry(new ApiRetry(100))
                    .owner(LoopOwner.of(parent, LlmConfig::devAgentConfig))
                    .build());
        }

        String search(String prompt, String answerText) {
            return run(prompt, r -> ChatResponse.builder().aiMessage(AiMessage.from(answerText)).build());
        }

        /** (a) the provider fails — the tool leaves via its catch block. */
        String searchFailing(String prompt) {
            return run(prompt, r -> { throw new IllegalStateException("provider exploded"); });
        }

        /** (b) the search ends with no answer text — the tool returns its fallback line. */
        String searchWithoutAnswer(String prompt) {
            return run(prompt, r -> ChatResponse.builder().aiMessage(AiMessage.from("")).build());
        }

        private String run(String prompt, Function<ChatRequest, ChatResponse> fn) {
            answer = fn;
            try {
                return sniffa.searchAgent(prompt);
            } finally {
                answer = null;
            }
        }

        private static String lastUserText(ChatRequest request) {
            var messages = request.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof UserMessage um && um.hasSingleText()) return um.singleText();
            }
            throw new IllegalStateException("sub-loop sent no user message: " + messages);
        }

        /** ToolService merges all static messages into one SystemMessage (toOneSystemMessage). */
        private static String systemText(ChatRequest request) {
            return request.messages().stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(m -> ((SystemMessage) m).text())
                    .collect(Collectors.joining("\n"));
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    // UC-LSR-1
    @Test
    @Timeout(10)
    void successfulSearchAppendsPair() {
        // GIVEN — one view, three complete searches after another
        var fixture = new Fixture();

        // WHEN
        fixture.search("q-1", "answer-1");
        fixture.search("q-2", "answer-2");
        fixture.search("q-3", "answer-3");

        // THEN — the second search carries the first pair, and never its own question (snapshot before)
        var second = fixture.sentSystemText.get("q-2");
        assertThat(second).contains("Frage: q-1", "Antwort: answer-1").doesNotContain("q-2");

        // AND — the third carries both earlier pairs, oldest first
        var third = fixture.sentSystemText.get("q-3");
        assertThat(third).contains("Frage: q-1", "Antwort: answer-1", "Frage: q-2", "Antwort: answer-2");
        assertThat(third.indexOf("Frage: q-1")).isLessThan(third.indexOf("Frage: q-2"));
    }

    // UC-LSR-2
    @Test
    @Timeout(10)
    void failedSearchLeavesBlockUnchanged() {
        // GIVEN — one complete search plus a near-cap pair: 9.950 chars, so nothing is cut yet
        var fixture = new Fixture();
        fixture.search("q-1", "answer-1");
        fixture.search("q-0", "near-cap-head" + "x".repeat(9_887));

        // WHEN (a) — the provider throws
        assertThat(fixture.searchFailing("q-2")).startsWith("Search agent failed");

        // WHEN (b) — the search ends without an answer text
        assertThat(fixture.searchWithoutAnswer("q-3")).isEqualTo("Search completed but returned no result");

        // WHEN (c) — the user stopped the call after the answer arrived (executeLoop breaks on cancel)
        fixture.monitor.canceled = true;
        fixture.search("q-4", "partial answer after stop");
        fixture.monitor.canceled = false;

        // THEN — the next search still sees the two good pairs, none of the three failures
        fixture.search("q-5", "answer-5");
        var sent = fixture.sentSystemText.get("q-5");
        assertThat(countOccurrences(sent, "Frage: ")).isEqualTo(2);
        assertThat(countOccurrences(sent, "Frage: q-1")).isEqualTo(1);
        assertThat(sent).doesNotContain("q-2", "q-3", "q-4", "provider exploded",
                "Search completed but returned no result", "Search agent failed", "partial answer after stop");

        // AND — still the uncut block: a raw cut always eats from the front, so the first pair and the
        //       head of the near-cap pair prove nothing was dropped and no disclosure appeared
        assertThat(sent).contains("Frage: q-1\nAntwort: answer-1\n", "Frage: q-0\nAntwort: near-cap-head")
                .doesNotContain(DISCLOSURE);
    }

    // UC-LSR-5
    // UC-LSR-4
    @Test
    @Timeout(10)
    void subLoopSeesRollingBlockAsStaticMessage() {
        // GIVEN — a first search delivered a pair
        var fixture = new Fixture();
        fixture.search("q-1", "answer-1");

        // WHEN — a second search runs in the same view
        fixture.search("q-2", "answer-2");

        // THEN — the block reaches the sub-loop as its (merged) SystemMessage
        var system = fixture.streamMock.getLast(SystemMessage.class);
        assertThat(system).isPresent();
        assertThat(system.get().text()).contains(TaggedResult.PREFIX + HEAD,
                TaggedResult.SUFFIX + HEAD, "Frage: q-1", "Antwort: answer-1");

        // AND — the sub-memory stays clean: its own question, never the earlier answer
        var subMemory = fixture.service.loops.get(1).getMemory().getCopy();
        assertThat(subMemory.stream().filter(UserMessage.class::isInstance).map(UserMessage.class::cast))
                .extracting(UserMessage::singleText).containsExactly("q-2");
        assertThat(subMemory).noneMatch(m -> ChatMessageUtil.toString(m).contains("answer-1"));

        // AND — the first search of the view had nothing to inject
        assertThat(fixture.sentSystemText.get("q-1")).doesNotContain(TaggedResult.PREFIX + HEAD);

        // GIVEN — a search whose answer pushes the pair content over the cap
        fixture.search("q-3", "y".repeat(12_000));

        // WHEN — the next search runs on the trimmed block
        fixture.search("q-4", "answer-4");

        // THEN — the injected message names its own cut, so the sub-loop cannot read a fragment as the
        //        whole history (UC-LSR-4 at the injection point, not only in the holder)
        var trimmed = fixture.streamMock.getLast(SystemMessage.class);
        assertThat(trimmed).isPresent();
        assertThat(trimmed.get().text()).contains(TaggedResult.PREFIX + HEAD,
                TaggedResult.SUFFIX + HEAD, DISCLOSURE);
    }

    // UC-LSR-6
    @Test
    @Timeout(10)
    void agentsShareOneBlockPerView() {
        // GIVEN — two agents of one view, each with its own parent memory
        var fixture = new Fixture();
        var memoryA = new ThreadSafeMemory();
        memoryA.add(UserMessage.from("parent A"));
        fixture.under("Peon-PO", memoryA);

        // WHEN — the other agent searches afterwards
        fixture.search("qA", "aA");
        fixture.under("Peon-Dev");
        fixture.search("qB", "aB");

        // THEN — Search answers B with A's pair, although B's agent never searched for it
        assertThat(fixture.sentSystemText.get("qB")).contains("Frage: qA", "Antwort: aA");

        // AND — the parent memories are untouched by the sub-loop
        assertThat(memoryA.getCopy()).containsExactly(UserMessage.from("parent A"));
    }
}

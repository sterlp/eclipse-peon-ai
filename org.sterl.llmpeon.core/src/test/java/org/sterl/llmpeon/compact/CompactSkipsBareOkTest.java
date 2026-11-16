package org.sterl.llmpeon.compact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.memory.ThreadSafeMemory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/** R-CC-21 (docs/compact.md): the compact input never carries a bare "ok" filler. */
class CompactSkipsBareOkTest {

    private static final String NL = System.lineSeparator();

    // UC-CC-3
    @Test
    void compactInputSkipsBareOk() {
        var memory = new ThreadSafeMemory();
        var call = ToolExecutionRequest.builder().id("1").name("read").arguments("{\"p\":\"a.txt\"}").build();
        var ok = AiMessage.from("ok");
        memory.add(UserMessage.from("lies a.txt"));
        memory.add(AiMessage.from(call));
        memory.add(ToolExecutionResultMessage.from("1", "read", "hello"));
        memory.add(ok);
        memory.add(UserMessage.from("weiter"));

        var outcome = new ContextTrimComponent().stage(memory.getCopy(), 0);

        assertThat(outcome.input()).isEqualTo(String.join(NL,
                "USER:" + NL + "lies a.txt",
                "TOOL REQUEST:" + NL + "tool name: read" + NL + "arguments:{\"p\":\"a.txt\"}",
                "TOOL RESULT:" + NL + "tool name: read" + NL + "result:" + NL + "hello",
                "USER:" + NL + "weiter"));
        assertThat(outcome.input()).doesNotContain("AI:" + NL + "ok");
        assertThat(memory.getCopy()).hasSize(5);
        assertThat(memory.getCopy().get(3)).isSameAs(ok);
    }

    // UC-CC-4
    @Test
    void compactInputKeepsOkWithContent() {
        var memory = new ThreadSafeMemory();
        memory.add(AiMessage.builder().text("ok").thinking("prüfe Pfad").build());
        memory.add(AiMessage.from("ok, erledigt"));

        var outcome = new ContextTrimComponent().stage(memory.getCopy(), 0);

        assertThat(outcome.input()).isEqualTo(String.join(NL,
                "AI:" + NL + "ok" + NL + "THINK:" + NL + "prüfe Pfad",
                "AI:" + NL + "ok, erledigt"));
        assertThat(memory.getCopy()).hasSize(2);
    }
}

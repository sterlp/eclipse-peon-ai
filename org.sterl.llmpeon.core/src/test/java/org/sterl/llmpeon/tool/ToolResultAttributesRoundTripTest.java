package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

/**
 * Contract test for "kein zweiter Speicher": a tool result carries its own attributes through the
 * history store's own serialization channel, so a value the factory needs later (who answered) can
 * ride in Jon's existing history file instead of a side file.
 * <p>
 * This is the measurement gate for the attribute transport — {@code attributes} is documented as
 * "not sent to the LLM but can be persisted in a ChatMemory"; whether langchain4j really writes and
 * reads them is exactly what is pinned here. If this test ever fails, the transport decision is
 * broken and must be re-decided by the owner, not patched around.
 */
class ToolResultAttributesRoundTripTest {

    private static final String ANSWER = "eine Frucht: Apfel";

    @Test
    void attributesSurviveHistoryRoundTrip(@TempDir Path dir) throws IOException {
        // GIVEN — the tool result as ToolService builds it: model text untouched, both values as attributes
        var request = ToolExecutionRequest.builder().id("42").name("askDev").arguments("{}").build();
        var withAttributes = ToolExecutionResultMessage.from(request, ANSWER).toBuilder()
                .attributes(Map.of(
                        ToolResultAttributes.DELEGATED_AGENT, "Peon-Dev",
                        ToolResultAttributes.DELEGATED_ANSWER, ANSWER))
                .build();

        // WHEN — through the same JSON channel FileAgentHistoryStore uses
        var json = ChatMessageSerializer.messageToJson(withAttributes);
        var read = ChatMessageDeserializer.messageFromJson(json);

        // THEN — both values come back, the model-facing text is unchanged
        var result = (ToolExecutionResultMessage) read;
        assertThat(result.attributes())
                .containsEntry(ToolResultAttributes.DELEGATED_AGENT, "Peon-Dev")
                .containsEntry(ToolResultAttributes.DELEGATED_ANSWER, ANSWER);
        assertThat(result.text()).isEqualTo(ANSWER);
        assertThat(result.id()).isEqualTo("42");
        assertThat(result.toolName()).isEqualTo("askDev");
        // AND — the attributes are not part of the model text
        assertThat(result.text()).doesNotContain("Peon-Dev");

        // WHEN — through the real history file, line by line
        var store = new FileAgentHistoryStore(dir.resolve("Peon-PO-history.jsonl"));
        store.append(withAttributes);
        List<ChatMessage> loaded = store.load();

        // THEN — same truth after a restart, still one file
        assertThat(loaded).hasSize(1);
        var fromFile = (ToolExecutionResultMessage) loaded.get(0);
        assertThat(fromFile.attributes())
                .containsEntry(ToolResultAttributes.DELEGATED_AGENT, "Peon-Dev")
                .containsEntry(ToolResultAttributes.DELEGATED_ANSWER, ANSWER);
        assertThat(fromFile.text()).isEqualTo(ANSWER);
        assertThat(fromFile).isEqualTo(withAttributes);
    }
}

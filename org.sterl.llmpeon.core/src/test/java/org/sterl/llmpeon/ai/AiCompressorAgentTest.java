package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.Tag;
import org.sterl.llmpeon.agent.AiCompressorAgent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

/**
 * https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/agents.md
 */
@Tag("integtration")
class AiCompressorAgentTest {
    ChatModel model = new LlmConfig(AiProvider.OLLAMA, "devstral-small-2:24b", "http://192.168.178.87:11434", 5000, false, null, null)
            .build();
    //@Test
    void test() {
        // GIVEN
        var messages = new ArrayList<ChatMessage>();
        messages.add(UserMessage.from("Build be a Hello world"));
        messages.add(AiMessage.from("In which language?"));
        messages.add(UserMessage.from("In java"));
        messages.add(AiMessage.from("What should it do?"));
        messages.add(UserMessage.from("It should show a Hello world with the current time"));
        
        // WHEN
        var result = new AiCompressorAgent(model).call(messages, null);
        
        // THEN
        System.out.println(result.aiMessage().text());
        System.out.println(result.metadata());
        
        assertTrue(result.aiMessage().text().length() > 10);
        assertTrue(result.aiMessage().text().contains("WHAT"));
    }
}

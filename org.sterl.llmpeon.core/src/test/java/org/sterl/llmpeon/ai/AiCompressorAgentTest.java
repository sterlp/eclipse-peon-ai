package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ChatService;
import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

/**
 * https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/agents.md
 */
class AiCompressorAgentTest {
    ChatModel model = new LlmConfig(AiProvider.OPEN_AI, 
            "qwen/qwen3.5-9b", "http://localhost:1234", 5000, false, null, null)
            .build();
    
    @Test
    @Tag("integration")
    void test_compressContext() {
        // GIVEN
        var config = LlmConfig.newConfig(AiProvider.LM_STUDIO, "qwen/qwen3.5-9b", "http://localhost:1234/v1");
        var subject = new ChatService<TemplateContext>(config, new ToolService(), new SkillService(), new TemplateContext(Path.of(".")));
        
        subject.addMessage(UserMessage.from("Build be a Hello world"));
        subject.addMessage(AiMessage.from("In which language?"));
        subject.addMessage(UserMessage.from("In java"));
        subject.addMessage(AiMessage.from("What should it do?"));
        subject.addMessage(UserMessage.from("It should show a Hello world with the current time"));
        
        // WHEN
        var result = subject.compressContext(AiMonitor.NULL_MONITOR);
        
        // THEN
        System.out.println(result.aiMessage().text());
        System.out.println(result.metadata());
        
        assertTrue(result.aiMessage().text().length() > 10);
        assertTrue(result.aiMessage().text().contains("WHAT"));
        
        // AND
        assertTrue(subject.getMessages().size() <= 2, "Chat messages aren't reduced! Still " + subject.getMessages().size());
    }
}

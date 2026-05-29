package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AiDeveloperService;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/agents.md
 */
class AiCompressorAgentTest {

    private MockLlmServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockLlmServer(0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void test_compressContext() {
        // GIVEN
        var config = LlmConfig.newConfig(AiProvider.OPEN_AI, "mock-model", 
                String.format("http://localhost:%d/v1", server.getPort()));
        server.queueResponse("WHAT: Build a Java Hello world application that displays the current time when executed.");

        var subject = new AiDeveloperService(config.build(), new ToolService(), new SkillService());

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

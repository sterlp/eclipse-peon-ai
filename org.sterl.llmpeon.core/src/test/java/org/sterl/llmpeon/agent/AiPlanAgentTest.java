package org.sterl.llmpeon.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

class AiPlanAgentTest {

    AiPlanAgent subject;

    @BeforeEach
    void setUp() {
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999");
        subject = new AiPlanAgent(config.build(), new ToolService());
    }

    @Test
    void extractLastPlan_returnsEmpty_whenNoMessages() {
        assertTrue(subject.getMemory().getLastOf(AiMessage.class) == null);
    }

    @Test
    void extractLastPlan_returnsLastPlan_forSmallConversation() {
        // GIVEN: a small conversation (would previously fail the tooLarge guard)
        subject.addMessage(UserMessage.from("Plan me a feature"));
        subject.addMessage(AiMessage.from("Here is the plan: 1. Context 2. Affected files 3. Steps"));

        // WHEN / THEN
        var plan = subject.getMemory().getLastOf(AiMessage.class);
        assertEquals("Here is the plan: 1. Context 2. Affected files 3. Steps", plan.text());

    }

    @Test
    void extractLastPlan_returnsLastPlan_forLargeConversation() {
        // GIVEN: > 4 messages
        subject.addMessage(UserMessage.from("question 1"));
        subject.addMessage(AiMessage.from("answer 1"));
        subject.addMessage(UserMessage.from("question 2"));
        subject.addMessage(AiMessage.from("answer 2"));
        subject.addMessage(UserMessage.from("question 3"));
        subject.addMessage(AiMessage.from("Final plan: 1. Context 2. Affected files 3. Steps"));

        // WHEN / THEN
        var plan = subject.getMemory().getLastOf(AiMessage.class);
        assertEquals("Final plan: 1. Context 2. Affected files 3. Steps", plan.text());
    }
}

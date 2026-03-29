package org.sterl.llmpeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

class AiPlannerServiceTest {

    AiPlannerService subject;

    @BeforeEach
    void setUp() {
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999");
        subject = new AiPlannerService(config, new ToolService(), new SkillService(), new TemplateContext(Path.of(".")));
    }

    @Test
    void extractLastPlan_returnsEmpty_whenNoMessages() {
        assertTrue(subject.extractLastPlan().isEmpty());
    }

    @Test
    void extractLastPlan_returnsLastPlan_forSmallConversation() {
        // GIVEN: a small conversation (would previously fail the tooLarge guard)
        subject.addMessage(UserMessage.from("Plan me a feature"));
        subject.addMessage(AiMessage.from("Here is the plan: 1. Context 2. Affected files 3. Steps"));

        // WHEN / THEN
        var plan = subject.extractLastPlan();
        assertTrue(plan.isPresent(), "Should return the plan even for small conversations");
        assertEquals("Here is the plan: 1. Context 2. Affected files 3. Steps", plan.get().text());
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
        var plan = subject.extractLastPlan();
        assertTrue(plan.isPresent());
        assertEquals("Final plan: 1. Context 2. Affected files 3. Steps", plan.get().text());
    }
}

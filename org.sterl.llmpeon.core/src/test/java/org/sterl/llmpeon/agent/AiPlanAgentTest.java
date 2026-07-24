package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
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

    // -------------------------------------------------------------------------
    // Bug 6: Agent model save asymmetry
    // -------------------------------------------------------------------------

    @Test
    void setAgentModelName_updatesPlanModel_notGlobalModel() {
        // GIVEN
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "qwen3", "http://localhost:9999");
        var subject = new AiPlanAgent(config.build(), new ToolService());

        // WHEN
        var changed = subject.setAgentModelName("gpt-4");

        // THEN
        assertThat(changed).isTrue();
        assertThat(subject.getAgentModelName()).isEqualTo("gpt-4");
        // AND global model is unchanged
        assertThat(subject.configuredModel.getConfig().getModel()).isEqualTo("qwen3");
    }

    @Test
    void getAgentModelName_returnsNull_whenNoPlanModelSet() {
        // GIVEN
        var config = LlmConfig.newConfig(AiProvider.OLLAMA, "qwen3", "http://localhost:9999");
        var subject = new AiPlanAgent(config.build(), new ToolService());

        // WHEN / THEN
        assertThat(subject.getAgentModelName()).isNull();
    }
}

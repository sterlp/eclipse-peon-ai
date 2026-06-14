package org.sterl.llmpeon.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.ChatMessageUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

class ThreadSafeMemoryTest {

    @Test
    void testExtractLastOf() {
        // GIVEN
        var subject = new ThreadSafeMemory();
        
        subject.add(UserMessage.from("Foo"));
        subject.add(AiMessage.from("bar"));
        subject.add(UserMessage.from("Build Plan"));
        subject.add(AiMessage.from("Your plan"));
        
        // WHEN
        var plan = subject.getLastOf(AiMessage.class);
        
        // THEN
        assertThat(plan.text()).isEqualTo("Your plan");
    }
    
    @Test
    void testJoinUserMessage() {
        // GIVEN
        var subject = new ThreadSafeMemory();
        
        subject.add(AiMessage.from("1"));
        subject.add(UserMessage.from("Foo"));
        subject.add(AiMessage.from("2"));
        subject.add(UserMessage.from("Bar"));
        subject.add(AiMessage.from("3"));
        
        // WHEN
        subject.add(UserMessage.from("U1"));
        subject.add(UserMessage.from("U2"));
        
        // THEN
        assertThat(subject.size()).isEqualTo(6);
        var messages = subject.getCopy();
        // AND
        assertThat(ChatMessageUtil.toString(messages.getLast())).contains("U1", "U2");
        // AND
        assertThat(subject.messageFlow()).isEqualTo("AI->USER->AI->USER->AI->USER");
    }
    
    
    @Test
    void test_trailing_tool_result() {
        // GIVEN
        var subject = new ThreadSafeMemory();
        
        subject.add(UserMessage.from("Foo"));
        subject.add(AiMessage.from(ToolExecutionRequest.builder().id("1").name("foo").build()));
        subject.add(ToolExecutionResultMessage.from("1", "foo", "bar"));
        
        // WHEN
        subject.add(UserMessage.from("U1"));
        
        // THEN
        assertThat(subject.size()).isEqualTo(5);
        var messages = subject.getCopy();
        // AND
        assertThat(ChatMessageUtil.toString(messages.getLast())).contains("U1");
        // AND
        assertThat(subject.messageFlow()).isEqualTo("USER->TOOL_REQUEST->TOOL_EXECUTION_RESULT->AI->USER");
    }

}

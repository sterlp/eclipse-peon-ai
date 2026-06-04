package org.sterl.llmpeon.mock.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
public class SseChunk {

    private String id = "chatcmpl-mock";
    private String object = "chat.completion.chunk";
    private long created;
    private String model = "mock";
    private List<Choice> choices;
    private List<ToolCall> tool_calls;

    public SseChunk() {
        this.created = System.currentTimeMillis() / 1000;
    }

    public void setChoices(List<Choice> choices) { this.choices = choices; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.tool_calls = toolCalls; }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    public static class Choice {
        private int index = 0;
        private Delta delta;
        private String finish_reason;

        public Choice(String content, String finishReason) {
            this.delta = new Delta(content);
            this.finish_reason = finishReason;
        }

        public Choice(Delta delta, String finishReason) {
            this.delta = delta;
            this.finish_reason = finishReason;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    public static class Delta {
        private String role = "assistant";
        private String content;
        private List<ToolCall> tool_calls;

        public Delta(String content) { this.content = content; }
        public void setToolCalls(List<ToolCall> toolCalls) { this.tool_calls = toolCalls; }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    public static class ToolCall {
        private String id;
        private String type = "function";
        private Function function;

        public ToolCall(String id, String name, String arguments) {
            this.id = id;
            this.function = new Function(name, arguments);
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    public static class Function {
        private String name;
        private String arguments;

        public Function(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }
}

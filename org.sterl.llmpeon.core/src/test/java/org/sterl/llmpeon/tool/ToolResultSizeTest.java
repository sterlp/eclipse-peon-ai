package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.CoreTestFixtures;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.tools.AbstractTool;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * R-RS-1 / R-RS-2 (docs/tool-result-size.md): every tool completion line ends with the exact
 * character count of the tool's return string; the return string itself stays untouched.
 */
class ToolResultSizeTest {

    // R-RS-1
    @Test
    void onToolLineDisclosesResultChars(@TempDir Path tempDir) throws IOException {
        // GIVEN a fixture file with one matching line
        Files.writeString(tempDir.resolve("a.txt"), "abc\n");
        var tools = new ArrayList<String>();
        var tool = new DiskGrepTool(tempDir);
        tool.withToolRequest(request(new ThreadSafeMemory(), monitorCapturing(tools)));

        // WHEN the tool runs
        String result = tool.diskGrepFiles("abc", null, null);

        // THEN the return string is untouched and the done line ends with its exact size
        assertThat(result).doesNotContain("chars)");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0)).contains("found 1 matched lines");
        assertThat(tools.get(0)).endsWith(StringUtil.charsSuffix(result));
    }

    // R-RS-1
    @Test
    void onToolEmptyResultDisclosesZeroChars() {
        // GIVEN a tool whose return string is empty
        var tools = new ArrayList<String>();
        var tool = new FixedResultTool("");
        tool.withToolRequest(request(new ThreadSafeMemory(), monitorCapturing(tools)));

        // WHEN the tool runs
        String result = tool.fixedResult();

        // THEN the line ends with an honest zero
        assertThat(result).isEmpty();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0)).endsWith("(0 chars)");
    }

    // R-RS-1
    @Test
    void subAgentTimingAndCharsCoexist() {
        // GIVEN an agent whose compact() returns COMPACTED; the monitor captures the done line
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.add(AiMessage.from("AI response"));
        AiAgent agent = new AiAgent() {
            @Override public String getName() { return "stub-agent"; }
            @Override public String getSystemPrompt() { return "system"; }
            @Override public ChatResponse call(String message, AiMonitor monitor) { return null; }
            @Override public CompactResult compact(AiMonitor monitor) { return CoreTestFixtures.compactedResult(); }
            @Override public ThreadSafeMemory getMemory() { return memory; }
            @Override public void clear() {}
            @Override public boolean isToolActive(SmartToolExecutor exec) { return true; }
            @Override public boolean isMcpToolActive(String toolName) { return true; }
            @Override public int tokenContextUsedInPercent() { return 0; }
            @Override public List<ChatMessage> buildStaticMessages(AiMonitor monitor) { return List.of(); }
        };
        var tools = new ArrayList<String>();
        var subject = new CompactSessionTool();
        subject.withToolRequest(request(memory, monitorCapturing(tools)).agent(agent));

        // WHEN the sub-agent tool finishes
        String result = subject.compactSession(null);

        // THEN duration and chars sit side by side, chars last — and the result stays clean
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0)).matches(".*\\(\\d+s(?: \\d+s)?\\) \\(\\d+ chars\\)$");
        assertThat(tools.get(0)).endsWith(StringUtil.charsSuffix(result));
        assertThat(result).doesNotContain(" chars)");
    }

    // R-RS-2
    @Test
    void onToolCharsAreExactLength() {
        // GIVEN a tool returning a 12345-char string
        var big = "x".repeat(12_345);
        var tools = new ArrayList<String>();
        var tool = new FixedResultTool(big);
        tool.withToolRequest(request(new ThreadSafeMemory(), monitorCapturing(tools)));

        // WHEN the tool runs
        String result = tool.fixedResult();

        // THEN the suffix is the exact String.length — no estimate, and the result is untouched
        assertThat(result).isEqualTo(big);
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0)).contains("(12345 chars)");
        assertThat(tools.get(0)).endsWith(StringUtil.charsSuffix(result));
    }

    /** Full request wiring (memory + chatModel are required fields) — the model is never called by these tools. */
    private static ToolLoopRequest request(ThreadSafeMemory memory, AiMonitor monitor) {
        var config = LlmConfig.builder().model("test").build();
        var cm = new StreamMock().buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("unused"))
                .build());
        return ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(config, cm))
                .monitor(monitor)
                .build();
    }

    private static AiMonitor monitorCapturing(List<String> tools) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { tools.add(message); }
        };
    }

    /** Minimal String tool with a fixed return value — the disclosure is asserted on its done line. */
    private static final class FixedResultTool extends AbstractTool {
        private final String result;

        FixedResultTool(String result) {
            this.result = result;
        }

        public String fixedResult() {
            onTool("Fixed tool done " + StringUtil.charsSuffix(result));
            return result;
        }
    }
}

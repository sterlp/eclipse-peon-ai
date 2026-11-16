package org.sterl.llmpeon.scaffold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/** Both entrances prepare the config-scoped disk tools — the typed one is not a way around it (R-TR-11). */
@Timeout(1)
class ScaffoldUserInputCallTest {

    @TempDir Path temp;

    static Stream<Arguments> entrances() {
        return Stream.of(
                Arguments.of(false, false), Arguments.of(true, false),
                Arguments.of(false, true), Arguments.of(true, true));
    }

    // R-TR-11
    @ParameterizedTest(name = "typed={0}, newConfigDir={1}")
    @MethodSource("entrances")
    void bothEntrancesRefreshEveryDiskToolWorkingDirectory(boolean typed, boolean newConfigDir) throws Exception {
        var original = Files.createDirectories(temp.resolve("original"));
        var replaced = Files.createDirectories(temp.resolve("replaced"));
        var wrong = Files.createDirectories(temp.resolve("wrong"));
        Files.writeString(original.resolve("r.txt"), "ORIGINAL-ONLY");
        Files.writeString(replaced.resolve("r.txt"), "REPLACED-ONLY");
        Files.writeString(wrong.resolve("r.txt"), "WRONG-ONLY");
        var current = newConfigDir ? replaced : original;

        var requests = new ArrayList<ChatRequest>();
        var model = new StreamMock().buildMock(r -> {
            requests.add(r);
            if (requests.size() > 1) return ChatResponse.builder().aiMessage(AiMessage.from("OK")).build();
            return ChatResponse.builder().aiMessage(AiMessage.from(List.of(
                    call("1", "diskReadFile", "{\"filePath\":\"r.txt\"}"),
                    call("2", "diskGrepFiles", "{\"query\":\"ONLY\"}"),
                    call("3", "diskWriteFile", "{\"filePath\":\"w.txt\",\"content\":\"WRITTEN\"}")))).build();
        });
        var configured = new ConfiguredChatModel(
                LlmConfig.builder().model("mock").configDir(original).build(), model);
        var subject = new AiScaffoldAgent(configured);
        if (newConfigDir) { // a changed config drops the model, the stub goes back in
            configured.updateConfig(configured.getConfig().toBuilder().configDir(replaced).build());
            configured.setChatModel(model);
        }
        var tools = subject.getToolService();
        tools.getTool(DiskFileReadTool.class).orElseThrow().setWorkingDir(wrong);
        tools.getTool(DiskFileWriteTool.class).orElseThrow().setWorkingDir(wrong);
        // grep starts in the previous legitimate dir (original), or in wrong when that is the current one
        tools.getTool(DiskGrepTool.class).orElseThrow().setWorkingDir(newConfigDir ? original : wrong);

        if (typed) subject.call(UserInput.of("go"), m -> {});
        else subject.call("go", m -> {});

        assertThat(requests).hasSize(2);
        var results = requests.get(1).messages().stream().filter(ToolExecutionResultMessage.class::isInstance)
                .map(m -> ((ToolExecutionResultMessage) m).text()).toList();
        assertThat(results).hasSize(3);
        assertThat(results.get(0)).isEqualTo(
                (newConfigDir ? "   1: REPLACED-ONLY" : "   1: ORIGINAL-ONLY") + "\n");
        var hit = current.resolve("r.txt").toAbsolutePath().normalize();
        assertThat(results.get(1)).isEqualTo(hit + ":1: " + (newConfigDir ? "REPLACED-ONLY" : "ORIGINAL-ONLY") + "\n\nregex search");
        assertThat(current.resolve("w.txt")).hasContent("WRITTEN");
        assertThat(wrong.resolve("w.txt")).doesNotExist();
    }

    private static ToolExecutionRequest call(String id, String name, String args) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(args).build();
    }
}

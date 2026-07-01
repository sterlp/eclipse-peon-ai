package org.sterl.llmpeon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.agent.AgentPromptFile;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.shared.PromptYmlParser;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.agent.tool.Tool;

class CustomAgentServiceTest {

    static class ReadTool implements SmartTool {
        @Tool("read a file")
        public String read_file() { return "r"; }
        @Override public boolean isEditTool() { return false; }
        @Override public void withToolRequest(ToolLoopRequest r) {}
    }

    static class WriteTool implements SmartTool {
        @Tool("write a file")
        public String write_file() { return "w"; }
        @Override public boolean isEditTool() { return true; }
        @Override public void withToolRequest(ToolLoopRequest r) {}
    }

    private final ToolService toolService = buildToolService();

    private static ToolService buildToolService() {
        var ts = new ToolService();
        ts.addTool(new ReadTool());
        ts.addTool(new WriteTool());
        return ts;
    }

    private CustomAgentService serviceFor(AgentPromptFile agent) {
        var configuredModel = LlmConfig.builder().model("base-model").build().build();
        return new CustomAgentService(configuredModel, toolService, agent);
    }

    private AgentPromptFile agent(List<String> tools, boolean readOnly, String model) {
        return AgentPromptFile.builder()
                .name("t").description(null).promptFile(Path.of("AGENT.md"))
                .tools(tools).readOnly(readOnly).model(model).build();
    }

    private SmartToolExecutor exec(String name) {
        return toolService.getExecutor(name);
    }

    @Test
    void absentAllowlistAllowsAllTools() {
        var svc = serviceFor(agent(null, false, null));
        var filter = svc.getToolFilter();
        assertThat(filter.test(exec("read_file"))).isTrue();
        assertThat(filter.test(exec("write_file"))).isTrue();
    }

    @Test
    void readOnlyBlocksEditTools() {
        var svc = serviceFor(agent(null, true, null));
        var filter = svc.getToolFilter();
        assertThat(filter.test(exec("read_file"))).isTrue();
        assertThat(filter.test(exec("write_file"))).isFalse();
    }

    @Test
    void allowlistRestrictsTools() {
        var svc = serviceFor(agent(List.of("read_"), false, null));
        var filter = svc.getToolFilter();
        assertThat(filter.test(exec("read_file"))).isTrue();
        assertThat(filter.test(exec("write_file"))).isFalse();
    }

    @Test
    void toolNameFilterGovernsMcpNames() {
        var svc = serviceFor(agent(List.of("mcp__docs__search"), false, null));
        var nameFilter = svc.getToolNameFilter();
        assertThat(nameFilter.test("mcp__docs__search_docs")).isTrue();
        assertThat(nameFilter.test("mcp__docs__scrape_docs")).isFalse();
    }

    @Test
    void introspectionApiReflectsFilters() {
        // GIVEN a read-only agent limited to read_ tools and one MCP prefix
        var svc = serviceFor(agent(List.of("read_", "mcp__docs__search"), true, null));

        // THEN built-in introspection matches getToolFilter
        assertThat(svc.isToolActive(exec("read_file"))).isTrue();
        assertThat(svc.isToolActive(exec("write_file"))).isFalse();

        // AND MCP introspection matches the name allowlist
        assertThat(svc.isMcpToolActive("mcp__docs__search_docs")).isTrue();
        assertThat(svc.isMcpToolActive("mcp__docs__scrape_docs")).isFalse();
    }

    @Test
    void modelOverrideWinsOverConfig() {
        assertThat(serviceFor(agent(null, false, "agent-model")).getAgentModelName()).isEqualTo("agent-model");
        assertThat(serviceFor(agent(null, false, null)).getAgentModelName()).isEqualTo("base-model");
    }

    @Test
    void systemPromptContainsBody(@TempDir Path tmp) throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\n---\nYou are a special agent body.");
        var agent = AgentPromptFile.parse(file);

        // WHEN
        var prompt = serviceFor(agent).getSystemPrompt();

        // THEN
        assertThat(prompt).contains("You are a special agent body.");
    }

    @Test
    void setModelNamePinsToAgentAndPersistsYaml(@TempDir Path tmp) throws Exception {
        // GIVEN — B3 building blocks: pin model in memory + write back to AGENT.md
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\n---\nbody");
        var svc = serviceFor(AgentPromptFile.parse(file));

        // WHEN
        boolean changed = svc.setModelName(AiModel.builder().id("m2").name("m2").build());
        PromptYmlParser.setFrontmatterValue(file, "model", "m2");

        // THEN
        assertThat(changed).isTrue();
        assertThat(svc.getAgentModelName()).isEqualTo("m2");
        assertThat(AgentPromptFile.parse(file).getModel()).isEqualTo("m2");
    }
}

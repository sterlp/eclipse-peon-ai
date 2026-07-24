package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.agent.CustomAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.prompt.PromptYmlParser;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.agent.tool.Tool;

class CustomAgentServiceTest extends AbstractMemoryFileTest {

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
    private final ConfiguredChatModel chatModel = LlmConfig.builder().model("base-model").build().build();

    private static ToolService buildToolService() {
        var ts = new ToolService();
        ts.addTool(new ReadTool());
        ts.addTool(new WriteTool());
        return ts;
    }

    private int count = 0;
    private CustomAgent newCustomAgent(boolean readOnly, String model) throws IOException {
        var name = "Agent" + (++count) + ".md";
        var agentFile = tmp.resolve(name);
        if (model == null) model = "";
        Files.writeString(agentFile, """
                ---
                name: {name}
                model: {model}
                read-only: {readOnly}
                ---
                Some instruction ...
                """.replace("{readOnly}", readOnly + "")
                .replace("{name}", name)
                .replace("{model}", model));

        return newAgent(agentFile);
    }

    private CustomAgent newAgent(Path agentFile) throws IOException {
        var prompt = PromptYmlParser.parseYml(agentFile);
        return new CustomAgent(prompt, chatModel, toolService);
    }

    private CustomAgent agent(List<String> tools, boolean readOnly, String model)  {
        try {
            var result = newCustomAgent(readOnly, model);
            result.setTools(tools);
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SmartToolExecutor exec(String name) {
        return toolService.getExecutor(name);
    }

    @Test
    void allAllowsTools() {
        var svc = agent(List.of("*"), false, null);

        assertThat(svc.isToolActive(exec("read_file"))).isTrue();
        assertThat(svc.isToolActive(exec("write_file"))).isTrue();
    }

    @Test
    void readOnlyBlocksEditTools() {
        var svc = agent(List.of("*"), true, null);

        assertThat(svc.isToolActive(exec("read_file"))).isTrue();
        assertThat(svc.isToolActive(exec("write_file"))).isFalse();
    }

    @Test
    void allowlistRestrictsTools() {
        var svc = agent(List.of("read_"), false, null);

        assertThat(svc.isToolActive(exec("read_file"))).isTrue();
        assertThat(svc.isToolActive(exec("write_file"))).isFalse();
    }

    @Test
    void toolNameFilterGovernsMcpNames() {
        var svc = agent(List.of("mcp__docs__search"), false, null);
        var nameFilter = svc.getToolNameFilter();
        
        assertThat(nameFilter.test("mcp__docs__search_docs")).isTrue();
        assertThat(nameFilter.test("mcp__docs__scrape_docs")).isFalse();
    }

    @Test
    void introspectionApiReflectsFilters() {
        // GIVEN a read-only agent limited to read_ tools and one MCP prefix
        var svc = agent(List.of("read_", "mcp__docs__search"), true, null);

        // THEN built-in introspection matches getToolFilter
        assertThat(svc.isToolActive(exec("read_file"))).isTrue();
        assertThat(svc.isToolActive(exec("write_file"))).isFalse();

        // AND MCP introspection matches the name allowlist
        assertThat(svc.isMcpToolActive("mcp__docs__search_docs")).isTrue();
        assertThat(svc.isMcpToolActive("mcp__docs__scrape_docs")).isFalse();
    }

    @Test
    void modelNameCheck() {
        assertThat(agent(null, false, "agent-model").getAgentModelName()).isEqualTo("agent-model");
    }

    @Test
    void systemPromptContainsBody() throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\n---\nYou are a special agent body.");

        // WHEN
        var agent = newAgent(file);

        // THEN
        assertThat(agent.getSystemPrompt()).contains("You are a special agent body.");
        // AND
        assertThat(agent.getSystemPrompt()).doesNotContain(PromptLoader.withDefault(""));
    }
    
    @Test
    void systemPromptContainsDefault() throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\ninclude-default: true\n---\nYou are a special agent body.");

        // WHEN
        var agent = newAgent(file);

        // THEN
        assertThat(agent.getSystemPrompt()).contains("You are a special agent body.");
        // AND
        assertThat(agent.getSystemPrompt()).contains(PromptLoader.withDefault(""));
    }

    @Test
    void legacyKeysNotMigratedOnLoadOnlyOnWrite() throws Exception {
        // GIVEN an AGENT.md with legacy `think_enabled: true` in frontmatter
        var file = tmp.resolve("AGENT.md");
        String original = "---\nname: t\nthink_enabled: true\n---\nbody";
        Files.writeString(file, original);

        // WHEN the agent is loaded (no write operation)
        var agent = newAgent(file);

        // THEN the file is NOT modified on load
        String afterLoad = Files.readString(file);
        assertThat(afterLoad).isEqualTo(original);
        // AND the agent still reads the legacy key correctly
        assertThat(agent.isThinkEnabled()).isTrue();
    }

    @Test
    void legacyKeysMigratedOnWrite() throws Exception {
        // GIVEN an AGENT.md with legacy `think_enabled: true` in frontmatter
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\nthink_enabled: true\n---\nbody");

        // WHEN the agent is loaded and a write operation occurs
        var agent = newAgent(file);
        agent.setAgentModelName("m1");

        // THEN the file is migrated with new keys
        String saved = Files.readString(file);
        assertThat(saved).contains("think_supported: true");
        assertThat(saved).doesNotContain("think_enabled");
        // AND the model change is also persisted
        assertThat(saved).contains("model: m1");
    }


    @Test
    void legacyThinkReadsCorrectlyBeforeMigration() throws Exception {
        // GIVEN an AGENT.md with legacy `think: high` in frontmatter
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\nthink: high\n---\nbody");

        // WHEN the agent is loaded (no write)
        var agent = newAgent(file);

        // THEN the legacy key is read correctly via backward compat
        assertThat(agent.getConfig().getThink()).isEqualTo("high");
        assertThat(agent.isThinkEnabled()).isTrue();
    }

    @Test
    void legacyThinkMigratesOnWrite() throws Exception {
        // GIVEN an AGENT.md with legacy `think: high` in frontmatter
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\nthink: high\n---\nbody");

        // WHEN the agent is loaded and a write operation occurs
        var agent = newAgent(file);
        agent.setAgentModelName("m1");

        // THEN the file is saved with the new keys (think implies enabled)
        String saved = Files.readString(file);
        assertThat(saved).contains("think_on_string: high");
        assertThat(saved).contains("think_supported: true");
        assertThat(saved).doesNotContain("think:");
    }

    @Test
    void legacyThinkEnabledReadsCorrectlyBeforeMigration() throws Exception {
        // GIVEN an AGENT.md with legacy `think_enabled: true` in frontmatter
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\nthink_enabled: true\n---\nbody");

        // WHEN the agent is loaded (no write)
        var agent = newAgent(file);

        // THEN the legacy key is read correctly via backward compat
        assertThat(agent.isThinkEnabled()).isTrue();
    }

    @Test
    void legacyThinkEnabledMigratesOnWrite() throws Exception {
        // GIVEN an AGENT.md with legacy `think_enabled: true` in frontmatter
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\nthink_enabled: true\n---\nbody");

        // WHEN the agent is loaded and a write operation occurs
        var agent = newAgent(file);
        agent.setAgentModelName("m1");

        // THEN the file is saved with the new key
        String saved = Files.readString(file);
        assertThat(saved).contains("think_supported: true");
        assertThat(saved).doesNotContain("think_enabled");
    }

    @Test
    void legacyThinkAndEnabledMigrateTogetherOnWrite() throws Exception {
        // GIVEN an AGENT.md with both legacy keys
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\nthink: high\nthink_enabled: true\n---\nbody");

        // WHEN the agent is loaded and a write operation occurs
        var agent = newAgent(file);
        agent.setAgentModelName("m1");

        // THEN both are migrated
        String saved = Files.readString(file);
        assertThat(saved).contains("think_supported: true");
        assertThat(saved).contains("think_on_string: high");
        assertThat(saved).doesNotContain("think:");
        assertThat(saved).doesNotContain("think_enabled");
    }

    @Test
    void setModelNamePinsToAgentAndPersistsYaml() throws Exception {
        // GIVEN — B3 building blocks: pin model in memory + write back to AGENT.md
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\n---\nbody");
        var svc = newAgent(file);

        // WHEN
        boolean changed = svc.setAgentModelName("m2");

        // THEN
        assertThat(changed).isTrue();
        assertThat(svc.getAgentModelName()).isEqualTo("m2");
        assertThat(Files.readString(file)).contains("model: m2");
    }
}

package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.EffectiveConnection;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.prompt.PromptYmlParser;
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
    void absentToolsAllowAll() throws IOException {
        // GIVEN an agent whose frontmatter has no tools: field
        var svc = newCustomAgent(false, null);

        // THEN every tool is active (absent = all)
        assertThat(svc.isToolActive(exec("read_file"))).isTrue();
        assertThat(svc.isToolActive(exec("write_file"))).isTrue();
        // AND the MCP name filter allows everything
        assertThat(svc.getToolNameFilter().test("mcp__docs__search_docs")).isTrue();
    }

    @Test
    void inlineCsvToolsFlattened() throws IOException {
        // GIVEN an agent with an inline CSV tools list (single scalar "grep, read_")
        var file = tmp.resolve("AgentCsv.md");
        Files.writeString(file, "---\nname: t\ntools: grep, read_\n---\nbody");
        var svc = newAgent(file);

        // THEN the CSV is flattened so each entry is a prefix
        assertThat(svc.getToolNameFilter().test("grep")).isTrue();
        assertThat(svc.getToolNameFilter().test("read_")).isTrue();
    }

    @Test
    void absentToolsReadOnlyOnlyReadTools() throws IOException {
        // GIVEN a read-only agent with no tools: field
        var svc = newCustomAgent(true, null);

        // THEN read tools are active but edit tools stay blocked by read-only
        assertThat(svc.isToolActive(exec("read_file"))).isTrue();
        assertThat(svc.isToolActive(exec("write_file"))).isFalse();
    }

    @Test
    void emptyToolsAllowNothing() {
        // GIVEN an agent with an empty tools: list
        var svc = agent(List.of(), false, null);

        // THEN nothing is allowed (empty != absent)
        assertThat(svc.isToolActive(exec("read_file"))).isFalse();
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
    void thinkSupportedCanonicalReadsCorrectly() throws Exception {
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "---\nname: t\nthink_supported: true\nthink_on_string: high\n---\nbody");

        var agent = newAgent(file);

        assertThat(agent.isThinkSupported()).isTrue();
        assertThat(agent.getConfig().getThink()).isEqualTo("high");
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
        assertThat(agent.isThinkSupported()).isTrue();
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
        assertThat(agent.isThinkSupported()).isTrue();
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
        assertThat(agent.isThinkSupported()).isTrue();
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

    private CustomAgent newAgentWith(LlmConfig cfg, String frontmatter) {
        try {
            var file = tmp.resolve("Agent" + (++count) + ".md");
            Files.writeString(file, "---\n" + frontmatter + "\n---\nbody");
            return new CustomAgent(PromptYmlParser.parseYml(file), cfg.build(), toolService);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void frontmatterUrl_resolvesOwnConnectionIdentity() {
        // GIVEN an AGENT.md with its own url
        var cfg = LlmConfig.builder().providerType(AiProvider.OPEN_AI).model("base-model").url("http://base:1234/v1").build();
        var agent = newAgentWith(cfg, "name: t\nurl: http://stub:9999/v1");
        // WHEN
        var conn = EffectiveConnection.of(cfg, agent.getConfig());
        // THEN the agent talks to its own endpoint
        assertThat(conn.identity().url()).isEqualTo("http://stub:9999/v1");
        assertThat(conn.isBase()).isFalse();
    }

    @Test
    void frontmatterApiKeyAndExtraBody_resolveLikeCoreAgents() {
        // GIVEN an OpenAI-based config (per-request body) with api_key + extra_body in the frontmatter
        var openAi = LlmConfig.builder().providerType(AiProvider.OPEN_AI).model("base-model").url("http://base:1234/v1").build();
        var openAgent = newAgentWith(openAi, "name: t\napi_key: sk-agent\nextra_body: '{\"foo\": 1}'");
        var openConn = EffectiveConnection.of(openAi, openAgent.getConfig());
        // THEN the key is on the identity, the body rides per-request (not in the identity)
        assertThat(openConn.identity().apiKey()).isEqualTo("sk-agent");
        assertThat(openConn.perRequestBody()).isEqualTo("{\"foo\": 1}");
        assertThat(openConn.identity().buildTimeBody()).isNull();
        // AND an Anthropic-based config (build-time body)
        var anthropic = LlmConfig.builder().providerType(AiProvider.ANTHROPIC).model("base-model").url("http://base:1234").build();
        var anthropicAgent = newAgentWith(anthropic, "name: t\napi_key: sk-agent\nextra_body: '{\"foo\": 1}'");
        var anthropicConn = EffectiveConnection.of(anthropic, anthropicAgent.getConfig());
        assertThat(anthropicConn.identity().buildTimeBody()).isEqualTo("{\"foo\": 1}");
        assertThat(anthropicConn.perRequestBody()).isNull();
    }

    @Test
    void extraBodySingleQuotedJson_parsesIntact() {
        // GIVEN extra_body as single-quoted JSON (the documented frontmatter quoting rule)
        var cfg = LlmConfig.builder().providerType(AiProvider.OPEN_AI).model("base-model").build();
        var agent = newAgentWith(cfg, "name: t\nextra_body: '{\"cache_control\": {\"type\": \"ephemeral\"}}'");
        // WHEN
        var config = agent.getConfig();
        // THEN the JSON survived the parser intact
        assertThat(config.getExtraBody()).isEqualTo("{\"cache_control\": {\"type\": \"ephemeral\"}}");
    }

    @Test
    void frontmatterTemperature_resolvesLikeCoreSlots() {
        var cfg = LlmConfig.builder().providerType(AiProvider.OPEN_AI).model("base-model").build();
        var agent = newAgentWith(cfg, "name: t\ntemperature: 0.4");

        assertThat(agent.getConfig().getTemperature()).isEqualTo(0.4);
    }

    @Test
    void frontmatterInvalidTemperature_isIgnored() {
        var cfg = LlmConfig.builder().providerType(AiProvider.OPEN_AI).model("base-model").build();
        var agent = newAgentWith(cfg, "name: t\ntemperature: abc");

        assertThat(agent.getConfig().getTemperature()).isNull();
    }


    @Test
    void noModelKeys_inheritsBaseIdentity() {
        // GIVEN an AGENT.md without url/api_key/extra_body
        var cfg = LlmConfig.builder().providerType(AiProvider.OPEN_AI).model("base-model").url("http://base:1234/v1").build();
        var agent = newAgentWith(cfg, "name: t");
        // WHEN
        var conn = EffectiveConnection.of(cfg, agent.getConfig());
        // THEN the base connection is inherited (no behavior change for existing agents)
        assertThat(conn.isBase()).isTrue();
    }
}

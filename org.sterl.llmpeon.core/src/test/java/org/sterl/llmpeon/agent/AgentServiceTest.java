package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.AgentService;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

class AgentServiceTest extends AbstractMemoryFileTest {

    private static Path writeAgent(Path agentsDir, String name, String content) throws Exception {
        var dir = Files.createDirectories(agentsDir.resolve(name));
        var file = dir.resolve("AGENT.md");
        Files.writeString(file, content);
        return file;
    }

    private static Path writeAgentOrder(Path agentsDir, String content) throws Exception {
        var file = agentsDir.resolve("agent-order.txt");
        Files.writeString(file, content);
        return file;
    }
    
    private AgentService service;
    private final ToolService toolService = new ToolService();
    private ConfiguredChatModel chatModel;
    private MockLlmServer mockServer;

    @BeforeEach
    void before() throws IOException {
        tmp = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(tmp);
        
        mockServer = new MockLlmServer();
        mockServer.start();
        LlmConfig config = LlmConfig.builder().model("test").url(mockServer.getUrl()).build();
        chatModel = config.build();

        service = new AgentService(tmp, toolService, chatModel);
    }

    @AfterEach
    void after() {
        if (mockServer != null) mockServer.stop();
    }
    
    @Test
    void hasDefaultAgent() {
        // GIVEN
        var subject = new AgentService(true, tmp.resolve("any-foo"), toolService, chatModel);
        assertThat(subject.getActiveAgent()).isNotNull();
        assertThat(subject.getAgents()).hasSize(2);
        // WHEN
        subject.refresh(tmp.resolve("config"));
        // THEN
        assertThat(subject.getActiveAgent()).isNotNull();
        assertThat(subject.getAgents()).hasSize(2);
    }
    
    @Test
    void loadsAgentsAutomatically() throws Exception {
        // GIVEN
        writeAgent(tmp, "foo", """
                ---
                model: qwen3
                ---
                You are the docs assistant.
                """);

        // WHEN
        var subject = new AgentService(true, tmp, toolService, chatModel);

        // THEN
        assertThat(subject.getAgents()).hasSize(3);
        // AND
        var agent = subject.get("foo").orElseThrow();
        assertThat(agent.getAgentModelName()).isEqualTo("qwen3");
    }

    @Test
    void discoversAgentDirsAndParsesFields() throws Exception {
        // GIVEN
        writeAgent(tmp, "docs", """
                ---
                name: Docs-Assistent
                description: only from docs
                readOnly: true
                model: qwen3
                tools:
                  - grep
                  - read_
                ---
                You are the docs assistant.
                """);

        // WHEN
        var subject = new AgentService(false, tmp, toolService, chatModel);
        var agent = subject.get("Docs-Assistent").orElseThrow();

        // THEN
        assertThat(subject.getAgents()).hasSize(1);
        assertThat(subject.getActiveAgent()).isEqualTo(agent);
        // AND
        assertThat(agent.getName()).isEqualTo("Docs-Assistent");
        assertThat(agent.isReadOnly()).isTrue();
        assertThat(agent.getAgentModelName()).isEqualTo("qwen3");
        assertThat(agent.getTools()).containsExactly("grep", "read_");
        assertThat(agent.getSystemPrompt()).isEqualTo("You are the docs assistant.");
    }

    @Test
    void absentToolsMeansAllTools() throws Exception {
        // GIVEN — no tools field, name derived from directory
        writeAgent(tmp, "free", """
                ---
                description: anything goes
                ---
                body
                """);

        // WHEN
        service.reloadAgents();
        var agent = service.get("free").orElseThrow();

        // THEN
        assertThat(agent.getName()).isEqualTo("free");
        assertThat(agent.getTools()).isNull();
        assertThat(agent.isReadOnly()).isFalse();
        assertThat(agent.getAgentModelName()).isNull();
    }

    @Test
    void ignoresDirectoriesWithoutAgentMd() throws Exception {
        // GIVEN
        Files.createDirectories(tmp.resolve("not-an-agent"));
        writeAgent(tmp, "real", "---\nname: real\n---\nbody");

        // WHEN
        service.reloadAgents();

        // THEN
        assertThat(service.getAgents()).hasSize(1);
        assertThat(service.get("real")).isPresent();
    }

    @Test
    void refreshPicksUpEdits() throws Exception {
        // GIVEN
        var file = writeAgent(tmp, "docs", "---\nname: docs\nmodel: a\n---\nbody");
        service.reloadAgents();
        assertThat(service.get("docs").orElseThrow().getAgentModelName()).isEqualTo("a");

        // WHEN
        Files.writeString(file, "---\nname: docs\nmodel: b\n---\nbody");
        service.refresh(tmp);

        // THEN
        assertThat(service.get("docs").orElseThrow().getAgentModelName()).isEqualTo("b");
    }

    @Test
    void customAgentHistoryPersistsWithoutHistoryFlag() throws Exception {
        // GIVEN
        var agentsDir = tmp.resolve("agents");
        writeAgent(agentsDir, "docs", "---\nname: docs\n---\nbody");
        var subject = new AgentService(false, agentsDir, toolService, chatModel, tmp);
        var agent = subject.get("docs").orElseThrow();

        // WHEN
        agent.getMemory().add(UserMessage.from("hello"));
        agent.getMemory().add(AiMessage.from("world"));
        var restarted = new AgentService(false, agentsDir, toolService, chatModel, tmp);

        // THEN
        var loaded = restarted.get("docs").orElseThrow().getMemory().getCopy();
        assertThat(loaded).hasSize(2);
        assertThat(Files.exists(tmp.resolve("state/docs-history.jsonl"))).isTrue();
    }

    @Test
    void enablesHistoryForPlanDevAndCustomOnly() throws Exception {
        // GIVEN
        var agentsDir = tmp.resolve("agents");
        writeAgent(agentsDir, "docs", "---\nname: docs\n---\nbody");
        var subject = new AgentService(true, agentsDir, toolService, chatModel, tmp);

        // WHEN
        subject.get(AiDevAgent.NAME).orElseThrow().getMemory().add(UserMessage.from("dev"));
        subject.get(AiPlanAgent.NAME).orElseThrow().getMemory().add(UserMessage.from("plan"));
        subject.get("docs").orElseThrow().getMemory().add(UserMessage.from("docs"));

        // THEN
        assertThat(Files.exists(tmp.resolve("state/Peon-Dev-history.jsonl"))).isTrue();
        assertThat(Files.exists(tmp.resolve("state/Peon-Plan-history.jsonl"))).isTrue();
        assertThat(Files.exists(tmp.resolve("state/docs-history.jsonl"))).isTrue();
        assertThat(Files.list(tmp.resolve("state")).map(p -> p.getFileName().toString()))
                .containsExactlyInAnyOrder("Peon-Dev-history.jsonl", "Peon-Plan-history.jsonl", "docs-history.jsonl");
    }

    @Test
    void defaultFileCreated() throws Exception {
        // GIVEN — agents directory exists but no agent-order.txt
        var agentsDir = tmp.resolve("agents");
        Files.createDirectory(agentsDir);

        // WHEN
        new AgentService(false, agentsDir, toolService, chatModel);

        // THEN
        var orderFile = agentsDir.resolve("agent-order.txt");
        assertThat(orderFile).exists();
        var content = Files.readString(orderFile);
        assertThat(content).contains("^Peon-PO$");
    }

    @Test
    void getAgentsWithOrdering() throws Exception {
        // GIVEN — custom ordering file with ^Peon-Dev$ first
        var agentsDir = tmp.resolve("agents");
        Files.createDirectory(agentsDir);
        writeAgent(agentsDir, "Peon-Dev", """
                ---
                name: Peon-Dev
                ---
                body
                """);
        writeAgent(agentsDir, "Alpha-Agent", """
                ---
                name: Alpha-Agent
                ---
                body
                """);
        writeAgent(agentsDir, "Zeta-Agent", """
                ---
                name: Zeta-Agent
                ---
                body
                """);

        writeAgentOrder(agentsDir, """
                ^Peon-Dev$
                """);

        // WHEN
        var subject = new AgentService(false, agentsDir, toolService, chatModel);
        var agents = subject.getAgents();

        // THEN
        assertThat(agents).extracting(AiAgent::getName).containsExactly("Peon-Dev", "Alpha-Agent", "Zeta-Agent");
    }

    @Test
    void alphabeticalFallbackWhenNoPatterns() throws Exception {
        // GIVEN — agent-order.txt with only comments
        var agentsDir = tmp.resolve("agents");
        Files.createDirectory(agentsDir);
        writeAgent(agentsDir, "Charlie", """
                ---
                name: Charlie
                ---
                body
                """);
        writeAgent(agentsDir, "Alpha", """
                ---
                name: Alpha
                ---
                body
                """);
        writeAgent(agentsDir, "Bravo", """
                ---
                name: Bravo
                ---
                body
                """);

        writeAgentOrder(agentsDir, """
                # Only comments
                # Nothing here
                """);

        // WHEN
        var subject = new AgentService(false, agentsDir, toolService, chatModel);
        var agents = subject.getAgents();

        // THEN
        assertThat(agents).extracting(AiAgent::getName).containsExactly("Alpha", "Bravo", "Charlie");
    }

    @Test
    void regexGrouping() throws Exception {
        // GIVEN — agents matching .*Manager.* should appear first, sorted alphabetically
        var agentsDir = tmp.resolve("agents");
        Files.createDirectory(agentsDir);
        writeAgent(agentsDir, "Dev-Manager", """
                ---
                name: Dev-Manager
                ---
                body
                """);
        writeAgent(agentsDir, "Admin-Manager", """
                ---
                name: Admin-Manager
                ---
                body
                """);
        writeAgent(agentsDir, "Other-Agent", """
                ---
                name: Other-Agent
                ---
                body
                """);

        writeAgentOrder(agentsDir, """
                .*Manager.*
                """);

        // WHEN
        var subject = new AgentService(false, agentsDir, toolService, chatModel);
        var agents = subject.getAgents();

        // THEN
        assertThat(agents).extracting(AiAgent::getName).containsExactly("Admin-Manager", "Dev-Manager", "Other-Agent");
    }

    @Test
    void invalidRegexHandling() throws Exception {
        // GIVEN — agent-order.txt with an invalid regex
        var agentsDir = tmp.resolve("agents");
        Files.createDirectory(agentsDir);
        writeAgent(agentsDir, "Good-Agent", """
                ---
                name: Good-Agent
                ---
                body
                """);

        writeAgentOrder(agentsDir, """
                [invalid(regex
                ^Good-Agent$
                """);

        // WHEN — should not crash, invalid regex skipped
        var subject = new AgentService(false, agentsDir, toolService, chatModel);
        var agents = subject.getAgents();

        // THEN
        assertThat(agents).extracting(AiAgent::getName).containsExactly("Good-Agent");
    }

    @Test
    void duplicatePrevention() throws Exception {
        // GIVEN — multiple patterns matching the same agent
        var agentsDir = tmp.resolve("agents");
        Files.createDirectory(agentsDir);
        writeAgent(agentsDir, "Peon-PO", """
                ---
                name: Peon-PO
                ---
                body
                """);
        writeAgent(agentsDir, "Peon-Dev", """
                ---
                name: Peon-Dev
                ---
                body
                """);

        writeAgentOrder(agentsDir, """
                ^Peon-.*$
                ^Peon-PO$
                """);

        // WHEN
        var subject = new AgentService(false, agentsDir, toolService, chatModel);
        var agents = subject.getAgents();

        // THEN — each agent appears exactly once, first pattern wins alphabetically
        assertThat(agents).extracting(AiAgent::getName).containsExactly("Peon-Dev", "Peon-PO");
    }

    @Test
    void multiplePatternsInOrder() throws Exception {
        // GIVEN — multiple patterns defining distinct groups
        var agentsDir = tmp.resolve("agents");
        Files.createDirectory(agentsDir);
        writeAgent(agentsDir, "Manager-A", """
                ---
                name: Manager-A
                ---
                body
                """);
        writeAgent(agentsDir, "Manager-B", """
                ---
                name: Manager-B
                ---
                body
                """);
        writeAgent(agentsDir, "Worker-X", """
                ---
                name: Worker-X
                ---
                body
                """);
        writeAgent(agentsDir, "Worker-Y", """
                ---
                name: Worker-Y
                ---
                body
                """);
        writeAgent(agentsDir, "Other-Z", """
                ---
                name: Other-Z
                ---
                body
                """);

        writeAgentOrder(agentsDir, """
                .*Manager.*
                .*Worker.*
                """);

        // WHEN
        var subject = new AgentService(false, agentsDir, toolService, chatModel);
        var agents = subject.getAgents();

        // THEN
        assertThat(agents).extracting(AiAgent::getName).containsExactly("Manager-A", "Manager-B", "Worker-X", "Worker-Y", "Other-Z");
    }
}

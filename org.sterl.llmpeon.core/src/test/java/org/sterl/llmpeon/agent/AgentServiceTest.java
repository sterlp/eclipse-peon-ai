package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.AgentService;

class AgentServiceTest extends AbstractMemoryFileTest {

    private static Path writeAgent(Path agentsDir, String name, String content) throws Exception {
        var dir = Files.createDirectories(agentsDir.resolve(name));
        var file = dir.resolve("AGENT.md");
        Files.writeString(file, content);
        return file;
    }
    
    private AgentService service;

    @BeforeEach
    void before() throws IOException {
        tmp = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(tmp);
        service = new AgentService(tmp, null, null);
    }
    
    @Test
    void hasDefaultAgent() {
        // GIVEN
        var subject = new AgentService(true, tmp, null, null);
        assertThat(subject.getActiveAgent()).isNotNull();
        // WHEN
        subject.refresh(tmp.resolve("config"));
        // THEN
        assertThat(subject.getActiveAgent()).isNotNull();
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
        var subject = new AgentService(true, tmp, null, null);

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
        var subject = new AgentService(false, tmp, null, null);
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
}

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
        service.refresh();
        var agent = service.get("Docs-Assistent").orElseThrow();

        // THEN
        assertThat(service.getAgents()).hasSize(1);
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
        service.refresh();
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
        service.refresh();

        // THEN
        assertThat(service.getAgents()).hasSize(1);
        assertThat(service.get("real")).isPresent();
    }

    @Test
    void refreshPicksUpEdits() throws Exception {
        // GIVEN
        var file = writeAgent(tmp, "docs", "---\nname: docs\nmodel: a\n---\nbody");
        service.refresh();
        assertThat(service.get("docs").orElseThrow().getAgentModelName()).isEqualTo("a");

        // WHEN
        Files.writeString(file, "---\nname: docs\nmodel: b\n---\nbody");
        service.refresh(tmp);

        // THEN
        assertThat(service.get("docs").orElseThrow().getAgentModelName()).isEqualTo("b");
    }
}

package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentServiceTest {

    private Path writeAgent(Path agentsDir, String name, String content) throws Exception {
        var dir = Files.createDirectories(agentsDir.resolve(name));
        var file = dir.resolve("AGENT.md");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void discoversAgentDirsAndParsesFields(@TempDir Path tmp) throws Exception {
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
        var service = new AgentService(tmp);
        var agent = service.get("Docs-Assistent").orElseThrow();

        // THEN
        assertThat(service.getAgents()).hasSize(1);
        assertThat(agent.getName()).isEqualTo("Docs-Assistent");
        assertThat(agent.getDescription()).isEqualTo("only from docs");
        assertThat(agent.isReadOnly()).isTrue();
        assertThat(agent.getModel()).isEqualTo("qwen3");
        assertThat(agent.getTools()).containsExactly("grep", "read_");
        assertThat(agent.readBody()).isEqualTo("You are the docs assistant.");
    }

    @Test
    void absentToolsMeansAllTools(@TempDir Path tmp) throws Exception {
        // GIVEN — no tools field, name derived from directory
        writeAgent(tmp, "free", """
                ---
                description: anything goes
                ---
                body
                """);

        // WHEN
        var agent = new AgentService(tmp).get("free").orElseThrow();

        // THEN
        assertThat(agent.getName()).isEqualTo("free");
        assertThat(agent.getTools()).isNull();
        assertThat(agent.isReadOnly()).isFalse();
        assertThat(agent.getModel()).isNull();
    }

    @Test
    void ignoresDirectoriesWithoutAgentMd(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.createDirectories(tmp.resolve("not-an-agent"));
        writeAgent(tmp, "real", "---\nname: real\n---\nbody");

        // WHEN
        var service = new AgentService(tmp);

        // THEN
        assertThat(service.getAgents()).hasSize(1);
        assertThat(service.get("real")).isPresent();
    }

    @Test
    void refreshPicksUpEdits(@TempDir Path tmp) throws Exception {
        // GIVEN
        var file = writeAgent(tmp, "docs", "---\nname: docs\nmodel: a\n---\nbody");
        var service = new AgentService(tmp);
        assertThat(service.get("docs").orElseThrow().getModel()).isEqualTo("a");

        // WHEN
        Files.writeString(file, "---\nname: docs\nmodel: b\n---\nbody");
        service.refresh(tmp);

        // THEN
        assertThat(service.get("docs").orElseThrow().getModel()).isEqualTo("b");
    }
}

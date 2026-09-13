package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.prompt.PromptYmlParser;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.UserMessage;

/**
 * Wire proof for custom agents (AGENT.md frontmatter): the frontmatter's
 * url/api_key/model/temperature/think/extra_body reach the wire — the request lands at the
 * agent's own stub carrying the full per-agent payload, while the base stub stays empty.
 * Characterization of the existing frontmatter -> AgentConfig -> modelFor() -> wire path.
 */
class CustomAgentConnectionE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    private MockLlmServer baseStub;
    private MockLlmServer agentStub;

    @BeforeEach
    void setUp() {
        baseStub = new MockLlmServer();
        agentStub = new MockLlmServer();
        baseStub.start();
        agentStub.start();
    }

    @AfterEach
    void tearDown() {
        agentStub.stop();
        baseStub.stop();
    }

    @Test
    @Timeout(10)
    void frontmatterAgent_reachesOwnStub_withFullPayloadOnTheWire() throws IOException {
        // GIVEN — base points at baseStub; the AGENT.md frontmatter carries its own
        // url/api_key/model/temperature/think/extra_body
        var base = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI)
                .model("base-model")
                .url(baseStub.getUrl())
                .apiKey("test-key")
                .build();
        var agentFile = writeAgentMd(tmp.resolve("wire-agent.md"));
        var ccm = new ConfiguredChatModel(base);
        var toolService = new ToolService(false);
        var agent = new CustomAgent(PromptYmlParser.parseYml(agentFile), ccm, toolService);
        agentStub.queueResponse("agent answer");

        // WHEN — one user turn through the tool loop with the frontmatter-derived agent config
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));
        var response = toolService.executeLoop(
                ToolLoopRequest.builder()
                        .memory(memory)
                        .chatModel(ccm)
                        .agentConfig(agent.getConfig())
                        .build());

        // THEN — the request landed at the agent's own stub with the full per-agent payload
        assertThat(response.aiMessage().text()).isEqualTo("agent answer");
        assertThat(baseStub.getLastRequestBody()).isNull();
        var body = parse(agentStub.getLastRequestBody());
        assertThat(body.path("model").asText()).isEqualTo("custom-model");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.3);
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("high");
        assertThat(body.path("foo").asText()).isEqualTo("bar");

        // AND — the reserved model key from the extra body was stripped (slot model wins)
        assertThat(agentStub.getLastRequestBody().split("\\\"model\\\"", -1)).hasSize(2);
    }

    private Path writeAgentMd(Path file) throws IOException {
        Files.writeString(file, """
                ---
                name: wire-agent
                url: %s
                api_key: sk-agent
                model: custom-model
                temperature: 0.3
                think_supported: true
                think_on_string: high
                extra_body: '{"foo":"bar","model":"hacked"}'
                ---
                You are a test agent.
                """.formatted(agentStub.getUrl()));
        return file;
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse captured request body: " + body, e);
        }
    }
}

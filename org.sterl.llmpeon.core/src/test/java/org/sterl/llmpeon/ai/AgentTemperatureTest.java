package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;

class AgentTemperatureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockLlmServer server;

    @BeforeEach
    void setUp() {
        server = new MockLlmServer();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void emptyMeansUnset() {
        for (var raw : new String[] {null, "", " "}) {
            var cfg = AgentConfig.builder().provider(AiProvider.OPEN_AI)
                    .temperature(AgentTemperature.parse(raw)).build();

            assertThat(LlmProviders.of(AiProvider.OPEN_AI)
                    .newRequestParameters(cfg, List.of()).temperature()).as("raw=%s", raw).isNull();
        }
    }

    @Test
    void setsConfiguredValue() {
        var cfg = AgentConfig.builder().provider(AiProvider.OPEN_AI).temperature(0.2).build();

        assertThat(LlmProviders.of(AiProvider.OPEN_AI)
                .newRequestParameters(cfg, List.of()).temperature()).isEqualTo(0.2);
    }

    @Test
    void invalidValueWarnsAndIgnores() {
        assertThat(AgentTemperature.parse("abc")).isNull();
        assertThat(AgentTemperature.parse("1,5")).isNull();
        assertThat(AgentTemperature.parse(" ")).isNull();
        assertThat(AgentTemperature.parse(null)).isNull();
        assertThat(AgentTemperature.parse("0")).isEqualTo(0.0);
        assertThat(AgentTemperature.parse("0.0")).isEqualTo(0.0);
        assertThat(AgentTemperature.parse("2")).isEqualTo(2.0);
    }

    @Test
    void bodyWinsOverField() {
        var cfg = AgentConfig.builder().provider(AiProvider.OPEN_AI).temperature(0.2)
                .extraBody("{\"temperature\":0.9}").build();
        var params = (OpenAiChatRequestParameters) LlmProviders.of(AiProvider.OPEN_AI)
                .newRequestParameters(cfg, List.of());

        assertThat(params.temperature()).isNull();
        assertThat(params.customParameters()).containsEntry("temperature", 0.9);
    }

    @Test
    void unsetIsAbsentFromWireBody() throws Exception {
        runRequest(AgentConfig.builder().provider(AiProvider.OPEN_AI).build());

        assertThat(MAPPER.readTree(server.getLastRequestBody()).has("temperature")).isFalse();
    }

    @Test
    void bodyTemperatureWinsOnTheWire() throws Exception {
        runRequest(AgentConfig.builder().provider(AiProvider.OPEN_AI).temperature(0.2)
                .extraBody("{\"temperature\":0.9}").build());

        var rawBody = server.getLastRequestBody();
        assertThat(MAPPER.readTree(rawBody).path("temperature").asDouble()).isEqualTo(0.9);
        assertThat(rawBody.split("\\\"temperature\\\"", -1)).hasSize(2);
    }

    private void runRequest(AgentConfig agent) {
        server.queueResponse("ok");
        var config = LlmConfig.builder().providerType(AiProvider.OPEN_AI).model("base-model")
                .url(server.getUrl()).apiKey("test-key").build();
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));

        new ToolService(false).executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(config))
                .agentConfig(agent)
                .build());
    }
}

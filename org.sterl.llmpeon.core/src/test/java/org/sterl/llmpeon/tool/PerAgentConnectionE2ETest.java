package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.mock.MockLlmServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * E2E proof (Night-Cycle A, S1/S2): the per-agent model config routes the tool loop's request to
 * the configured URL, and the per-agent config (model, think, extraBody) arrives on the wire —
 * real HTTP against one dynamic-port stub per role (base/agent), one provider per matrix row.
 */
@Timeout(30)
class PerAgentConnectionE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * One provider matrix row (plan §D4): the agent's model/think/extraBody plus the wire
     * expectations as consumers over the captured request body.
     */
    record Variant(String name, AiProvider provider, String model, String think, String extraBody,
                   Consumer<JsonNode> expectThink, Consumer<JsonNode> expectExtraBody) {}

    static Stream<Arguments> variants() {
        return Stream.of(
                // PER_REQUEST: think → reasoning_effort; extraBody merged per request (user wins, reserved keys stripped)
                Arguments.of(new Variant("openai", AiProvider.OPEN_AI, "claude-mock", "medium",
                        "{\"foo\":\"bar\",\"cache_control\":{\"type\":\"user-wins\"},\"model\":\"hacked\"}",
                        body -> assertThat(body.path("reasoning_effort").asText()).isEqualTo("medium"),
                        body -> {
                            assertThat(body.path("foo").asText()).isEqualTo("bar");
                            assertThat(body.path("cache_control").path("type").asText()).isEqualTo("user-wins");
                        })),
                // BUILD_TIME: think → thinking{type,budget_tokens}; extraBody baked into the model
                Arguments.of(new Variant("anthropic", AiProvider.ANTHROPIC, "claude-mock", "enabled",
                        "{\"foo\":\"bar\",\"model\":\"hacked\"}",
                        body -> {
                            assertThat(body.path("thinking").path("type").asText()).isEqualTo("enabled");
                            assertThat(body.path("thinking").path("budget_tokens").asInt()).isEqualTo(8000);
                        },
                        body -> assertThat(body.path("foo").asText()).isEqualTo("bar"))),
                // NONE: think → think:true; extraBody ignored
                Arguments.of(new Variant("ollama", AiProvider.OLLAMA, "llama-mock", "true",
                        "{\"foo\":\"bar\"}",
                        body -> assertThat(body.path("think").asBoolean()).isTrue(),
                        body -> assertThat(body.has("foo"))
                                .as("extraBody mode NONE: user body must not reach the wire").isFalse())));
    }

    /** S1 — the agent inherits the base connection: the request lands at the base stub, but the agent's model + think are on the wire. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("variants")
    void inheritsBaseUrl_landsAtBaseStub_withAgentModelAndThink(Variant v) {
        // GIVEN — base points at baseStub; the agent carries its own model + think but no url/key/extraBody
        baseStub.queueResponse("inherited");
        var ccm = new ConfiguredChatModel(baseConfig(v));
        var agent = AgentConfig.builder()
                .provider(v.provider())
                .model(v.model())
                .think(v.think())
                .build();

        // WHEN — one user turn through the tool loop
        var response = runLoop(ccm, agent);

        // THEN — the request landed at the base stub, and the agent's model + think are on the wire
        assertThat(response.aiMessage().text()).isEqualTo("inherited");
        assertThat(agentStub.getLastRequestBody()).isNull();
        var body = parse(baseStub.getLastRequestBody());
        assertThat(body.path("model").asText()).isEqualTo(v.model());
        v.expectThink().accept(body);
    }

    /** S2 — the agent carries its own URL: the request lands at the agent stub, and the full per-agent config is on the wire. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("variants")
    void agentOwnUrl_landsAtAgentStub_withConfigOnTheWire(Variant v) {
        // GIVEN — base points at baseStub; the agent carries its own url + model + think + extraBody
        agentStub.queueResponse("own");
        var ccm = new ConfiguredChatModel(baseConfig(v));
        var agent = AgentConfig.builder()
                .provider(v.provider())
                .url(stubUrl(v.provider(), agentStub))
                .model(v.model())
                .think(v.think())
                .extraBody(v.extraBody())
                .build();

        // WHEN — one user turn through the tool loop
        var response = runLoop(ccm, agent);

        // THEN — the request landed at the agent stub, and the agent's model + think + extraBody are on the wire
        assertThat(response.aiMessage().text()).isEqualTo("own");
        assertThat(baseStub.getLastRequestBody()).isNull();
        var body = parse(agentStub.getLastRequestBody());
        assertThat(body.path("model").asText()).isEqualTo(v.model());
        v.expectThink().accept(body);
        v.expectExtraBody().accept(body);
    }

    private LlmConfig baseConfig(Variant v) {
        return LlmConfig.builder()
                .providerType(v.provider())
                .model("base-model")
                .url(stubUrl(v.provider(), baseStub))
                .apiKey("test-key")
                .build();
    }

    private ChatResponse runLoop(ConfiguredChatModel ccm, AgentConfig agent) {
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));
        return new ToolService(false).executeLoop(
                ToolLoopRequest.builder()
                        .memory(memory)
                        .chatModel(ccm)
                        .agentConfig(agent)
                        .build());
    }

    /** Ollama speaks at the root ({@code /api/chat}); the other providers under {@code /v1}. */
    private static String stubUrl(AiProvider provider, MockLlmServer stub) {
        return provider == AiProvider.OLLAMA ? stub.rootUrl() : stub.getUrl();
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse captured request body: " + body, e);
        }
    }
}

package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.mock.MockLlmServer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Wiring test (plan 2a, inc 7): the tool loop resolves the model via
 * {@link ConfiguredChatModel#modelFor(AgentConfig)} — an agent carrying its own url talks to its
 * own endpoint, not the base one (ADR-0034).
 */
class ToolLoopRequestConnectionTest {

    private final MockLlmServer agentServer = new MockLlmServer();
    private final MockLlmServer baseServer = new MockLlmServer();

    @AfterEach
    void tearDown() {
        agentServer.stop();
        baseServer.stop();
    }

    @Test
    @Timeout(30)
    void toolLoopUsesAgentConnection() {
        // GIVEN — base points at baseServer, the agent carries its own url (agentServer)
        agentServer.start();
        baseServer.start();
        var base = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI)
                .model("mock-model")
                .url(baseServer.getUrl())
                .build();
        var ccm = new ConfiguredChatModel(base);
        var agent = AgentConfig.builder()
                .provider(AiProvider.OPEN_AI)
                .model("mock-model")
                .url(agentServer.getUrl())
                .build();
        agentServer.queueResponse(AiMessage.from("Hello from agent server"));
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));

        // WHEN — run the loop with the agent config
        var response = new ToolService(false).executeLoop(
                ToolLoopRequest.builder()
                        .memory(memory)
                        .chatModel(ccm)
                        .agentConfig(agent)
                        .build());

        // THEN — the request landed at the agent server, not the base server
        assertThat(response.aiMessage().text()).isEqualTo("Hello from agent server");
        assertThat(agentServer.getLastRequestBody()).isNotNull();
        assertThat(baseServer.getLastRequestBody()).isNull();
    }
}

package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.Picture;
import org.sterl.llmpeon.shared.PictureMessage;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/** R-RP-4 through the real compact path (AbstractAgent.compact → CompactService). */
class PictureCompactTest {

    private static final String BASE64 = "QkFTRTY0LUJJTEQtREFURQ==";
    private static final String PATH = "/work/ui/login.png";

    // UC-RP-5
    @Test
    @org.junit.jupiter.api.Timeout(10)
    void compactSendsAPlaceholderNotTheBytesAndLeavesNoPictureBehind() {
        // GIVEN — a memory with a picture user message
        var streamMock = new StreamMock();
        var cm = streamMock.buildMock(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("WHAT: summary")).build());
        var agent = new AiDevAgent(new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), cm), new ToolService());
        agent.addMessage(UserMessage.from("look at the login"));
        agent.addMessage(AiMessage.from("ok"));
        agent.addMessage(PictureMessage.of(new Picture(PATH, "image/png", BASE64, 16)));

        // WHEN
        var result = agent.compact(AiMonitor.NULL_MONITOR);

        // THEN — the compressor input names the picture and carries no image and no bytes
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        var request = streamMock.getLastRequest().messages();
        assertThat(request.stream().filter(PictureCompactTest::hasImage)).isEmpty();
        assertThat(streamMock.allAsString()).anyMatch(s -> s.contains("[picture removed for compact: " + PATH + "]"));
        assertThat(String.join("", streamMock.allAsString())).doesNotContain(BASE64);

        // AND — no picture survives the compact
        assertThat(agent.getMemory().getCopy().stream().filter(PictureCompactTest::hasImage)).isEmpty();
    }

    // UC-RP-5
    @Test
    void estimateCountsAPictureAs1600Tokens() {
        var picture = PictureMessage.of(new Picture(PATH, "image/png", BASE64, 16));

        assertThat(ChatMessageUtil.estimateTokens(List.<ChatMessage>of(picture))).isGreaterThanOrEqualTo(1600);
    }

    private static boolean hasImage(ChatMessage m) {
        return m instanceof UserMessage um && um.contents().stream().anyMatch(c -> c instanceof ImageContent);
    }
}

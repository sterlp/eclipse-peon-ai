package org.sterl.llmpeon.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.LlmConfig;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

class VoiceInputServiceTest {

    private VoiceInputService subject = new VoiceInputService();
    
    byte[] wav;
    
    @BeforeEach
    void before() throws IOException {
        wav = Files.readAllBytes(Path.of("./test ping.wav"));
        assertTrue(wav.length > 10000);
    }

    // https://ai.google.dev/gemma/docs/core/model_card_4?hl=de
    @Test
    @Tag("integration")
    void testTestPing_Ollama() throws Exception {
        // GIVEN
        var cfg = new VoiceConfig(
                true,
                "gemma4:e2b",
                "/v1/audio/transcriptions",
                "http://localhost:11434",
                null, 
                "de",
                null);

        // WHEN
        var result = subject.transcribe(wav, cfg);

        // THEN
        System.err.println(result);
        assertThat(result).containsIgnoringCase("test").containsIgnoringCase("ping");
    }

    @Test
    @Tag("integration")
    void testTestPing_LM_Studio() throws Exception {
        // GIVEN
        var cfg = new VoiceConfig(
                true,
                "google/gemma-4-e2b", // whisper-large-v3
                "/v1/audio/transcriptions",
                "http://localhost:1234",
                null, 
                "de",
                null);
        
        
        // WHEN
        var result = subject.transcribe(wav, cfg);

        // THEN
        System.err.println(result);
        assertThat(result).containsIgnoringCase("test").containsIgnoringCase("ping");
    }

    @Test
    @Tag("integration") // doesn't work in LM Studio
    void testTestPing_Langchain_LM_Studio() throws Exception {
        // GIVEN
        var chatModel = LlmConfig.newLmStudio("google/gemma-4-e2b").build();
        
        // WHEN
        var audio = Audio.builder()
            .binaryData(wav)
            .base64Data(Base64.getEncoder().encodeToString(wav))
            .mimeType("audio/wav")
            .build();

        var response = chatModel.callBlocking(
                new ChatRequest.Builder()
                    .messages(UserMessage.from(
                            AudioContent.from(audio),
                            TextContent.from("Write a transcription of this audio file")
                        )
                    ).build(), null
            );

        // THEN
        System.err.println(response.aiMessage());
        System.err.println(response.tokenUsage());
    }

    @Test
    @Tag("integration") // Langchain4j doesn't support it
    void testTestPing_Langchain_Ollama() throws Exception {
        // GIVEN
        var chatModel = LlmConfig.newOllama("gemma-4-e2b").build();

        // WHEN
        var audio = Audio.builder()
            .binaryData(wav)
            .base64Data(Base64.getEncoder().encodeToString(wav))
            .mimeType("audio/wav")
            .build();

        var response = chatModel.callBlocking(
                new ChatRequest.Builder()
                    .messages(UserMessage.from(
                            AudioContent.from(audio),
                            TextContent.from("Write a transcription of this audio file")
                        )
                    ).build(), null
            );

        // THEN
        System.err.println(response.aiMessage());
        System.err.println(response.tokenUsage());
    }

}

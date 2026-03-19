package org.sterl.llmpeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ChatService;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

class ChatServiceTest {

    @Test
    void test() {
        assertEquals("", ChatService.trimArgs("{}"));
        assertEquals("args0: Fooobar.java", ChatService.trimArgs("{args0: Fooobar.java}"));
    }
    
    @Tag("integration")
    @Test
    void ping_lm_stutio_test() {
        var config = LlmConfig.newConfig(AiProvider.LM_STUDIO, "qwen/qwen3.5-9b", "http://localhost:1234/v1");
        var subject = new ChatService<TemplateContext>(config, new ToolService(), new SkillService(), new TemplateContext(Path.of(".")));
        
        var result = subject.call("Ping", m -> System.err.println(m));

        System.err.println(result.aiMessage());
    }
    
    @Tag("integration")
    @Test
    void ping_langchain4j_lm_studio() {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);

        JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
                .httpClientBuilder(httpClientBuilder);
            
        var subject = OpenAiChatModel.builder()
            .timeout(Duration.ofMinutes(2))
            .baseUrl("http://172.25.30.242:1234/v1")
            .modelName("qwen/qwen3.5-9b")
            .httpClientBuilder(jdkHttpClientBuilder)
            .logRequests(true)
            .logResponses(true)
            .strictTools(true)
            .build();
        
        var response = subject.chat("say hello");

        System.err.println(response);
    }

    @Tag("integration")
    @Test
    void ping_langchain4j_lm_studio_streaming() throws Exception {
        var httpClientBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
        var jdkHttpClientBuilder = JdkHttpClient.builder().httpClientBuilder(httpClientBuilder);

        var subject = OpenAiStreamingChatModel.builder()
            .timeout(Duration.ofSeconds(60))
            .baseUrl("http://localhost:1234/v1")
            .modelName("qwen/qwen3.5-9b")
            .httpClientBuilder(jdkHttpClientBuilder)
            .logRequests(true)
            .logResponses(true)
            .build();

        var toolService = new ToolService();
        var future = new CompletableFuture<AiMessage>();
        var request = ChatRequest.builder()
                .messages(UserMessage.from("Call the webfetchtool with http://localhost/v1"))
                .toolSpecifications(toolService.toolSpecifications())
                .build();
        
        subject.chat(request, 
                new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                System.err.print(token);
            }
            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                System.err.println();
                future.complete(response.aiMessage());
            }
            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        var result = future.get(2, TimeUnit.MINUTES);
        System.err.println("Result: " + result);
    }

}

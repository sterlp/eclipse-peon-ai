package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.open.aifactory.FactorySpringTest;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * UC-WP-64 over the real edge: a turn that cannot start is answered with 202 and with nothing live-only —
 * its two lines are on {@code GET …/messages}, which is what a reload, a second browser and the next turn
 * read (R-WP-34).
 *
 * <p>{@code ChatTurnTest#configReadFromFactoryOwnStore} already proves the live {@code error} event of an
 * incomplete config; this class is the other half of the same failure — the stored conversation. The config
 * is incomplete from the first read on, so nothing has to be taken away mid-test and no provider double is
 * needed: the turn never reaches a model, which is exactly the case that used to leave no trace.</p>
 */
@Timeout(120)
class ChatTurnFailureTest extends FactorySpringTest {

    private static final String INPUT = "BAUE: Wo legt die Fabrik ihren Plan ab?";
    private static final String MISSING_MODEL_HINT = "turn needs a model";

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    private RestTemplate http;

    @BeforeEach
    void startClient() {
        this.http = new RestTemplate();
        // the status IS the assertion — a client that throws would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
    }

    @Test
    // UC-WP-64
    void sendWithoutConfigShowsTheTurnFailInTheHistory() throws Exception {
        writeFactoryConfigWithoutAModel();
        String projectId = createProject("turn-fails-without-a-model");

        assertThat(http.postForEntity(messagesUrl(projectId), jsonEntity(new SendMessageRequest(INPUT, "Paul")),
                String.class).getStatusCode())
                .as("the input is accepted, its failure is a line and not a status")
                .isEqualTo(HttpStatus.ACCEPTED);

        awaitUntil(() -> messages(projectId).size() == 2, 20_000, "the failed turn wrote its two lines");
        List<ChatMessageDto> history = messages(projectId);
        assertThat(history).extracting(ChatMessageDto::role).containsExactly("user", "error");
        assertThat(history.get(0).text()).isEqualTo(INPUT);
        assertThat(history.get(0).origin())
                .as("the typed line keeps the name it was sent with")
                .isEqualTo("Paul");
        assertThat(history.get(1).text())
                .as("the failure line names the missing field, the way the live line does")
                .contains(MISSING_MODEL_HINT);
        assertThat(history.get(1).origin())
                .as("and no one is named for it — neither Jon nor a member")
                .isNull();

        assertThat(runtimes.runtime(projectId).jon().getMemory().getCopy())
                .as("the lines are in the core's memory, so the next turn and a restart both read them")
                .anySatisfy(message -> assertThat(String.valueOf(message)).contains(MISSING_MODEL_HINT));
        assertThat(history)
                .as("reading twice shows the same two lines, never a second copy")
                .isEqualTo(messages(projectId));
    }

    @Test
    // UC-WP-64
    void failedTurnLeavesTheProjectFreeAgain() throws Exception {
        writeFactoryConfigWithoutAModel();
        String projectId = createProject("turn-fails-and-frees-the-project");
        http.postForEntity(messagesUrl(projectId), jsonEntity(new SendMessageRequest(INPUT, "Paul")), String.class);
        awaitUntil(() -> messages(projectId).size() == 2, 20_000, "the failed turn wrote its two lines");

        assertThat(statusOf(HttpMethod.POST, "/api/projects/" + projectId + "/messages/stop", null))
                .as("the turn that failed is over, so a stop after it has nothing to stop")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------ the three edges

    private String messagesUrl(String projectId) {
        return "http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages";
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange(messagesUrl(projectId), HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private HttpStatus statusOf(HttpMethod method, String path, Object body) {
        return (HttpStatus) http.exchange("http://127.0.0.1:" + port + path, method,
                body == null ? HttpEntity.EMPTY : jsonEntity(body), String.class).getStatusCode();
    }

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String createProject(String slug) {
        ResponseEntity<String> created = http.postForEntity("http://127.0.0.1:" + port + "/api/projects",
                jsonEntity(Map.of("gitUrl", "https://git.example.com/acme/" + slug + ".git")), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JsonPath.read(created.getBody(), "$.id");
    }

    /**
     * A config that names a provider and a key but no model: the turn is refused over the model, which is
     * the first field the turn read asks for. The factory's own file — never {@code ~/.peon}.
     */
    private static void writeFactoryConfigWithoutAModel() throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, "");
        properties.setProperty(LlmConfigKeys.URL, "http://127.0.0.1:1/v1");
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(HOME.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "ChatTurnFailureTest");
        }
    }

    private static void awaitUntil(BooleanSupplier condition, long timeoutMs, String what) {
        long until = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis())
                    .as("%s happened within %d ms", what, timeoutMs)
                    .isLessThan(until);
            sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the turn", interrupted);
        }
    }
}

package org.sterl.open.aifactory.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import org.sterl.open.aifactory.FactorySpringTest;

/**
 * The contract of the error channel itself, before any field rule exists on the wire (R-WP-55, C-8):
 * what an answer looks like when nothing was validated, and which status a refused live edge keeps.
 *
 * <p>Real HTTP in the shared context — the refusal of an SSE edge is exactly the case a mocked servlet
 * layer cannot show, because the lost status comes out of content negotiation and not out of a
 * handler ({@code aifactory/AGENTS-DEV.md} — one context per build).</p>
 */
class ApiErrorContractTest extends FactorySpringTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String EVENT_STREAM = "text/event-stream";

    @LocalServerPort
    int port;

    // C-8
    @Test
    void anSseEdgeKeepsItsRejectedStatus() throws Exception {
        HttpResponse<String> refused = get("/api/projects/nope-1234567/events", EVENT_STREAM);

        assertThat(refused.statusCode())
                .as("a live edge that is refused says why it is refused, and does not become a 500")
                .isEqualTo(404);
        assertThat(refused.body())
                .as("a wire that accepts no JSON is answered without a body, so nothing has to be negotiated")
                .isEmpty();
    }

    // R-WP-55
    @Test
    void aPlainErrorBodyCarriesNoFieldErrors() throws Exception {
        HttpResponse<String> refused = put("/api/settings", "{ this is no json");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(number(refused, "$.status")).isEqualTo(400);
        assertThat(text(refused, "$.error")).isNotBlank();
        assertThat(text(refused, "$.message")).as("the readable reason stays where the UI prints it")
                .isNotBlank();
        assertThat(fieldErrors(refused))
                .as("nothing was validated, so no field is named")
                .isNull();
    }

    // R-WP-55
    @Test
    void withoutOurAdviceSpringAnswersItsOwnErrorBody() throws Exception {
        HttpResponse<String> unmapped = get("/api/nothing-is-mapped-here", "application/json");

        assertThat(unmapped.statusCode()).isEqualTo(404);
        // measured 2026-11-09: {"timestamp":…,"status":404,"error":"Not Found","path":…} — neither a reason
        // nor a field, which is why the advice is the contract and stays (R-WP-55, open-points ⏳)
        assertThat(unmapped.body())
                .as("Spring answers its own body here, and it is not the factory's")
                .doesNotContain("fieldErrors")
                .doesNotContain("\"message\"");
    }

    // ------------------------------------------------------------------ the wire

    private HttpResponse<String> get(String path, String accept) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).header("Accept", accept));
    }

    private HttpResponse<String> put(String path, String body) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request)
            throws IOException, InterruptedException {
        return CLIENT.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String text(HttpResponse<String> answer, String jsonPath) {
        Object value = JsonPath.read(answer.body(), jsonPath);
        return String.valueOf(value);
    }

    private static int number(HttpResponse<String> answer, String jsonPath) {
        return ((Number) JsonPath.read(answer.body(), jsonPath)).intValue();
    }

    /** {@code null} when the answer names no field at all — an absent entry and an empty one say the same. */
    private static Object fieldErrors(HttpResponse<String> answer) {
        try {
            return JsonPath.read(answer.body(), "$.fieldErrors");
        } catch (PathNotFoundException absent) {
            return null;
        }
    }
}

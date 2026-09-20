package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.tool.tools.WebGetTool;

import com.sun.net.httpserver.HttpServer;

/**
 * webGet (docs/web-tools.md R-WEB-4…8): download to disk, metadata only in the return,
 * same guards as diskWriteFile, honest zero-byte and HTTP-error behaviour.
 */
@Timeout(30)
class WebGetToolTest {

    @TempDir
    Path tempDir;

    WebGetTool tool;
    HttpServer server;
    String base;

    @BeforeEach
    void setUp() throws IOException {
        tool = new WebGetTool();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        base = "http://localhost:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serve(String context, int status, byte[] body) {
        server.createContext(context, exchange -> {
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try {
                    exchange.getResponseBody().write(body);
                } catch (IOException ignored) {
                    // test server — client already got the headers
                }
            }
            exchange.close();
        });
    }

    // UC-WEB-5
    @Test
    void webGetDownloadsAndReturnsMetadata() throws Exception {
        serve("/file", 200, "hello webget".getBytes(StandardCharsets.UTF_8));
        Path target = tempDir.resolve("sub/download.bin");

        String result = tool.webGet(base + "/file", target.toString());

        // THEN file on disk, return names status + size + path — never the content
        assertEquals("hello webget", Files.readString(target));
        assertThat(result)
                .contains("HTTP 200")
                .contains("12 bytes")
                .contains(target.toString());
        assertThat(result).doesNotContain("hello webget");
    }

    // UC-WEB-6
    @Test
    void webGetEnforcesWriteGuards() throws Exception {
        serve("/file", 200, "x".getBytes(StandardCharsets.UTF_8));
        tool.withToolRequest(docsRequest());

        // GIVEN an absolute path outside the docs allowlist WHEN webGet THEN denial, nothing written
        Path denied = tempDir.resolve("src/Foo.java");
        var error = assertThrows(IllegalArgumentException.class,
                () -> tool.webGet(base + "/file", denied.toString()));
        assertThat(error.getMessage()).contains("Write denied");
        assertFalse(Files.exists(denied));

        // AND GIVEN a relative path WHEN webGet THEN qualified-path contract error
        var relative = assertThrows(IllegalArgumentException.class,
                () -> tool.webGet(base + "/file", "relative/file.bin"));
        assertThat(relative.getMessage()).contains("must be fully qualified");
    }

    // UC-WEB-7
    @Test
    void webGetHonestZeroBytes() throws Exception {
        serve("/empty", 200, new byte[0]);
        Path target = tempDir.resolve("empty.bin");

        String result = tool.webGet(base + "/empty", target.toString());

        // THEN honest report (status + 0 bytes), file exists as empty file
        assertTrue(Files.exists(target));
        assertEquals(0, Files.size(target));
        assertThat(result)
                .contains("HTTP 200")
                .contains("0 bytes — server returned an empty body");
    }

    // UC-WEB-8
    @Test
    void webGetIsEditTool() {
        assertThat(new WebGetTool().isEditTool()).isTrue();
    }

    @Test
    void webGetHttpErrorThrowsWithoutPartialFile() throws Exception {
        serve("/missing", 500, "internal server error body".getBytes(StandardCharsets.UTF_8));
        Path target = tempDir.resolve("missing.bin");

        var error = assertThrows(IllegalArgumentException.class,
                () -> tool.webGet(base + "/missing", target.toString()));

        // R-WEB-7: honest error with the status code, never a partial file
        assertThat(error.getMessage()).contains("HTTP 500").contains(base + "/missing");
        assertFalse(Files.exists(target));
    }

    private ToolLoopRequest docsRequest() {
        var model = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .writeValidator(WriteValidator.DOCS)
                .build();
    }
}

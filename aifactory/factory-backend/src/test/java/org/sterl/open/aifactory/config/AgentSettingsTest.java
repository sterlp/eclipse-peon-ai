package org.sterl.open.aifactory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sterl.open.aifactory.config.FactoryConfigServiceTest.componentNamesOf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.open.aifactory.FactorySpringTest;
import org.sterl.open.aifactory.config.agent.AgentConfigFile;
import org.sterl.open.aifactory.config.dto.AgentSettingsDto;
import org.sterl.open.aifactory.config.dto.AgentSettingsUpdateRequest;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The agent section over real HTTP (R-WP-23): the slot's file is the whole storage, so every assertion
 * ends in the bytes of {@code agents/<id>/AGENT.md} — the answer alone could still be an echo of the
 * request. Masking is checked on the raw body for the same reason it is checked there for the default
 * level (R-WP-16).
 */
@Timeout(60)
class AgentSettingsTest extends FactorySpringTest {

    private static final String KEY = "sk-agent-secret-0123456789";
    private static final String BODY = "This is the slot's own prompt.";

    @LocalServerPort
    int port;

    @Autowired
    FactoryConfigService configService;

    private RestTemplate http;

    /** The shared context already emptied the home (see {@link FactorySpringTest#resetSharedState()}). */
    @BeforeEach
    void talkToTheRunningServer() {
        this.http = new RestTemplate();
        // the status IS the assertion — a client that throws would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
    }

    /**
     * Q9: exactly the slots the factory runs, in the doc's order, from one source. Reading them creates
     * nothing — no file, no {@code agents} folder, no config file.
     */
    @Test
    void theSixSlotsAnswerInDocOrder() {
        String body = bodyOfAgents();

        assertThat(values(body, "$[*].id")).containsExactly("po", "plan", "dev", "review", "search", "compact");
        assertThat(values(body, "$[*].name")).containsExactly("Jon", "Plan", "Dev", "Review", "Search", "Compact");
        assertThat(values(body, "$[*].overridden")).containsOnly(false);
        assertThat(HOME.resolve("agents")).doesNotExist();
        assertThat(HOME.resolve(FactoryHome.CONFIG_FILE)).doesNotExist();

        // no field a key could sit in — an added component fails whatever it is called
        assertThat(componentNamesOf(AgentSettingsDto.class)).containsExactly("id", "name", "overridden", "model",
                "url", "think", "temperature", "extraBody", "tools", "keySet");
        assertThat(componentNamesOf(AgentSettingsUpdateRequest.class)).containsExactly("overridden", "model", "url",
                "think", "temperature", "extraBody", "tools", "apiKey", "removeApiKey");
    }

    /** UC-WP-44 GIVEN 1: a file that carries no config field shows no fields, and opening the page leaves it alone. */
    @Test
    // UC-WP-44
    void anEmptyFileReportsNoOverrides() throws IOException {
        write("dev", "---\nname: dev\n---\n" + BODY);
        byte[] untouched = Files.readAllBytes(agentFile("dev"));

        Map<String, Object> dev = slot(bodyOfAgents(), "dev");

        assertThat(dev).containsEntry("overridden", false).containsEntry("keySet", false);
        // "keine Dev-Felder sind sichtbar": no field of the slot carries a value
        assertThat(dev.get("model")).isNull();
        assertThat(dev.get("url")).isNull();
        assertThat(dev.get("think")).isNull();
        assertThat(dev.get("temperature")).isNull();
        assertThat(Files.readAllBytes(agentFile("dev"))).containsExactly(untouched);
    }

    /**
     * UC-WP-44 GIVEN 2: one field entered is one field stored — the file carries nothing else, and the
     * next save that names no other field keeps it (R-WP-16's three states, one slot at a time).
     */
    @Test
    // UC-WP-44
    void savingAnOverrideWritesOnlyThatField() {
        ResponseEntity<String> saved = put("dev", "{\"overridden\":true,\"model\":\"gpt-5\"}");

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(slot(saved.getBody(), "dev")).containsEntry("overridden", true).containsEntry("model", "gpt-5");
        assertThat(text("dev")).isEqualTo("""
                ---
                model: gpt-5
                ---
                """);

        // the answer is the file, not the body: a field this request never named still reads as stored
        assertThat(put("dev", "{\"overridden\":true,\"url\":\"https://dev.example\"}").getBody())
                .contains("\"model\":\"gpt-5\"");
        assertThat(text("dev")).contains("model: gpt-5").contains("url: https://dev.example");
    }

    /**
     * UC-WP-45: the unchecked box clears the whole slot — every config value, now including the two
     * fields of R-WP-52 — while what the page never shows (read-only, body) stays verbatim and the next
     * read reports no override.
     */
    @Test
    // UC-WP-45
    void uncheckingTheBoxClearsEveryValueOfTheSlot() {
        write("dev", """
                ---
                model: gpt-5
                api_key: %s
                think: high
                tools: read, grep
                read-only: true
                ---
                """.formatted(KEY) + BODY);

        ResponseEntity<String> saved = put("dev", "{\"overridden\":false}");

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(slot(saved.getBody(), "dev")).containsEntry("overridden", false).containsEntry("keySet", false);
        String stored = text("dev");
        assertThat(stored).doesNotContain("model", "api_key", "think", "tools", KEY, "sk-");
        assertThat(stored).contains("read-only: true").contains(BODY);
    }

    /** R-WP-16 for a slot: the wire carries the fact that a key is set and never the key itself. */
    @Test
    // R-WP-16
    void anAgentKeyIsOnlyReportedAsSet() {
        ResponseEntity<String> saved = put("po", "{\"overridden\":true,\"apiKey\":\"%s\"}".formatted(KEY));
        String body = saved.getBody();

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(slot(body, "po")).containsEntry("keySet", true);
        assertThat(body).doesNotContain(KEY, KEY.substring(0, 6), KEY.substring(KEY.length() - 6), "sk-");
        assertThat(text("po")).contains("api_key: " + KEY);

        // an empty key field is no value — the stored key survives the next save of another field
        assertThat(put("po", "{\"overridden\":true,\"model\":\"gpt-5\"}").getBody()).doesNotContain(KEY, "sk-");
        assertThat(text("po")).contains("api_key: " + KEY).contains("model: gpt-5");

        // removing it is its own intent, and it reaches the file
        assertThat(put("po", "{\"overridden\":true,\"removeApiKey\":true}").getBody())
                .contains("\"keySet\":false");
        assertThat(text("po")).doesNotContain("api_key", KEY, "sk-");
    }

    /** A slot the factory does not run has no file to write, and the refusal does not repeat the id. */
    @Test
    void anUnknownSlotIsRefusedAndNothingIsWritten() {
        ResponseEntity<String> rejected = put("paco", "{\"overridden\":true,\"model\":\"gpt-5\"}");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody()).contains("agent").doesNotContain("paco");
        assertThat(HOME.resolve("agents")).doesNotExist();
    }

    /** R-WP-16's one-intent rule, same as the default level: setting and removing in one request is a 400. */
    @Test
    // R-WP-16
    void anAgentKeyCannotBeSetAndRemovedAtOnce() {
        ResponseEntity<String> rejected = put("review",
                "{\"overridden\":true,\"apiKey\":\"%s\",\"removeApiKey\":true}".formatted(KEY));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody()).doesNotContain(KEY, "sk-");
        assertThat(HOME.resolve("agents")).doesNotExist();
    }

    /** The core reads a temperature as a number and only warns when it cannot — so the edge refuses. */
    @Test
    void aTemperatureThatIsNotANumberIsRefused() {
        ResponseEntity<String> rejected = put("plan", "{\"overridden\":true,\"temperature\":\"hot\"}");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody()).contains("temperature").doesNotContain("hot");
        assertThat(HOME.resolve("agents")).doesNotExist();
    }

    /**
     * D-11's effect: what the file says is what the loaded config carries — no restart, no runtime cache,
     * and a slot whose file carries nothing is never put on the config, so the core's own inheritance
     * stays the rule.
     */
    @Test
    void theFileOverridesTheLoadedConfig() {
        write("po", "---\nmodel: gpt-5\nthink: high\ntemperature: 0.3\n---\n" + BODY);

        LlmConfig loaded = configService.load();
        AgentModelConfig po = loaded.modelConfigFor("po");

        assertThat(po.model()).isEqualTo("gpt-5");
        assertThat(po.think()).isEqualTo("high");
        assertThat(po.temperature()).isEqualTo("0.3");
        // an empty field inherits: no url in the file means the base url, not an empty one
        assertThat(po.url()).isNull();
        assertThat(loaded.modelConfigFor("plan")).isEqualTo(AgentModelConfig.empty());
    }

    /**
     * Q10's family, measured and accepted: dev <em>is</em> the base slot, so the loader gives its record
     * no model, url or key — the file keeps them, the config does not apply them (UC-WP-44 checks the
     * file). Think is the one field the dev slot really carries.
     */
    @Test
    void aDevOverrideReachesOnlyTheFieldsTheCoreReads() {
        put("dev", "{\"overridden\":true,\"model\":\"gpt-5\",\"url\":\"https://dev.example\","
                + "\"apiKey\":\"%s\",\"think\":\"high\"}".formatted(KEY));

        AgentModelConfig dev = configService.load().modelConfigFor("dev");

        assertThat(text("dev")).contains("model: gpt-5");
        assertThat(dev.model()).isNull();
        assertThat(dev.url()).isNull();
        assertThat(dev.apiKey()).isNull();
        assertThat(dev.think()).isEqualTo("high");
    }

    /**
     * UC-WP-67: the <em>shape</em> of the field comes from the provider, the <em>value</em> never does. A
     * level that no provider list offers is stored and read back exactly as typed — ADR-0064 forbids the
     * silent substitute a fixed list would otherwise invite.
     */
    @Test
    // UC-WP-67
    void thinkValueIsStoredVerbatim() {
        put("search", "{\"overridden\":true,\"think\":\"ultra-max\"}");

        String body = bodyOfAgents();

        assertThat(text("search")).contains("think: ultra-max");
        assertThat(slot(body, "search")).containsEntry("think", "ultra-max");
        assertThat(configService.load().modelConfigFor("search").think()).isEqualTo("ultra-max");
    }


    /**
     * R-WP-52: the two new slot fields travel like the others — {@code extra_body} under the core's
     * canonical key, and {@code tools} held by the file exactly as typed (D9: the factory stores the
     * wording, the core alone interprets it). The prompt body stays as it is.
     */
    @Test
    // R-WP-52
    void extraBodyAndToolsAreWrittenToTheSlotBlock() {
        ResponseEntity<String> saved = put("po",
                "{\"overridden\":true,\"extraBody\":\"{\\\"thinking\\\":{\\\"type\\\":\\\"enabled\\\"}}\","
                        + "\"tools\":\"!\"}");

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(slot(saved.getBody(), "po"))
                .containsEntry("extraBody", "{\"thinking\":{\"type\":\"enabled\"}}")
                .containsEntry("tools", "!");
        String stored = text("po");
        assertThat(stored).contains("extra_body: {\"thinking\":{\"type\":\"enabled\"}}").contains("tools: !");
    }

    /**
     * R-WP-16 for the new fields: an emptied field deletes its key and nothing else — an emptied
     * {@code tools} field means "all tools" again, not an empty filter.
     */
    @Test
    // R-WP-52
    void blankValuesClearBothKeys() {
        write("dev", "---\nmodel: gpt-5\nextra_body: {\"a\":1}\ntools: read\n---\n" + BODY);

        ResponseEntity<String> saved = put("dev", "{\"overridden\":true,\"extraBody\":\"\",\"tools\":\"\"}");

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(slot(saved.getBody(), "dev")).containsEntry("extraBody", null).containsEntry("tools", null);
        String stored = text("dev");
        assertThat(stored).contains("model: gpt-5").doesNotContain("extra_body", "tools:");
    }

    /**
     * A value the core could only ignore silently (D9/A1) is refused here, where the answer can still
     * name the field — and it names the field alone, never the value.
     */
    @Test
    // R-WP-52
    void anExtraBodyThatIsNotAJsonObjectIsRefused() {
        ResponseEntity<String> number = put("review", "{\"overridden\":true,\"extraBody\":\"42\"}");
        ResponseEntity<String> broken = put("review", "{\"overridden\":true,\"extraBody\":\"{\\\"a\\\"\"}");

        assertThat(number.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(broken.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(number.getBody()).contains("extraBody").doesNotContain("42");
        assertThat(broken.getBody()).contains("extraBody");
        assertThat(HOME.resolve("agents")).doesNotExist();
    }

    /**
     * R-WP-52: the preview reads the <b>typed</b> value and computes only — the intersection of the
     * slot's own class filter with the named list, sorted. It writes no file.
     */
    @Test
    // R-WP-52
    void previewOfATypedAllowlistNamesTheActiveTools() {
        ResponseEntity<String> preview = get("/api/settings/agents/po/tools?allowlist=diskReadFile,shellRunCommand");

        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(values(preview.getBody(), "$")).containsExactly("diskReadFile", "shellRunCommand");
        assertThat(HOME.resolve("agents")).doesNotExist();
    }

    /** No allowlist is every tool the agent could send — the set a project's turn would be built with. */
    @Test
    // R-WP-52
    void previewWithoutAllowlistNamesEveryToolOfTheAgent() {
        ResponseEntity<String> preview = get("/api/settings/agents/po/tools");

        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(values(preview.getBody(), "$")).isSorted()
                .contains("diskReadFile", "diskWriteFile", "shellRunCommand", "searchAgent", "compactSession");
    }

    /**
     * The search slot is the core's sub-loop, not an agent, and brings its own restriction: no edit
     * tools, no shell, neither itself nor the compact — the preview reads exactly that (one source).
     */
    @Test
    // R-WP-52
    void previewOfASearchSlotUsesTheSearchAgentTool() {
        ResponseEntity<String> preview = get("/api/settings/agents/search/tools");

        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(values(preview.getBody(), "$")).contains("diskReadFile")
                .doesNotContain("diskWriteFile", "shellRunCommand", "searchAgent", "compactSession");
    }

    /**
     * Compact has neither a field nor a preview (R-WP-52): the core runs no agent for it, so it gets
     * the same slot answer as an unknown id.
     */
    @Test
    // R-WP-52
    void compactHasNoToolPreview() {
        ResponseEntity<String> rejected = get("/api/settings/agents/compact/tools");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody()).contains("agent must name one of the slots");
    }

    // ------------------------------------------------------------------ wire

    private ResponseEntity<String> get(String path) {
        return http.getForEntity(url(path), String.class);
    }

    private ResponseEntity<String> put(String id, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url("/api/settings/agents/" + id), HttpMethod.PUT,
                new HttpEntity<>(json, headers), String.class);
    }

    private String bodyOfAgents() {
        ResponseEntity<String> response = http.getForEntity(url("/api/settings/agents"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static Object field(String body, String path) {
        return JsonPath.read(body, path);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> values(String body, String path) {
        return (List<Object>) field(body, path);
    }

    /** One slot of the list, by its id — the order is asserted once, not relied on everywhere. */
    private static Map<String, Object> slot(String body, String id) {
        List<Map<String, Object>> matches = JsonPath.read(body, "$[?(@.id == '" + id + "')]");
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    // ----------------------------------------------------------------- files

    private static Path agentFile(String slot) {
        return AgentConfigFile.forSlot(FactoryHome.of(HOME), slot).file();
    }

    private static void write(String slot, String content) {
        try {
            Path file = agentFile(slot);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static String text(String slot) {
        try {
            return Files.readString(agentFile(slot));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}

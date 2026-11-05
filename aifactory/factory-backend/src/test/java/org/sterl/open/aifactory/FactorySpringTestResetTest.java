package org.sterl.open.aifactory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.open.aifactory.chat.ChatLine;
import org.sterl.open.aifactory.chat.ProjectRuntimeRegistry;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The isolation proof of one shared context: one test method may leave nothing behind that the next one
 * could mistake for its own state ({@code FactorySpringTest#resetSharedState()}).
 *
 * <p>The two methods are one story, so they run in order. What makes this more than "the folder is
 * empty" is the runtime: the project id is a hash of the git url, so the same url always yields the same
 * id — and a runtime the reset did not drop is still registered under it and answers with the
 * <em>previous</em> conversation. Reading the history after re-adding the same url is therefore the
 * witness that the removal ran, and skipping it is red here.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(60)
class FactorySpringTestResetTest extends FactorySpringTest {

    private static final String GIT_URL = "https://git.example.com/reset/left-behind.git";
    private static final String QUESTION = "reset-frage";
    private static final String ANSWER = "RESET-ANTWORT: gearbeitet";
    private static final String LEFTOVER_FILE = "left-behind.txt";

    /** Id of the project of the first method — the second method must get exactly this id again. */
    private static String previousProjectId;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    @Test
    @Order(1)
    void aTurnLeavesAProjectARuntimeAndAFileBehind() throws Exception {
        MockLlmServer llm = new MockLlmServer(0);
        llm.start();
        try {
            writeFactoryConfig(llm.getUrl());
            previousProjectId = projects.add(GIT_URL).project().id();

            llm.queueResponse(ANSWER);
            runtimes.submit(previousProjectId, QUESTION, "Jon");
            awaitUntil(() -> historyTexts(previousProjectId).contains(ANSWER), "die Antwort im Verlauf");
            Files.writeString(HOME.resolve(LEFTOVER_FILE), "ein Rest des vorherigen Tests");

            assertThat(projects.projects()).as("Grundlage: ein Projekt ist eingetragen").hasSize(1);
            assertThat(historyTexts(previousProjectId))
                    .as("Grundlage: sein Verlauf ist gefüllt")
                    .contains(QUESTION, ANSWER);
        } finally {
            llm.stop();
        }
    }

    @Test
    @Order(2)
    void aPreviousTestLeavesNothingBehind() throws IOException {
        assertThat(projects.projects()).as("no project of the previous test").isEmpty();
        assertThat(childrenOf(HOME)).as("the shared home is empty").isEmpty();
        assertThat(fakeGit.cloneTargets).as("the git fake knows no clone anymore").isEmpty();
        assertThat(fakeGit.failWith).as("and no longer fails on purpose").isNull();

        // same url, same id, so the runtime table is addressed by exactly the same key again
        var reAdded = projects.add(GIT_URL);
        assertThat(reAdded.created()).as("the registration of the previous test is gone").isTrue();
        assertThat(reAdded.project().id()).isEqualTo(previousProjectId);
        assertThat(historyTexts(reAdded.project().id()))
                .as("the same code gets a fresh history — the runtime of the previous test was dropped")
                .isEmpty();
    }

    private List<Path> childrenOf(Path directory) throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children.toList();
        }
    }

    private List<String> historyTexts(String projectId) {
        return runtimes.history(projectId).stream().map(ChatLine::text).toList();
    }

    /** The factory's own file, never the plugin store ({@code ~/.peon}) — see {@code ChatConfigTurnTest}. */
    private static void writeFactoryConfig(String url) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, "reset-test");
        properties.setProperty(LlmConfigKeys.URL, url);
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(HOME.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "FactorySpringTestResetTest");
        }
    }

    private static void awaitUntil(BooleanSupplier condition, String what) {
        long until = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis())
                    .as("%s innerhalb der Frist", what)
                    .isLessThan(until);
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("unterbrochen während: " + what, interrupted);
            }
        }
    }
}

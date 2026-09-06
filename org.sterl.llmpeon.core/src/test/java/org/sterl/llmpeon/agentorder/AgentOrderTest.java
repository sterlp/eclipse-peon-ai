package org.sterl.llmpeon.agentorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.poagent.AiPoAgent;

class AgentOrderTest extends AbstractMemoryFileTest {

    private static AiAgent agent(String name) {
        AiAgent a = mock(AiAgent.class);
        when(a.getName()).thenReturn(name);
        return a;
    }

    private static Path newAgentsDir() throws Exception {
        return Files.createDirectory(fs.getPath("/agents-" + UUID.randomUUID()));
    }

    private static AgentOrder loadedWith(String orderFileContent) throws Exception {
        Path dir = newAgentsDir();
        Files.writeString(dir.resolve(AgentOrder.AGENT_ORDER_FILE), orderFileContent);
        AgentOrder subject = new AgentOrder();
        subject.load(dir);
        return subject;
    }

    @Test
    void parseSkipsBlankLinesAndComments() {
        // GIVEN
        List<String> lines = List.of("", "  ", "# a comment", "^Foo$");

        // WHEN
        List<Pattern> patterns = AgentOrder.parse(lines);

        // THEN
        assertThat(patterns).extracting(Pattern::pattern).containsExactly("^Foo$");
    }

    @Test
    void parseSkipsInvalidRegexButKeepsValidOnes() {
        // GIVEN
        List<String> lines = List.of("[invalid(regex", "^Good-Agent$");

        // WHEN
        List<Pattern> patterns = AgentOrder.parse(lines);

        // THEN
        assertThat(patterns).extracting(Pattern::pattern).containsExactly("^Good-Agent$");
    }

    @Test
    void loadCreatesDefaultFileWhenAbsent() throws Exception {
        // GIVEN
        Path dir = newAgentsDir();

        // WHEN
        new AgentOrder().load(dir);

        // THEN
        Path orderFile = dir.resolve(AgentOrder.AGENT_ORDER_FILE);
        assertThat(orderFile).exists();
        assertThat(Files.readString(orderFile)).contains("^Peon-PO$");
    }

    @Test
    void loadDoesNotOverwriteAnExistingFile() throws Exception {
        // GIVEN
        Path dir = newAgentsDir();
        Files.writeString(dir.resolve(AgentOrder.AGENT_ORDER_FILE), "^Peon-Dev$\n");

        // WHEN
        new AgentOrder().load(dir);

        // THEN
        assertThat(Files.readString(dir.resolve(AgentOrder.AGENT_ORDER_FILE))).isEqualTo("^Peon-Dev$\n");
    }

    @Test
    void sortFallsBackToDefaultPatternsWhenNoneLoaded() {
        // GIVEN - a fresh AgentOrder, load() never called
        List<AiAgent> agents = new AgentOrder().sort(List.of(agent("Zeta-Agent"), agent("Peon-PO"), agent("Alpha-Agent")));

        // THEN - without DEFAULT_PATTERNS this would fall through to plain alphabetical
        assertThat(agents).extracting(AiAgent::getName).containsExactly("Peon-PO", "Alpha-Agent", "Zeta-Agent");
    }

    @Test
    void sortFallsBackToDefaultPatternsWhenOrderFileHasOnlyComments() throws Exception {
        // GIVEN
        AgentOrder subject = loadedWith("""
                # Only comments
                # Nothing here
                """);
        List<AiAgent> agents = List.of(agent("Zeta-Agent"), agent("Peon-PO"), agent("Alpha-Agent"));

        // WHEN
        List<AiAgent> sorted = subject.sort(agents);

        // THEN
        assertThat(sorted).extracting(AiAgent::getName).containsExactly("Peon-PO", "Alpha-Agent", "Zeta-Agent");
    }

    @Test
    void sortGroupsByPatternThenAlphabeticalWithinGroup() throws Exception {
        // GIVEN
        AgentOrder subject = loadedWith(".*Manager.*\n");
        List<AiAgent> agents = List.of(agent("Other-Agent"), agent("Dev-Manager"), agent("Admin-Manager"));

        // WHEN
        List<AiAgent> sorted = subject.sort(agents);

        // THEN
        assertThat(sorted).extracting(AiAgent::getName).containsExactly("Admin-Manager", "Dev-Manager", "Other-Agent");
    }

    @Test
    void sortAppendsUnmatchedAgentsAlphabeticallyAtTheEnd() throws Exception {
        // GIVEN
        AgentOrder subject = loadedWith(".*Manager.*\n.*Worker.*\n");
        List<AiAgent> agents = List.of(agent("Other-Z"), agent("Worker-Y"), agent("Manager-B"), agent("Worker-X"), agent("Manager-A"));

        // WHEN
        List<AiAgent> sorted = subject.sort(agents);

        // THEN
        assertThat(sorted).extracting(AiAgent::getName).containsExactly("Manager-A", "Manager-B", "Worker-X", "Worker-Y", "Other-Z");
    }

    @Test
    void sortDoesNotDuplicateAnAgentMatchedByMultiplePatterns() throws Exception {
        // GIVEN
        AgentOrder subject = loadedWith("^Peon-.*$\n^Peon-PO$\n");
        List<AiAgent> agents = List.of(agent("Peon-PO"), agent("Peon-Dev"));

        // WHEN
        List<AiAgent> sorted = subject.sort(agents);

        // THEN
        assertThat(sorted).extracting(AiAgent::getName).containsExactly("Peon-Dev", "Peon-PO");
    }

    @Test
    void sortIsAlphabeticalWhenPatternsMatchNothing() throws Exception {
        // GIVEN
        AgentOrder subject = loadedWith("^Does-Not-Match$\n");
        List<AiAgent> agents = List.of(agent("Charlie"), agent("Alpha"), agent("Bravo"));

        // WHEN
        List<AiAgent> sorted = subject.sort(agents);

        // THEN
        assertThat(sorted).extracting(AiAgent::getName).containsExactly("Alpha", "Bravo", "Charlie");
    }

    @Test
    void sortSkipsInvalidRegexInTheOrderFile() throws Exception {
        // GIVEN
        AgentOrder subject = loadedWith("[invalid(regex\n^Good-Agent$\n");
        List<AiAgent> agents = List.of(agent("Good-Agent"));

        // WHEN
        List<AiAgent> sorted = subject.sort(agents);

        // THEN - falsifiable: if the invalid line were not skipped, load() would throw instead of returning
        assertThat(sorted).extracting(AiAgent::getName).containsExactly("Good-Agent");
    }

    @Test
    void defaultOrderContentReferencesThePoAgentByName() {
        // GIVEN - AgentOrder.DEFAULT_ORDER_CONTENT exists
        // WHEN - (none, static content)
        // THEN
        assertThat(AgentOrder.DEFAULT_ORDER_CONTENT).contains(AiPoAgent.NAME);
    }
}

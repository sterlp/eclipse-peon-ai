package org.sterl.llmpeon.agentorder;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import org.sterl.llmpeon.agent.AiAgent;

/**
 * Orders agents for the UI dropdown using regex patterns read from {@code agent-order.txt}.
 * <ul>
 * <li>Patterns are applied top-to-bottom: agents matching an earlier line appear first.
 * <li>Within a pattern group agents are sorted alphabetically; unmatched agents are appended alphabetically at the end.
 * <li>When the file yields no patterns, {@link #DEFAULT_PATTERNS} - parsed from {@link #DEFAULT_ORDER_CONTENT} - is used instead, so
 * "Peon-PO first" has a single source of truth: the default file content, not a type check against a concrete agent class.
 * </ul>
 */
@Slf4j
public class AgentOrder {

    public static final String AGENT_ORDER_FILE = "agent-order.txt";
    public static final String DEFAULT_ORDER_CONTENT = """
            # Ordering of agents in the UI dropdown. Each line is a regex that matches agent names.
            # Patterns are applied top-to-bottom: agents matching an earlier line appear first.
            # Within each pattern group, agents are sorted alphabetically.
            # Unmatched agents are appended alphabetically at the end.
            # Falls back to Peon-PO first, then alphabetical, when no valid patterns exist.
            ^Peon-PO$
            """;

    static final List<Pattern> DEFAULT_PATTERNS = parse(DEFAULT_ORDER_CONTENT.lines().toList());

    private volatile List<Pattern> patterns = List.of();

    /**
     * Parses agent-order.txt lines into compiled regex patterns.
     * <p>
     * Lines starting with '#' are comments and ignored. Empty lines are ignored. Invalid regex patterns are logged as warnings and skipped.
     */
    static List<Pattern> parse(List<String> lines) {
        List<Pattern> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            try {
                result.add(Pattern.compile(trimmed));
            } catch (PatternSyntaxException e) {
                log.warn("Invalid regex '{}' in {}, skipping: {}", trimmed, AGENT_ORDER_FILE, trimmed, e);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Creates the default agent-order.txt file if it does not exist, then (re-)loads the patterns from it.
     */
    public void load(Path agentsDir) throws IOException {
        Path orderFile = agentsDir.resolve(AGENT_ORDER_FILE);
        if (!Files.exists(orderFile)) {
            Files.writeString(orderFile, DEFAULT_ORDER_CONTENT);
        }
        this.patterns = parse(Files.readAllLines(orderFile));
    }

    /**
     * Sorts the given agents by the loaded patterns (or {@link #DEFAULT_PATTERNS} when none were loaded), grouped by pattern in file order,
     * alphabetical within each group, unmatched agents appended alphabetically.
     * <p>
     * Each agent name appears at most once (R4): two distinct instances sharing a name collapse into one dropdown entry — the first wins,
     * the collision is surfaced via a warning, never dropped silently.
     */
    public List<AiAgent> sort(Collection<AiAgent> agents) {
        List<Pattern> effectivePatterns = patterns.isEmpty() ? DEFAULT_PATTERNS : patterns;

        // Name-keyed dedup (R4): the dropdown is name-based, so two distinct instances sharing a name
        // collapse into one entry — the first wins, the collision is surfaced, never silent.
        agents.stream().collect(groupingBy(AiAgent::getName, counting()))
                .forEach((name, count) -> {
                    if (count > 1) {
                        log.warn("Agent name collision: {} agents share name '{}' — first one kept, others dropped", count, name);
                    }
                });

        Set<String> seenNames = new HashSet<>();
        List<AiAgent> uniqueAgents = agents.stream().filter(agent -> seenNames.add(agent.getName())).toList();

        List<AiAgent> sortedAgents = uniqueAgents.stream().sorted(Comparator.comparing(AiAgent::getName)).toList();

        // Name-keyed first match: an agent matched by a later pattern line keeps the group
        // of the first line (R2) and the later match is warned, never silently dropped.
        Map<String, Integer> firstMatchLine = new HashMap<>();
        Stream<AiAgent> matchingAgents = IntStream.range(0, effectivePatterns.size())
                .mapToObj(Integer::valueOf)
                .flatMap(lineIdx -> sortedAgents.stream()
                        .filter(agent -> effectivePatterns.get(lineIdx).matcher(agent.getName()).matches())
                        .filter(agent -> {
                            Integer claimedBy = firstMatchLine.putIfAbsent(agent.getName(), lineIdx);
                            if (claimedBy != null) {
                                log.warn("Agent '{}' matched by line {} (already claimed by line {}) — keeping first",
                                        agent.getName(), lineIdx + 1, claimedBy + 1);
                                return false;
                            }
                            return true;
                        }));

        Set<String> matchedNames = firstMatchLine.keySet();
        Stream<AiAgent> remainingAgents = sortedAgents.stream().filter(agent -> !matchedNames.contains(agent.getName()));

        return Stream.concat(matchingAgents, remainingAgents).toList();
    }
}

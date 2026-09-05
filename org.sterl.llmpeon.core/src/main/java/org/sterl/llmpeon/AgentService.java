package org.sterl.llmpeon;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.CustomAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.prompt.PromptYmlParser;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.llmpeon.tool.ToolService;

import lombok.Getter;
import lombok.Setter;

/**
 * Loads user-defined custom agents from a configured directory.
 *
 * <p>Each immediate subdirectory that contains an {@code AGENT.md} (case-insensitive) becomes one
 * {@link AgentPromptFile}; the directory name is the default agent name. Mirrors
 * {@link org.sterl.llmpeon.skill.SkillService}. Files are read on demand, so edits to an
 * {@code AGENT.md} are picked up on the next {@link #refresh(Path)}.</p>
 */
public class AgentService {

    private static final Logger LOG = Logger.getLogger(AgentService.class.getName());

    private static final String AGENT_ORDER_FILE = "agent-order.txt";
    private static final String DEFAULT_ORDER_CONTENT = """
            # Ordering of agents in the UI dropdown. Each line is a regex that matches agent names.
            # Patterns are applied top-to-bottom: agents matching an earlier line appear first.
            # Within each pattern group, agents are sorted alphabetically.
            # Unmatched agents are appended alphabetically at the end.
            # Falls back to Peon-PO first, then alphabetical, when no valid patterns exist.
            ^Peon-PO$
            """;

    private final ConfiguredChatModel chatModel;
    private final ToolService toolService;
    private final Path historyConfigDir;

    private final Map<String, AiAgent> agents = new ConcurrentHashMap<>();

    /** Agents added via {@link #addPersistentAgent(AiAgent)} — survive {@link #clearAgents()}. */
    private final Map<String, AiAgent> persistentAgents = new ConcurrentHashMap<>();

    private volatile Path agentsDirectory;

    /** Parsed regex patterns from agent-order.txt, applied in file order. */
    private volatile List<Pattern> orderPatterns = List.of();

    /** Non-null when a custom agent is selected; takes precedence over {@link #mode}. */
    @Getter @Setter
    private volatile AiAgent activeAgent;
    private final AiDevAgent devAgent;
    private final AiPlanAgent planAgent;

    public AgentService(
            Path agentsDirectory, 
            ToolService toolService,
            ConfiguredChatModel configuredChatModel) {
        this(false, agentsDirectory, toolService, configuredChatModel, null);
    }

    public AgentService(
            Path agentsDirectory,
            ToolService toolService,
            ConfiguredChatModel configuredChatModel,
            Path historyConfigDir) {
        this(false, agentsDirectory, toolService, configuredChatModel, historyConfigDir);
    }

    public AgentService(
            boolean withDefaultAgent,
            Path agentsDirectory, 
            ToolService toolService,
            ConfiguredChatModel configuredChatModel) {
        this(withDefaultAgent, agentsDirectory, toolService, configuredChatModel, null);
    }

    public AgentService(
            boolean withDefaultAgent,
            Path agentsDirectory, 
            ToolService toolService,
            ConfiguredChatModel configuredChatModel,
            Path historyConfigDir) {
        this.chatModel = configuredChatModel;
        this.toolService = toolService;
        this.historyConfigDir = historyConfigDir;
        
        Objects.requireNonNull(this.chatModel, "ConfiguredChatModel cannot be null");
        Objects.requireNonNull(this.toolService, "ToolService cannot be null");

        if (withDefaultAgent) {
            devAgent = new AiDevAgent(chatModel, toolService, historyConfigDir);
            this.persistentAgents.put(devAgent.getName(), devAgent);
            planAgent = new AiPlanAgent(chatModel, toolService, historyConfigDir);
            this.persistentAgents.put(planAgent.getName(), planAgent);
            this.activeAgent = devAgent;
        } else {
            devAgent = null;
            planAgent = null;
        }

        refresh(agentsDirectory);
        reloadAgents();
    }

    public void clear() {
        agents.clear();
    }

    /**
     * Returns loaded and persistent agents ordered by {@code agent-order.txt} regex patterns.
     * Matches are grouped by pattern (alphabetically within each group), followed by unmatched agents alphabetically.
     * Falls back to {@link AiPoAgent} first, then alphabetical, when no valid patterns exist.
     */
    public List<AiAgent> getAgents() {
        var all = new java.util.LinkedHashSet<AiAgent>(agents.values());
        all.addAll(persistentAgents.values());

        if (orderPatterns.isEmpty()) {
            return all.stream()
                    .sorted(Comparator.<AiAgent>comparingInt(a -> a instanceof AiPoAgent ? 0 : 1)
                            .thenComparing(AiAgent::getName))
                    .toList();
        }

        var result = new java.util.ArrayList<AiAgent>();
        var seen = new java.util.HashSet<String>();

        for (var pattern : orderPatterns) {
            var matches = all.stream()
                    .filter(a -> !seen.contains(a.getName()))
                    .filter(a -> pattern.matcher(a.getName()).matches())
                    .sorted(Comparator.comparing(AiAgent::getName))
                    .toList();
            result.addAll(matches);
            matches.forEach(a -> seen.add(a.getName()));
        }

        var remaining = all.stream()
                .filter(a -> !seen.contains(a.getName()))
                .sorted(Comparator.comparing(AiAgent::getName))
                .toList();
        result.addAll(remaining);
        return result;
    }

    public int loadedAgentCount() {
        return agents.size();
    }

    /**
     * Adds an agent that survives {@link #clearAgents()} on reload.
     * Used for built-in agents like the scaffold agent that must persist across reloads.
     */
    public void addPersistentAgent(AiAgent agent) {
        if (agent == null) return;
        this.persistentAgents.put(agent.getName(), agent);
    }

    /** Returns true if the agent is a persistent agent (survives clearAgents). */
    public boolean isPersistentAgent(String name) {
        return persistentAgents.containsKey(name);
    }

    public void addAgent(AiAgent agent) {
        if (agent == null) return;
        this.agents.put(agent.getName(), agent);
    }

    public boolean refresh(String newPath) throws IOException {
        return refresh(newPath == null || newPath.isBlank() ? null : Path.of(newPath));
    }

    /**
     * Reloads using the new path
     */
    public boolean refresh(Path newPath) {
        if (newPath == null && agentsDirectory == null) return false;

        if (newPath == null) {
            this.agentsDirectory = null;
        } else {
            this.agentsDirectory = newPath.toAbsolutePath().normalize();
        }
        return reloadAgents();
    }

    public boolean reloadAgents() {
        if (Files.isDirectory(agentsDirectory)) {
            try {
                ensureOrderFileExists();
                parseOrderFile();
                reloadAgentConfig();
            } catch (IOException e) {
                throw new RuntimeException("Failed to reload agents from: " + agentsDirectory, e);
            }
        } else {
            clearAgents();
        }
        return true;
    }


    /**
     * Creates the default agent-order.txt file if it does not exist.
     * The default preserves current UI behavior: Peon-PO first, others alphabetical.
     */
    private void ensureOrderFileExists() throws IOException {
        var orderFile = agentsDirectory.resolve(AGENT_ORDER_FILE);
        if (!Files.exists(orderFile)) {
            Files.writeString(orderFile, DEFAULT_ORDER_CONTENT);
        }
    }

    /**
     * Parses agent-order.txt into a list of compiled regex patterns.
     * Lines starting with '#' are comments and ignored. Empty lines are ignored.
     * Invalid regex patterns are logged as warnings and skipped.
     */
    private void parseOrderFile() throws IOException {
        var orderFile = agentsDirectory.resolve(AGENT_ORDER_FILE);
        if (!Files.exists(orderFile)) {
            orderPatterns = List.of();
            return;
        }

        var patterns = new java.util.ArrayList<Pattern>();
        var lines = Files.readAllLines(orderFile);
        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(trimmed));
            } catch (PatternSyntaxException e) {
                LOG.log(Level.WARNING, "Invalid regex in agent-order.txt, skipping: " + trimmed, e);
            }
        }
        orderPatterns = patterns;
    }

    private void reloadAgentConfig() throws IOException {
        var newAgents = new ConcurrentHashMap<String, AiAgent>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(agentsDirectory)) {
            for (Path entry : entries) {
                var agentCfg = readAgentPrompt(entry);
                if (agentCfg != null) {
                    var agent = this.agents.get(agentCfg.getName());
                    if (agent == null) {
                        agent = new CustomAgent(agentCfg, chatModel, toolService, historyConfigDir);
                    } else if (agent instanceof CustomAgent ca) ca.setPromptFile(agentCfg);
                    newAgents.put(agent.getName(), agent);
                }
            }
        }
        clearAgents();
        this.agents.putAll(newAgents);
        // clear active if gone
        if (activeAgent != null && !agents.containsKey(activeAgent.getName())) {
            this.activeAgent = agents.isEmpty() ? null : agents.values().iterator().next();
        } else if (activeAgent == null && !agents.isEmpty()) {
            this.activeAgent = agents.values().iterator().next();
        }
    }

    private void clearAgents() {
        this.agents.clear();
        // Re-add persistent agents (survive reloads)
        for (var agent : persistentAgents.values()) {
            this.agents.put(agent.getName(), agent);
            if (activeAgent == null) activeAgent = agent;
        }
    }

    private SimplePromptFile readAgentPrompt(Path dir) throws IOException {
        var agentFile = detectAgentFile(dir);
        if (agentFile == null) return null;
        return PromptYmlParser.parseYml(agentFile);
    }

    private Path detectAgentFile(Path dir) {
        var file = dir.resolve("AGENT.md");
        if (Files.isRegularFile(file)) return file;
        file = dir.resolve("agent.md");
        return Files.isRegularFile(file) ? file : null;
    }

    /** Returns the agent by name, including disabled ones. */
    public Optional<AiAgent> get(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(agents.get(name));
    }

    public boolean hasAgents() {
        return !agents.isEmpty();
    }

    public Path getAgentsDirectory() {
        return agentsDirectory;
    }
}

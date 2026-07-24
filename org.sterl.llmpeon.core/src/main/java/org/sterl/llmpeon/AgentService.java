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

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.CustomAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
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

    private final ConfiguredChatModel chatModel;
    private final ToolService toolService;

    private final Map<String, AiAgent> agents = new ConcurrentHashMap<>();

    /** Agents added via {@link #addPersistentAgent(AiAgent)} — survive {@link #clearAgents()}. */
    private final Map<String, AiAgent> persistentAgents = new ConcurrentHashMap<>();

    private volatile Path agentsDirectory;

    /** Non-null when a custom agent is selected; takes precedence over {@link #mode}. */
    @Getter @Setter
    private volatile AiAgent activeAgent;
    private final AiDevAgent devAgent;
    private final AiPlanAgent planAgent;

    public AgentService(
            Path agentsDirectory, 
            ToolService toolService,
            ConfiguredChatModel configuredChatModel) {
        this(false, agentsDirectory, toolService, configuredChatModel);
    }

    public AgentService(
            boolean withDefaultAgent,
            Path agentsDirectory, 
            ToolService toolService,
            ConfiguredChatModel configuredChatModel) {
        this.chatModel = configuredChatModel;
        this.toolService = toolService;
        
        Objects.requireNonNull(this.chatModel, "ConfiguredChatModel cannot be null");
        Objects.requireNonNull(this.toolService, "ToolService cannot be null");

        if (withDefaultAgent) {
            devAgent = new AiDevAgent(chatModel, toolService);
            this.persistentAgents.put(devAgent.getName(), devAgent);
            planAgent = new AiPlanAgent(chatModel, toolService);
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

    /** Returns loaded agents when enabled, empty list when disabled, sorted by name. */
    public List<AiAgent> getAgents() {
        var all = new java.util.LinkedHashSet<AiAgent>(agents.values());
        all.addAll(persistentAgents.values());
        return all.stream()
                    .sorted(Comparator.comparing(a -> a.getName()))
                    .toList();
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
                reloadAgentConfig();
            } catch (IOException e) {
                throw new RuntimeException("Failed to reload agents from: " + agentsDirectory, e);
            }
        } else {
            clearAgents();
        }
        return true;
    }

    private void reloadAgentConfig() throws IOException {
        var newAgents = new ConcurrentHashMap<String, AiAgent>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(agentsDirectory)) {
            for (Path entry : entries) {
                var agentCfg = readAgentPrompt(entry);
                if (agentCfg != null) {
                    var agent = this.agents.get(agentCfg.getName());
                    if (agent == null) agent = new CustomAgent(agentCfg, chatModel, toolService);
                    else if (agent instanceof CustomAgent ca) ca.setPromptFile(agentCfg);
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

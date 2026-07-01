package org.sterl.llmpeon.agent;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lombok.NoArgsConstructor;

/**
 * Loads user-defined custom agents from a configured directory.
 *
 * <p>Each immediate subdirectory that contains an {@code AGENT.md} (case-insensitive) becomes one
 * {@link AgentPromptFile}; the directory name is the default agent name. Mirrors
 * {@link org.sterl.llmpeon.skill.SkillService}. Files are read on demand, so edits to an
 * {@code AGENT.md} are picked up on the next {@link #refresh(Path)}.</p>
 */
@NoArgsConstructor
public class AgentService {

    private volatile Path agentsDirectory;
    private final Map<String, AgentPromptFile> agents = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;

    public AgentService(Path agentsDirectory) throws IOException {
        refresh(agentsDirectory);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Returns loaded agents when enabled, empty list when disabled, sorted by name. */
    public List<AgentPromptFile> getAgents() {
        return enabled
                ? agents.values().stream()
                    .filter(AgentPromptFile::isEnabled)
                    .sorted(Comparator.comparing(a -> a.name().toLowerCase(Locale.ROOT)))
                    .toList()
                : List.of();
    }

    public int loadedAgentCount() {
        return agents.size();
    }

    public boolean refresh(String newPath) throws IOException {
        return refresh(newPath == null || newPath.isBlank() ? null : Path.of(newPath));
    }

    public boolean refresh(Path newPath) throws IOException {
        if (newPath == null && agentsDirectory == null) return false;

        this.agents.clear();
        if (newPath == null) {
            this.agentsDirectory = null;
            return true;
        }

        this.agentsDirectory = newPath.toAbsolutePath().normalize();
        if (Files.isDirectory(agentsDirectory)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(agentsDirectory)) {
                for (Path entry : entries) {
                    if (Files.isDirectory(entry)) handleDirectoryAgent(entry);
                }
            }
        }
        return true;
    }

    private void handleDirectoryAgent(Path dir) throws IOException {
        var agentFile = detectAgentFile(dir);
        if (agentFile == null) return;
        var agent = AgentPromptFile.parse(agentFile);
        if (agent != null) agents.put(agent.getName().toLowerCase(Locale.ROOT), agent);
    }

    private Path detectAgentFile(Path dir) {
        var file = dir.resolve("AGENT.md");
        if (Files.isRegularFile(file)) return file;
        file = dir.resolve("agent.md");
        return Files.isRegularFile(file) ? file : null;
    }

    /** Returns the agent by name, including disabled ones. */
    public Optional<AgentPromptFile> get(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(agents.get(name.toLowerCase(Locale.ROOT)));
    }

    public boolean hasAgents() {
        return enabled && !agents.isEmpty();
    }

    public Path getAgentsDirectory() {
        return agentsDirectory;
    }
}

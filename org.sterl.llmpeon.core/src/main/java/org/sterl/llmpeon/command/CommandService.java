package org.sterl.llmpeon.command;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.sterl.llmpeon.shared.PromptYmlParser;
import org.sterl.llmpeon.shared.model.SimplePromptFile;

import lombok.NoArgsConstructor;

/**
 * Loads user-defined slash commands from a configured directory.
 *
 * <p>Each {@code <name>.md} file directly inside the directory becomes one
 * {@link SimplePromptFile}. Unlike {@link org.sterl.llmpeon.skill.SkillService}, the
 * frontmatter is optional and only the {@code description} field is parsed.
 * Hidden files and files in subdirectories are ignored.</p>
 */
@NoArgsConstructor
public class CommandService {

    private volatile Path commandsDirectory;
    private final Map<String, SimplePromptFile> commands = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;

    public CommandService(Path commandsDirectory) throws IOException {
        refresh(commandsDirectory);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Set enabled state for a specific command by name. */
    public void setCommandEnabled(String commandName, boolean enabled) {
        var cmd = commands.get(commandName.toLowerCase(Locale.ROOT));
        if (cmd != null) cmd.setEnabled(enabled);
    }

    /** Enable/disable all commands at once. */
    public void setAllCommandsEnabled(boolean enabled) {
        commands.values().forEach(cmd -> cmd.setEnabled(enabled));
    }

    public List<SimplePromptFile> getCommands() {
        return enabled
                ? commands.values().stream()
                    .filter(SimplePromptFile::isEnabled)
                    .sorted(Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT)))
                    .toList()
                : List.of();
    }

    /** Returns all loaded commands regardless of global enabled state. */
    public List<SimplePromptFile> getAllLoadedCommands() {
        return commands.values().stream()
                .sorted(Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public int loadedCommandCount() {
        return commands.size();
    }

    public boolean refresh(String newPath) throws IOException {
        return refresh(newPath == null || newPath.isBlank() ? null : Path.of(newPath));
    }

    public boolean refresh(Path newPath) throws IOException {
        if (newPath == null && commandsDirectory == null) return false;
        if (newPath != null && Objects.equals(newPath, commandsDirectory)) {
            return reload();
        }
        this.commands.clear();
        if (newPath == null) {
            this.commandsDirectory = null;
            return true;
        }
        this.commandsDirectory = newPath.toAbsolutePath().normalize();
        reload();
        return true;
    }

    private boolean reload() throws IOException {
        this.commands.clear();
        if (commandsDirectory == null || !Files.isDirectory(commandsDirectory)) return true;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(commandsDirectory, "*.md")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) continue;
                var fileName = file.getFileName().toString();
                if (fileName.startsWith(".")) continue;
                var cmd = PromptYmlParser.parseYml(file);
                if (cmd != null) commands.put(cmd.name().toLowerCase(Locale.ROOT), cmd);
            }
        }
        return true;
    }

    public Optional<SimplePromptFile> get(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(commands.get(name.toLowerCase(Locale.ROOT)));
    }

    public boolean hasCommands() {
        return enabled && !commands.isEmpty();
    }

    /**
     * Returns all active command names
     */
    public String commandNames() {
        return getCommands().stream().map(SimplePromptFile::name).collect(Collectors.joining(", "));
    }

    public Path getCommandsDirectory() {
        return commandsDirectory;
    }
}
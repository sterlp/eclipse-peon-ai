package org.sterl.llmpeon.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads user-defined slash commands from a configured directory.
 *
 * <p>Each {@code <name>.md} file directly inside the directory becomes one
 * {@link CommandRecord}. Unlike {@link org.sterl.llmpeon.skill.SkillService}, the
 * frontmatter is optional and only the {@code description} field is parsed.
 * Hidden files and files in subdirectories are ignored.</p>
 */
public class CommandService {

    private Path commandsDirectory;
    private final List<CommandRecord> commands = new ArrayList<>();

    public CommandService() {
    }

    public CommandService(Path commandsDirectory) throws IOException {
        refresh(commandsDirectory);
    }

    public List<CommandRecord> getCommands() {
        return List.copyOf(commands);
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
            // path unchanged — still re-scan in case files were added/removed externally
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
                if (fileName.startsWith(".")) continue; // skip hidden files
                CommandRecord cmd = parseCommandFile(file);
                if (cmd != null) commands.add(cmd);
            }
        }
        commands.sort(Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT)));
        return true;
    }

    static CommandRecord parseCommandFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        var fileName = file.getFileName().toString();
        if (!fileName.endsWith(".md")) return null;
        var name = fileName.substring(0, fileName.length() - 3);
        if (name.isBlank()) return null;

        String description = readOptionalDescription(file);
        return new CommandRecord(name, description, file);
    }

    private static String readOptionalDescription(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String first = reader.readLine();
            while (first != null && first.isBlank()) first = reader.readLine();
            if (first == null || !"---".equals(first.trim())) return null;

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if ("---".equals(trimmed)) return null;
                if (trimmed.startsWith("description:")) {
                    return stripYamlValue(trimmed.substring("description:".length()));
                }
            }
        }
        return null;
    }

    private static String stripYamlValue(String value) {
        if (value == null) return null;
        value = value.strip();
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                 || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    public Optional<CommandRecord> get(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return commands.stream()
                .filter(c -> c.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public boolean hasCommands() {
        return !commands.isEmpty();
    }

    public String commandNames() {
        return commands.stream().map(CommandRecord::name).collect(Collectors.joining(", "));
    }

    public Path getCommandsDirectory() {
        return commandsDirectory;
    }
}

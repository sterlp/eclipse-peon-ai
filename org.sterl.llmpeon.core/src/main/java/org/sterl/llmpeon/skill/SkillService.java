package org.sterl.llmpeon.skill;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.sterl.llmpeon.template.TemplateContext;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

public class SkillService {

    private Path skillsDirectory;
    private final List<Skill> skills = new LinkedList<>();
    private boolean enabled = true;

    public SkillService() {
    }

    public SkillService(Path skillsDirectory) throws IOException {
        refresh(skillsDirectory);
    }

    public ChatMessage skillMessage(TemplateContext context) {
        if (getSkills().isEmpty()) return null;
        var string = getSkills().stream()
                .map(s -> context.process(s.shortDescription()))
                .collect(Collectors.joining("\n"));
        return SystemMessage.from("""
                Following skills are availble load and read them them if a user task maches the name or description.
                """ + string);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Set enabled state for a specific skill by name. */
    public void setSkillEnabled(String skillName, boolean enabled) {
        skills.stream()
            .filter(s -> s.name().equalsIgnoreCase(skillName))
            .findFirst()
            .ifPresent(s -> s.setEnabled(enabled));
    }

    /** Enable/disable all skills at once. */
    public void setAllSkillsEnabled(boolean enabled) {
        skills.forEach(s -> s.setEnabled(enabled));
    }

    /** Total number of loaded skills regardless of enabled state. */
    public int loadedSkillCount() {
        return skills.size();
    }

    /** Returns loaded skills when enabled, empty list when disabled. */
    public List<Skill> getSkills() {
        return enabled ? skills.stream().filter(Skill::isEnabled).toList() : List.of();
    }

    /** Returns all loaded skills regardless of global enabled state. */
    public List<Skill> getAllLoadedSkills() {
        return new LinkedList<>(skills);
    }
    
    public boolean refresh(String newPath) throws IOException {
        return this.refresh(newPath == null ? null : Path.of(newPath));
    }

    public boolean refresh(Path newPath) throws IOException {
        if (newPath == null && skillsDirectory == null) return false;
        if (Objects.equals(newPath, skillsDirectory)) return false;

        this.skills.clear();
        this.skillsDirectory = newPath;
        // cleared
        if (this.skillsDirectory == null) return true;
        
        this.skillsDirectory = newPath.toAbsolutePath().normalize();
        if (Files.isDirectory(skillsDirectory)) {
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(skillsDirectory)) {
                for (Path dir : dirs) {
                    var skillFile = detectSkillFile(dir);
                    if (Files.isRegularFile(skillFile)) {
                        Skill skill = parseSkillFile(skillFile);
                        if (skill != null) {
                            skills.add(skill);
                        }
                    }
                }
            }
        }

        // skill count is visible in the status line
        return true;
    }

    private Path detectSkillFile(Path dir) {
        var skillFile = dir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) skillFile = dir.resolve("skill.md");
        return skillFile;
    }

    static Skill parseSkillFile(Path skillFile) throws IOException {
        String name = null;
        String description = null;
        boolean inFrontmatter = false;

        try (BufferedReader reader = Files.newBufferedReader(skillFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (!inFrontmatter) {
                    if ("---".equals(trimmed)) {
                        inFrontmatter = true;
                    }
                    continue;
                }

                // end of frontmatter
                if ("---".equals(trimmed)) {
                    break;
                }

                if (trimmed.startsWith("name:")) {
                    name = stripYamlValue(trimmed.substring(5));
                } else if (trimmed.startsWith("description:")) {
                    description = stripYamlValue(trimmed.substring(12));
                }
            }
        }

        if (name != null && description != null) {
            return new Skill(name, description, skillFile);
        }
        return null;
    }

    private static String stripYamlValue(String value) {
        if (value == null) return null;
        value = value.strip();
        // remove surrounding quotes
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                 || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    public Optional<Skill> get(String name) {
        return skills.stream().filter(s -> s.name().equalsIgnoreCase(name)).findFirst();
    }

    public boolean hasSkills() {
        return enabled && !skills.isEmpty();
    }

    public String skillNames() {
        return this.skills.stream().map(Skill::name).collect(Collectors.joining(", "));
    }
}

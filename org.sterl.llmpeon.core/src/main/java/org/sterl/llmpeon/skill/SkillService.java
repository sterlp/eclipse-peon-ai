package org.sterl.llmpeon.skill;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.sterl.llmpeon.shared.PromptYmlParser;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class SkillService {

    private volatile Path skillsDirectory;
    private final Map<String, SkillPromptFile> skills = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;

    public SkillService(Path skillsDirectory) throws IOException {
        refresh(skillsDirectory);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Set enabled state for a specific skill by name. */
    public void setSkillEnabled(String skillName, boolean enabled) {
        var s = skills.get(skillName.toLowerCase(Locale.ROOT));
        if (s != null) s.setEnabled(enabled);
    }

    /** Enable/disable all skills at once. */
    public void setAllSkillsEnabled(boolean enabled) {
        skills.values().forEach(s -> s.setEnabled(enabled));
    }

    /** Total number of loaded skills regardless of enabled state. */
    public int loadedSkillCount() {
        return skills.size();
    }

    /** Returns loaded skills when enabled, empty list when disabled. */
    public List<SkillPromptFile> getSkills() {
        return enabled
                ? skills.values().stream()
                    .filter(SkillPromptFile::isEnabled)
                    .toList()
                : List.of();
    }

    /** Returns all loaded skills regardless of global enabled state. */
    public List<SkillPromptFile> getAllLoadedSkills() {
        return new LinkedList<>(skills.values());
    }

    public boolean refresh(String newPath) throws IOException {
        return this.refresh(newPath == null ? null : Path.of(newPath));
    }

    public boolean refresh(Path newPath) throws IOException {
        if (newPath == null && skillsDirectory == null) return false;

        this.skills.clear();
        if (newPath == null) {
            this.skillsDirectory = null;
            return true;
        }

        this.skillsDirectory = newPath.toAbsolutePath().normalize();
        if (Files.isDirectory(skillsDirectory)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(skillsDirectory)) {
                for (Path entry : entries) {
                    if (Files.isDirectory(entry)) {
                        handleDirectorySkill(entry);
                    } else if (Files.isRegularFile(entry)) {
                        handleFileSkill(entry);
                    }
                }
            }
        }

        return true;
    }
    
    private void handleFileSkill(Path entry) throws IOException {
        var yml = PromptYmlParser.parseYml(entry);
        if (yml != null) {
            var skill = SkillPromptFile.from(yml).build();
            skills.put(skill.getName().toLowerCase(Locale.ROOT), skill);
        }
    }

    private void handleDirectorySkill(Path entry) throws IOException {
        var skillFile = detectSkillFile(entry);
        if (skillFile != null && Files.isRegularFile(skillFile)) {
            var yml = PromptYmlParser.parseYml(skillFile);
            if (yml != null) {
                var skill = SkillPromptFile.from(yml)
                        .skillDir(entry.getParent())
                        .build();
                skills.put(skill.getName().toLowerCase(Locale.ROOT), skill);
            }
        }
    }

    private Path detectSkillFile(Path dir) {
        var skillFile = dir.resolve("SKILL.md");
        if (Files.isRegularFile(skillFile)) return skillFile;
        skillFile = dir.resolve("skill.md");
        return Files.isRegularFile(skillFile) ? skillFile : null;
    }

    /**
     * Return the skill -- also the disabled ones
     */
    public Optional<SkillPromptFile> get(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(skills.get(name.toLowerCase(Locale.ROOT)));
    }

    public boolean hasSkills() {
        return enabled && !skills.isEmpty();
    }

    /**
     * Returns all active skill names
     */
    public String skillNames() {
        return getSkills().stream().map(SkillPromptFile::name).collect(Collectors.joining(", "));
    }
}
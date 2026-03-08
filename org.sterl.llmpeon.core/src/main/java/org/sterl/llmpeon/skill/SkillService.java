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
    private final List<SkillRecord> skills = new LinkedList<>();

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

    public List<SkillRecord> getSkills() {
        return skills;
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
                        SkillRecord skill = parseSkillFile(skillFile);
                        if (skill != null) {
                            skills.add(skill);
                        }
                    }
                }
            }
        }

        System.out.println("Loaded " + skills.size() + " skills in " + skillsDirectory);
        return true;
    }

    private Path detectSkillFile(Path dir) {
        var skillFile = dir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) skillFile = dir.resolve("skill.md");
        return skillFile;
    }

    static SkillRecord parseSkillFile(Path skillFile) throws IOException {
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
            return new SkillRecord(name, description, skillFile);
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

    public Optional<SkillRecord> get(String name) {
        return skills.stream().filter(s -> s.name().equalsIgnoreCase(name)).findFirst();
    }

    public boolean hasSkills() {
        return !skills.isEmpty();
    }

    public String skillNames() {
        return this.skills.stream().map(SkillRecord::name).collect(Collectors.joining(", "));
    }
}

package org.sterl.llmpeon.skill;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.shared.model.SimplePromptFile;

import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
public class SkillPromptFile extends SimplePromptFile {

    @Nullable
    private final Path skillDir;

    public static SkillPromptFileBuilder from(SimplePromptFile yml) {
        return SkillPromptFile.builder()
            .name(yml.name())
            .description(yml.description())
            .promptFile(yml.promptFile());
    }

    public SkillPromptFile(String name, String description, Path promptFile, Path skillDir) {
        super(name, description, promptFile);
        this.skillDir = skillDir;
    }

    @Override
    public String readBody() {
        var result = new StringBuilder();
        result.append("=== SKILL: ").append(name).append(" ===\n");
        if (skillDir == null) {
            result.append(promptFile).append("\n");
            result.append("only a SKILL file.\n");
        } else {
            result.append("Skill disk dir: ").append(skillDir).append("\n");
            try (var stream = Files.walk(skillDir)) {
                stream.filter(Files::isRegularFile)
                    .forEach(p -> result.append(skillDir.relativize(p)).append("\n"));
            } catch (java.io.IOException e) {
                result.append("[unable to scan directory] ").append(e.getMessage()).append("\n");
            }
        }
        result.append("Editing requires disk tools — ensure they are enabled or request access from the user, only if needed.\n");
        result.append("=== BODY ===\n");
        result.append(super.readBody());
        return result.toString();
    }

    public String readRelativeFile(String relativePath) {
        if (skillDir == null) {
            throw new IllegalStateException("Skill " + name + " has no files.");
        }
        // Strip leading slashes to avoid absolute path resolution
        String cleaned = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        Path target = skillDir.resolve(cleaned).normalize();
        if (!target.startsWith(skillDir)) {
            throw new IllegalArgumentException("Path traversal not allowed: " + relativePath);
        }
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("File not found in skill directory: " + relativePath);
        }
        try {
            return Files.readString(target);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read " + target, e);
        }
    }
}
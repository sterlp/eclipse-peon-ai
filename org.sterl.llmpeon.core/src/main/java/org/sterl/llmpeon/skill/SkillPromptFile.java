package org.sterl.llmpeon.skill;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;

import lombok.Getter;

public class SkillPromptFile extends SimplePromptFile {
    @Nullable
    @Getter
    private volatile Path skillDir;
    
    public static SkillPromptFile from(SimplePromptFile yml) {
        return new SkillPromptFile(yml.getFrontmatter(), yml.getBody(), yml.getPromptFile());
    }
    
    public static SkillPromptFile from(SimplePromptFile yml, Path dir) {
        return new SkillPromptFile(yml.getFrontmatter(), yml.getBody(), yml.getPromptFile(), dir);
    }

    public SkillPromptFile(Map<String, List<String>> frontmatter, String body,
            Path promptFile) {
        this(frontmatter, body, promptFile, null);
    }
    
    public SkillPromptFile(Map<String, List<String>> frontmatter, String body,
            Path promptFile, Path skillDir) {
        super(frontmatter, body, promptFile);
        this.skillDir = skillDir;
    }

    public String buildShortInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("---\nname: ").append(getName())
          .append("\ndescription: ").append(getDescription());
        return sb.toString();
    }

    public String renderBody() {
        var result = new StringBuilder();
        result.append("=== SKILL: ").append(getName())
                .append(" ===\n");
        if (skillDir == null) {
            result.append(getPromptFile()).append("\n");
            result.append("only a SKILL file.\n");
        } else {
            result.append("SKILL disk dir: ").append(skillDir).append("\n");
            try (var stream = Files.walk(skillDir)) {
                stream.filter(Files::isRegularFile).forEach(p -> result
                        .append(skillDir.relativize(p)).append("\n"));
            } catch (java.io.IOException e) {
                result.append("[unable to scan directory] ")
                        .append(e.getMessage()).append("\n");
            }
        }
        result.append(
                "Editing requires disk tools — ask access from the user if missing and needed.\n");
        result.append("=== BODY ===\n");
        result.append(getBody());
        return result.toString();
    }

    public String readRelativeFile(String relativePath) {
        if (skillDir == null) {
            throw new IllegalStateException(
                    "SKILL " + getName() + " has no files.");
        }
        // Strip leading slashes to avoid absolute path resolution
        String cleaned = relativePath.startsWith("/")
                ? relativePath.substring(1)
                : relativePath;
        Path target = skillDir.resolve(cleaned).normalize();
        if (!target.startsWith(skillDir)) {
            throw new IllegalArgumentException(
                    "Path traversal not allowed: " + relativePath);
        }
        if (!Files.exists(target)) {
            throw new IllegalArgumentException(
                    "File not found in skill directory: " + relativePath);
        }
        try {
            return Files.readString(target);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read " + target, e);
        }
    }
}
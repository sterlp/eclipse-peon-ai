package org.sterl.llmpeon.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.llmpeon.shared.FileUtils;

import lombok.Getter;
import lombok.Setter;

public class SkillPromptFile extends SimplePromptFile {
    @Nullable
    @Getter
    private volatile Path skillDir;

    @Getter
    @Setter
    private volatile SkillSource source = SkillSource.CONFIG;
    
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
          .append("\n")
          .append("description: ").append(getDescription())
          .append("\nsource: ").append(source.tag().trim());
        return sb.toString();
    }

    public String renderBody() {
        var result = new StringBuilder();
        result.append("=== SKILL: ").append(getName())
                .append(source.tag()).append(" ===").append(System.lineSeparator());
        if (skillDir == null) {
            result.append(getPromptFile()).append(System.lineSeparator());
            result.append("only a SKILL file.").append(System.lineSeparator());
        } else {
            result.append("SKILL disk dir: ").append(skillDir).append(System.lineSeparator());
            try (var stream = Files.walk(skillDir)) {
                stream.filter(Files::isRegularFile).forEach(p -> result
                        .append(FileUtils.normalizePath(skillDir.relativize(p).toString()))
                        .append(System.lineSeparator()));
            } catch (java.io.IOException e) {
                result.append("[unable to scan directory] ")
                      .append(e.getMessage()).append(System.lineSeparator());
            }
        }
        result.append("Editing requires disk edit tools — ask access from the user if missing and needed."
                + System.lineSeparator());
        result.append("=== BODY ===").append(System.lineSeparator());
        result.append(getBody());
        return result.toString();
    }

    public String readRelativeFile(String relativePath) {
        if (skillDir == null) {
            throw new IllegalStateException(
                    "SKILL " + getName() + " has no files.");
        }
        // Strip leading slashes to avoid absolute path resolution
        String cleaned = FileUtils.makeReltive(relativePath);

        var target = skillDir.resolve(cleaned);
        // accept SKILL path in the relative path
        if (!Files.exists(target)) target = skillDir.getParent().resolve(cleaned);

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
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + target, e);
        }
    }
}
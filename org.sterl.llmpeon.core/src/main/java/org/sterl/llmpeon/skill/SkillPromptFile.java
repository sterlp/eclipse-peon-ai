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
        // Defense in depth: reject traversal on the RAW input — makeReltive below strips "../",
        // which would turn a traversal into a silent "File not found" (SOLL-4, ADR-0046).
        // Normalize unconditionally: backslash→slash first, so mixed-separator traversals
        // (a/docs/..\..\x) are caught on every platform, not only where "\" is a separator.
        // One leading "/" is stripped before the check — the documented skill-relative contract
        // (makeReltive below does the same), so only true absolute paths and ".." escapes count.
        String rawPath = FileUtils.normalizePath(relativePath);
        if (rawPath.startsWith("/")) rawPath = rawPath.substring(1);
        Path base = skillDir.toAbsolutePath().normalize();
        Path raw = base.resolve(rawPath).normalize();
        if (!raw.startsWith(base)) {
            throw new IllegalArgumentException("Path traversal not allowed: " + relativePath);
        }

        // Strip leading slashes to avoid absolute path resolution
        String cleaned = FileUtils.makeReltive(relativePath);

        var target = resolveInSkill(cleaned);
        try {
            return Files.readString(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + target, e);
        }
    }

    /**
     * Resolves a cleaned, already traversal-checked relative path inside the skill directory:
     * first relative to the skill dir itself, then against its ancestors to accept
     * SKILL-qualified paths — a candidate only counts if it stays inside the skill dir.
     * A missing file yields "File not found", never an NPE.
     */
    private Path resolveInSkill(String cleaned) {
        Path base = skillDir.toAbsolutePath().normalize();
        Path plain = base.resolve(cleaned).normalize();
        if (Files.exists(plain)) return plain;
        for (Path ancestor = base.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            Path candidate = ancestor.resolve(cleaned).normalize();
            if (candidate.startsWith(base) && Files.exists(candidate)) return candidate;
        }
        throw new IllegalArgumentException("File not found in skill directory: " + cleaned);
    }
}
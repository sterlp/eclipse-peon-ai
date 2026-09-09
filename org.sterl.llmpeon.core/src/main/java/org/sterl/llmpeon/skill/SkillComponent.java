package org.sterl.llmpeon.skill;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.prompt.PromptYmlParser;

/**
 * One skill root (ADR-0042): identified by its responsible path, owns the
 * discovery + parse and <b>its own</b> skill map (key = lowercase name).
 *
 * <p>Refresh builds the new map fully and swaps the reference in one step —
 * readers never observe a half-filled map. A failed refresh keeps the previous
 * map and rethrows so the caller can report it (no clear-before-load).</p>
 *
 * <p>A missing or null path yields an empty map, not an error.</p>
 */
public class SkillComponent {

    private final SkillSource source;
    private volatile @Nullable Path dir;
    private volatile Map<String, SkillPromptFile> skills = Map.of();

    public SkillComponent(SkillSource source) {
        this.source = source;
    }

    /** The responsible path (null = no path set, empty slot). */
    public @Nullable Path path() {
        return dir;
    }

    /** Replaces the responsible path and reloads. Null path = empty component. */
    public void setPath(@Nullable Path newDir) throws IOException {
        this.dir = newDir;
        this.skills = load(newDir);
    }

    /**
     * Reloads the current path. On IOException the previous map is kept
     * untouched and the error rethrown for the caller to report.
     */
    public void refresh() throws IOException {
        this.skills = load(dir);
    }

    /** Current skills, keyed by lowercase name (immutable view). */
    public Map<String, SkillPromptFile> skills() {
        return skills;
    }

    public int loadedSkillCount() {
        return skills.size();
    }

    private Map<String, SkillPromptFile> load(@Nullable Path p) throws IOException {
        if (p == null) return Map.of();

        var normalized = p.toAbsolutePath().normalize();
        var result = new HashMap<String, SkillPromptFile>();
        if (Files.isDirectory(normalized)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(normalized)) {
                for (Path entry : entries) {
                    if (Files.isDirectory(entry)) {
                        handleDirectorySkill(entry, result);
                    } else if (Files.isRegularFile(entry)) {
                        handleFileSkill(entry, result);
                    }
                }
            }
        }
        return Map.copyOf(result);
    }

    private void handleFileSkill(Path entry, Map<String, SkillPromptFile> result) throws IOException {
        var yml = PromptYmlParser.parseYml(entry);
        if (yml != null) {
            var skill = SkillPromptFile.from(yml);
            skill.setSource(source);
            result.put(skill.getName().toLowerCase(Locale.ROOT), skill);
        }
    }

    private void handleDirectorySkill(Path entry, Map<String, SkillPromptFile> result) throws IOException {
        var skillFile = detectSkillFile(entry);
        if (skillFile != null && Files.isRegularFile(skillFile)) {
            var yml = PromptYmlParser.parseYml(skillFile);
            if (yml != null) {
                var skill = SkillPromptFile.from(yml, entry);
                skill.setSource(source);
                result.put(skill.getName().toLowerCase(Locale.ROOT), skill);
            }
        }
    }

    private Path detectSkillFile(Path dir) {
        var skillFile = dir.resolve("SKILL.md");
        if (Files.isRegularFile(skillFile)) return skillFile;
        skillFile = dir.resolve("skill.md");
        return Files.isRegularFile(skillFile) ? skillFile : null;
    }
}

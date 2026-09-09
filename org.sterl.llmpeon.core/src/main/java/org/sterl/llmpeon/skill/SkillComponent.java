package org.sterl.llmpeon.skill;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.sterl.llmpeon.prompt.PromptYmlParser;

/**
 * One skill root (ADR-0042): identified by its responsible path, owns the
 * discovery + parse and <b>its own</b> skill map (key = lowercase name).
 *
 * <p>A component <b>always has a path</b>. An empty component (no responsible
 * path yet) carries {@link #EMPTY_PATH} — a deliberate placeholder that is
 * never a real skill directory, so it loads as empty. The service expresses
 * "no project" by swapping in a fresh empty component, never by a null path.</p>
 *
 * <p>Refresh builds the new map fully and swaps the reference in one step —
 * readers never observe a half-filled map. A failed refresh keeps the previous
 * map and rethrows so the caller can report it (no clear-before-load).</p>
 */
public class SkillComponent {

    /** Deliberate placeholder path of an empty component — never a real skill dir. */
    public static final Path EMPTY_PATH = Path.of("peon-empty-skills-placeholder");

    private final SkillSource source;
    private volatile Path dir;
    private volatile Map<String, SkillPromptFile> skills = Map.of();

    /** Empty component: no responsible path yet (path = {@link #EMPTY_PATH}, no load). */
    public SkillComponent(SkillSource source) {
        this.source = source;
        this.dir = EMPTY_PATH;
    }

    /** Component for the given path — loads immediately, IOException on failure. */
    public SkillComponent(SkillSource source, Path dir) throws IOException {
        this(source);
        setPath(dir);
    }

    /** The responsible path — never null; {@link #EMPTY_PATH} while empty. */
    public Path path() {
        return dir;
    }

    /** Replaces the responsible path and reloads. */
    public void setPath(Path newDir) throws IOException {
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

    private Map<String, SkillPromptFile> load(Path p) throws IOException {
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

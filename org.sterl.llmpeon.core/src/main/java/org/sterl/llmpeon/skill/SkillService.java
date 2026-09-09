package org.sterl.llmpeon.skill;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Composes the skill components (ADR-0042) into one effective view: the
 * config component first, the project component overrides by lowercase name
 * — the override happens <b>only at read time</b>, the config map is never
 * touched by project switches.
 *
 * <p>Owns the name-keyed enabled state (R1): toggles survive every refresh
 * and project switch, are override-aware by name, and new names start
 * enabled. State is decorated onto the view instances at view build so
 * consumers reading {@link SkillPromptFile#isEnabled()} stay consistent.</p>
 *
 * <p>API note: {@link #refresh(Path)} sets + refreshes the <b>config</b> component
 * (kept for the existing config-dir callers); {@link #setProjectSkillsDir(Path)}
 * replaces only the project component; {@link #refreshAll()} refreshes every component.</p>
 */
public class SkillService {

    /** Project-local skills root, relative to the project disk path (ADR-0042). */
    public static final String PROJECT_SKILLS_DIR = ".agents/skills";

    private volatile SkillComponent configComponent = new SkillComponent(SkillSource.CONFIG);
    private volatile SkillComponent projectComponent = new SkillComponent(SkillSource.PROJECT);

    private final Map<String, Boolean> enabledByName = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;

    public SkillService() {
    }

    public SkillService(Path skillsDirectory) throws IOException {
        refresh(skillsDirectory);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Sets/refreshes the config component (compat: the old single-directory refresh). */
    public boolean refresh(String newPath) throws IOException {
        return this.refresh(newPath == null ? null : Path.of(newPath));
    }

    public boolean refresh(@Nullable Path newPath) throws IOException {
        if (newPath == null) {
            if (SkillComponent.EMPTY_PATH.equals(configComponent.path())) return false;
            configComponent = new SkillComponent(SkillSource.CONFIG);
            return true;
        }
        configComponent.setPath(newPath);
        return true;
    }

    /**
     * Replaces ONLY the project component (R2a, R14) — the config component is
     * untouched. Null = no project: a fresh empty component. Every non-null
     * switch is a fresh instance + load — no per-path cache (staleness risk
     * outweighs load cost).
     */
    public void setProjectSkillsDir(@Nullable Path projectSkillsDir) throws IOException {
        projectComponent = projectSkillsDir == null
                ? new SkillComponent(SkillSource.PROJECT)
                : new SkillComponent(SkillSource.PROJECT, projectSkillsDir);
    }

    /** Visible for tests: the current project component instance. */
    SkillComponent projectComponent() {
        return projectComponent;
    }

    /** Refreshes every component (R3). A failed refresh keeps the previous state and rethrows. */
    public void refreshAll() throws IOException {
        configComponent.refresh();
        projectComponent.refresh();
    }

    /** Effective view: config first, project overrides by lowercase name. */
    private Map<String, SkillPromptFile> effectiveView() {
        var merged = new LinkedHashMap<String, SkillPromptFile>(configComponent.skills());
        merged.putAll(projectComponent.skills());
        return merged;
    }

    private void decorate(Map<String, SkillPromptFile> view) {
        for (var entry : view.entrySet()) {
            entry.getValue().setEnabled(enabledByName.getOrDefault(entry.getKey(), true));
        }
    }

    /** Set enabled state for a specific skill by name — survives refreshes (R1). */
    public void setSkillEnabled(String skillName, boolean enabled) {
        var key = skillName.toLowerCase(Locale.ROOT);
        enabledByName.put(key, enabled);
        var skill = effectiveView().get(key);
        if (skill != null) skill.setEnabled(enabled);
    }

    /** Enable/disable all skills at once. */
    public void setAllSkillsEnabled(boolean enabled) {
        var view = effectiveView();
        view.keySet().forEach(key -> enabledByName.put(key, enabled));
        view.values().forEach(skill -> skill.setEnabled(enabled));
    }

    /** Total number of skills in the effective view regardless of enabled state. */
    public int loadedSkillCount() {
        return effectiveView().size();
    }

    /** Returns enabled skills of the effective view, empty list when the service is disabled. */
    public List<SkillPromptFile> getSkills() {
        if (!enabled) return List.of();
        var view = effectiveView();
        decorate(view);
        return view.values().stream()
                .filter(SkillPromptFile::isEnabled)
                .toList();
    }

    /** Returns all skills of the effective view regardless of global enabled state. */
    public List<SkillPromptFile> getAllLoadedSkills() {
        var view = effectiveView();
        decorate(view);
        return new LinkedList<>(view.values());
    }

    /**
     * Return the skill -- also the disabled ones; project variant wins on name collision.
     * Accepts tagged names as echoed from {@link #skillNames()} (e.g. {@code "review [project]"})
     * — the trailing source tag is stripped before the lookup, so an echo never misses.
     */
    public Optional<SkillPromptFile> get(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        var key = stripSourceTag(name).toLowerCase(Locale.ROOT);
        var skill = effectiveView().get(key);
        if (skill != null) skill.setEnabled(enabledByName.getOrDefault(key, true));
        return Optional.ofNullable(skill);
    }

    /**
     * Strips one trailing source tag (e.g. {@code " [project]"}) from a skill name,
     * case-insensitive. At most one tag; unknown tags are kept so such lookups miss as before.
     */
    private static String stripSourceTag(String name) {
        var lower = name.toLowerCase(Locale.ROOT);
        for (SkillSource source : SkillSource.values()) {
            var tag = source.tag();
            if (lower.endsWith(tag)) return name.substring(0, name.length() - tag.length()).strip();
        }
        return name;
    }

    public boolean hasSkills() {
        return enabled && !effectiveView().isEmpty();
    }

    /**
     * Returns all active skill names, tagged with their source, e.g.
     * {@code "review [project], deploy [config]"}.
     */
    public String skillNames() {
        return getSkills().stream()
                .map(skill -> skill.getName() + skill.getSource().tag())
                .collect(Collectors.joining(", "));
    }
}

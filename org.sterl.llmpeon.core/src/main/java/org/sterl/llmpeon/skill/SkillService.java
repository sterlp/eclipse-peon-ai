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
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/**
 * Composes the skill components (ADR-0042) into one effective view — three
 * roots with fixed precedence (R17): the deprecated {@code <configDir>/skills}
 * root, the user root {@code ~/.agents/skills} and the project root. Each one
 * overrides the previous by lowercase name; the override happens <b>only at
 * read time</b>, no component map is ever touched by project switches.
 *
 * <p>The service knows <b>no</b> environment paths: the composition root
 * resolves {@code ~/.agents/skills} and injects it (R17). A no-arg instance
 * therefore has no user root at all — what is not set stays empty.</p>
 *
 * <p>Owns the name-keyed enabled state (R1): toggles survive every refresh
 * and project switch, are override-aware by name, and new names start
 * enabled. State is decorated onto the view instances at view build so
 * consumers reading {@link SkillPromptFile#isEnabled()} stay consistent.</p>
 *
 * <p>API note: {@link #refresh(Path)} sets + refreshes <b>only</b> the deprecated
 * configDir component (kept for the existing config-dir callers);
 * {@link #setProjectSkillsDir(Path)} replaces only the project component;
 * {@link #refreshAll()} refreshes every component (R3); {@link #skillRoots()}
 * lists every responsible path in ascending precedence.</p>
 */
public class SkillService {

    /** Project-local skills root, relative to the project disk path (ADR-0042). */
    public static final String PROJECT_SKILLS_DIR = ".agents/skills";

    /**
     * User-home skills root, relative to the home dir (R17 — APM deploys skills
     * there). Resolved by the composition root; the service never reads the
     * home directory itself.
     */
    public static final String USER_SKILLS_DIR = ".agents/skills";

    /** Deprecated {@code ~/.peon/skills} root (= {@code <configDir>/skills}), set via {@link #refresh(Path)}. */
    private volatile SkillComponent legacyComponent = new SkillComponent(SkillSource.CONFIG);
    /** User root {@code ~/.agents/skills} (R17) — path fixed at construction, never swapped. */
    private final SkillComponent userAgentsComponent;
    private volatile SkillComponent projectComponent = new SkillComponent(SkillSource.PROJECT);

    private final Map<String, Boolean> enabledByName = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;

    /** No user root: exactly the roots given by {@link #refresh(Path)} and {@link #setProjectSkillsDir(Path)}. */
    public SkillService() {
        userAgentsComponent = new SkillComponent(SkillSource.CONFIG);
    }

    /**
     * @param userSkillsDir the {@code ~/.agents/skills} root (R17); {@code null} = no user root.
     *                      A missing directory loads empty — it is not an error (R17).
     */
    public SkillService(@Nullable Path userSkillsDir) throws IOException {
        this();
        if (userSkillsDir != null) {
            userAgentsComponent.setPath(userSkillsDir);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Sets/refreshes ONLY the deprecated configDir root (compat: the old single-directory refresh). */
    public boolean refresh(String newPath) throws IOException {
        return this.refresh(newPath == null ? null : Path.of(newPath));
    }

    public boolean refresh(@Nullable Path legacyDir) throws IOException {
        if (legacyDir == null) {
            if (SkillComponent.EMPTY_PATH.equals(legacyComponent.path())) return false;
            legacyComponent = new SkillComponent(SkillSource.CONFIG);
            return true;
        }
        legacyComponent.setPath(legacyDir);
        return true;
    }

    /**
     * Replaces ONLY the project component (R2a, R14) — the config components are
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

    /** The config-side components in ascending precedence (R17): deprecated root, then user root. */
    private List<SkillComponent> configComponents() {
        return List.of(legacyComponent, userAgentsComponent);
    }

    /**
     * Refreshes every component (R3) — both config roots and the project root.
     * A failed refresh keeps the previous state and rethrows.
     */
    public void refreshAll() throws IOException {
        for (var component : configComponents()) {
            component.refresh();
        }
        projectComponent.refresh();
    }

    /**
     * Every responsible path in ascending precedence (deprecated configDir, user
     * {@code .agents}, project); {@link SkillComponent#EMPTY_PATH} for a root that
     * is not set. A path is listed even when the directory does not exist.
     */
    public List<Path> skillRoots() {
        return Stream.concat(
                configComponents().stream().map(SkillComponent::path),
                Stream.of(projectComponent.path()))
                .toList();
    }

    /** Effective view: ascending precedence putAll — project overrides user root, that the deprecated root. */
    private Map<String, SkillPromptFile> effectiveView() {
        var merged = new LinkedHashMap<String, SkillPromptFile>();
        for (var component : configComponents()) {
            merged.putAll(component.skills());
        }
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
     * Return the skill -- also the disabled ones; the closest variant wins on name collision (R17).
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

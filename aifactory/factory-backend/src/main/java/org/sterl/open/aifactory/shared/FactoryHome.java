package org.sterl.open.aifactory.shared;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The single runtime home of the factory — everything the factory persists lives below it
 * (registry, clones, agent state, its own config store). It never reads or writes the plugin
 * config dir (R-WP-9).
 *
 * <p>Resolution order: property {@code aifactory.home}, then env {@code AIFACTORY_HOME}, then
 * {@code ~/.aifactory}.</p>
 */
@Component
public class FactoryHome {

    public static final String HOME_PROPERTY   = "aifactory.home";
    public static final String HOME_ENV        = "AIFACTORY_HOME";
    public static final String DEFAULT_DIR     = ".aifactory";
    public static final String PROJECTS_FILE   = "projects.json";
    public static final String PROJECTS_DIR    = "projects";
    public static final String STATE_DIR       = "state";
    public static final String CONFIG_FILE     = "config.properties";

    private final Path root;

    public FactoryHome(@Value("${" + HOME_PROPERTY + ":}") String configuredHome) {
        this.root = resolve(configuredHome).toAbsolutePath().normalize();
    }

    private static Path resolve(String configuredHome) {
        if (configuredHome != null && !configuredHome.isBlank()) {
            return Paths.get(configuredHome.trim());
        }
        String fromEnv = System.getenv(HOME_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Paths.get(fromEnv.trim());
        }
        return Paths.get(System.getProperty("user.home"), DEFAULT_DIR);
    }

    /** The single test seam — {@code @TempDir} homes instead of the developer's home dir. */
    public static FactoryHome of(Path root) {
        return new FactoryHome(root.toString());
    }

    public Path root() {
        return root;
    }

    /** Directory holding one sub-folder per cloned project. */
    public Path projectsDir() {
        return root.resolve(PROJECTS_DIR);
    }

    /** The clone target of a project — {@code <home>/projects/<name>}. */
    public Path projectDir(String name) {
        return projectsDir().resolve(name);
    }

    /** Per-project runtime state (agent history etc.), never inside the checkout. */
    public Path stateDir(String projectId) {
        return root.resolve(STATE_DIR).resolve(projectId);
    }

    public Path registryFile() {
        return root.resolve(PROJECTS_FILE);
    }

    public Path configFile() {
        return root.resolve(CONFIG_FILE);
    }
}

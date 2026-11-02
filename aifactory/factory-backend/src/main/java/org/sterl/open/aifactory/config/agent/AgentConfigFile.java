package org.sterl.open.aifactory.config.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.agent.CustomAgent;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.prompt.PromptYmlParser;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The config file of one agent slot: {@code <home>/agents/<id>/AGENT.md} — the directory the core
 * resolves below its config dir for an agent, and the file {@code AgentService} picks up, so the
 * factory writes where the core reads and defines no format of its own (D-11). Read and write run
 * through the core's {@link PromptYmlParser} and {@link SimplePromptFile}: the five canonical
 * {@link CustomAgent} keys in the frontmatter, the prompt body never touched.
 *
 * <p>Reading creates nothing — a factory that never overrode anything has no {@code agents} folder at
 * all, and the config page is opened far more often than an override is saved. Writing creates the
 * parents, and only then.</p>
 *
 * <p>The rewritten file is owner-only: like the config file it may carry a key (R-WP-16 "Rechte 600").</p>
 */
public class AgentConfigFile {

    /** The name {@code AgentService} resolves inside an agent directory; the core keeps it a literal. */
    public static final String AGENT_FILE = "AGENT.md";

    /**
     * The keys this page owns, by the core's canonical frontmatter names — {@code extra_body} is not
     * among them, and neither is a provider: the provider stays at the base level (D-13).
     */
    public static final List<String> CONFIG_KEYS = List.of(
            CustomAgent.MODEL, CustomAgent.URL, CustomAgent.API_KEY, CustomAgent.THINK,
            CustomAgent.TEMPERATURE);

    private final Path file;

    public AgentConfigFile(Path file) {
        this.file = file;
    }

    /** {@code <home>/agents/<id>/AGENT.md} — the slot's own file, nothing else below the home. */
    public static AgentConfigFile forSlot(FactoryHome home, String agentId) {
        return new AgentConfigFile(home.root().resolve(LlmConfig.AGENT_DIRECTORY).resolve(agentId)
                .resolve(AGENT_FILE));
    }

    public Path file() {
        return file;
    }

    /**
     * What the file carries: {@code null} for every field it does not name. {@link #overridden()} is the
     * whole story of the checkbox — R-WP-23 says it is no stored value, only what the file says.
     */
    public record Values(String model, String url, String apiKey, String think, String temperature) {

        public boolean overridden() {
            return named(model) || named(url) || named(apiKey) || named(think) || named(temperature);
        }

        private static boolean named(String value) {
            return value != null && !value.isBlank();
        }
    }

    /**
     * One save of one slot, in the three states of R-WP-16: {@code null} leaves a key alone, an empty
     * string removes it, a value writes it trimmed. {@link #clearing()} is the unchecked box — R-WP-23
     * deletes every config value of the slot, which is deliberately not "five empty strings" because a
     * clear must also reach a field the request never named.
     */
    public record Changes(boolean clearAll, String model, String url, String apiKey, String think,
            String temperature) {

        public static Changes clearing() {
            return new Changes(true, null, null, null, null, null);
        }

        /** The five keys in their canonical order, {@code null} meaning "not submitted". */
        public Map<String, String> byKey() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put(CustomAgent.MODEL, model);
            fields.put(CustomAgent.URL, url);
            fields.put(CustomAgent.API_KEY, apiKey);
            fields.put(CustomAgent.THINK, think);
            fields.put(CustomAgent.TEMPERATURE, temperature);
            return fields;
        }
    }

    /** The stored values, or all-null for a file that is not there. Never writes, never creates. */
    public Values read() {
        SimplePromptFile parsed = parse();
        if (parsed == null) {
            return new Values(null, null, null, null, null);
        }
        return new Values(parsed.firstOrDefault(CustomAgent.MODEL, null),
                parsed.firstOrDefault(CustomAgent.URL, null),
                parsed.firstOrDefault(CustomAgent.API_KEY, null),
                parsed.firstOrDefault(CustomAgent.THINK, null),
                parsed.firstOrDefault(CustomAgent.TEMPERATURE, null));
    }

    /**
     * Applies one save to the file and leaves everything else in it alone: the prompt body, the agent's
     * own keys ({@code tools}, {@code read-only}, …) and the fields another tool wrote. A save that ends
     * with nothing to store writes nothing — no file, no directory (UC-WP-45's file is only rewritten
     * when it actually carried something).
     */
    public void write(Changes changes) {
        SimplePromptFile parsed = parse();
        boolean existed = parsed != null;
        SimplePromptFile target = existed ? parsed
                : new SimplePromptFile(new LinkedHashMap<>(), "", file);
        Map<String, List<String>> frontmatter = target.getFrontmatter();

        // parseYml names a file that carries no name after its directory; writing that line back would
        // add a field nobody asked for, and the core derives the very same name from the directory
        if (existed && PromptYmlParser.defaultSkillName(file).equals(target.getName())) {
            frontmatter.remove(PromptYmlParser.NAME);
        }

        if (changes.clearAll()) {
            CONFIG_KEYS.forEach(frontmatter::remove);
        } else {
            changes.byKey().forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                if (value.isBlank()) {
                    frontmatter.remove(key);
                } else {
                    target.setValue(key, value.trim());
                }
            });
        }

        if (!existed && frontmatter.isEmpty()) {
            return;
        }
        save(target);
    }

    private SimplePromptFile parse() {
        try {
            return PromptYmlParser.parseYml(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("agent config file cannot be read", unreadable);
        }
    }

    /**
     * The file may carry a key, so it gets the mode the config file gets (R-WP-16). The mode is applied
     * after the write because the core's writer owns the create; a file system without posix modes keeps
     * its own promise and is not failed here.
     */
    private void save(SimplePromptFile target) {
        try {
            Files.createDirectories(file.getParent());
            target.save();
            if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file,
                        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
        } catch (IOException failed) {
            throw new UncheckedIOException("agent config file cannot be written", failed);
        }
    }
}

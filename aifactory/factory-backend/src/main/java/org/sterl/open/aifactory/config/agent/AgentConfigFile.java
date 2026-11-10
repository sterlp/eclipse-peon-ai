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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.sterl.llmpeon.agent.CustomAgent;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.prompt.PromptYmlParser;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The config file of one agent slot: {@code <home>/agents/<id>/AGENT.md}. Only the factory reads it —
 * {@code FactoryConfigService} lays the file over the loaded config (D-11); the factory never hands this
 * directory to the core, so "the core reads this file" is <em>not</em> true and nothing may depend on it.
 * What comes from the core is the format: read and write run through {@link PromptYmlParser} and
 * {@link SimplePromptFile}, the seven canonical {@link CustomAgent} keys in the frontmatter, the prompt
 * body never touched.
 *
 * <p>One consequence to know before the directory is ever wired into the core: it <em>is</em> the core's
 * custom-agent directory, and {@code AgentService} turns every entry in it into a custom agent named
 * after its folder — six agents the factory does not run. Harmless today, and {@code AgentSlots} pins the
 * six slots (Q9), so keeping the file where R-WP-23 puts it costs nothing.</p>
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
     * The keys this page owns, by the core's canonical frontmatter names — {@code extra_body} and
     * {@code tools} joined them with R-WP-52; a provider is not among them and stays at the base level.
     */
    public static final List<String> CONFIG_KEYS = List.of(
            CustomAgent.MODEL, CustomAgent.URL, CustomAgent.API_KEY, CustomAgent.THINK,
            CustomAgent.TEMPERATURE, CustomAgent.EXTRA_BODY, CustomAgent.TOOLS);

    /**
     * Serialises the read-modify-write per file, for the same reason {@code FactoryConfigService} locks
     * its own: a save parses, mutates and rewrites the whole head, so two interleaved saves lose one
     * writer's field, and a reader that arrives between the truncate and the write of {@code save()} sees
     * an empty head and reports an un-overridden slot. Shared for readers because the config page and
     * every turn read all six slots; keyed by file so one slot's save never waits for another's and the
     * map holds one entry per slot.
     */
    private static final Map<Path, ReadWriteLock> LOCKS = new ConcurrentHashMap<>();

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
    public record Values(String model, String url, String apiKey, String think, String temperature,
            String extraBody, String tools) {

        public boolean overridden() {
            return named(model) || named(url) || named(apiKey) || named(think) || named(temperature)
                    || named(extraBody) || named(tools);
        }

        private static boolean named(String value) {
            return value != null && !value.isBlank();
        }
    }

    /**
     * One save of one slot, in the three states of R-WP-16: {@code null} leaves a key alone, an empty
     * string removes it, a value writes it trimmed. {@link #clearing()} is the unchecked box — R-WP-23
     * deletes every config value of the slot, which is deliberately not "seven empty strings" because a
     * clear must also reach a field the request never named.
     */
    public record Changes(boolean clearAll, String model, String url, String apiKey, String think,
            String temperature, String extraBody, String tools) {

        public static Changes clearing() {
            return new Changes(true, null, null, null, null, null, null, null);
        }

        /** The seven keys in their canonical order, {@code null} meaning "not submitted". */
        public Map<String, String> byKey() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put(CustomAgent.MODEL, model);
            fields.put(CustomAgent.URL, url);
            fields.put(CustomAgent.API_KEY, apiKey);
            fields.put(CustomAgent.THINK, think);
            fields.put(CustomAgent.TEMPERATURE, temperature);
            fields.put(CustomAgent.EXTRA_BODY, extraBody);
            fields.put(CustomAgent.TOOLS, tools);
            return fields;
        }
    }

    /** The stored values, or all-null for a file that is not there. Never writes, never creates. */
    public Values read() {
        ReadWriteLock lock = lock();
        lock.readLock().lock();
        try {
            SimplePromptFile parsed = parse();
            if (parsed == null) {
                return new Values(null, null, null, null, null, null, null);
            }
            return new Values(parsed.firstOrDefault(CustomAgent.MODEL, null),
                    parsed.firstOrDefault(CustomAgent.URL, null),
                    parsed.firstOrDefault(CustomAgent.API_KEY, null),
                    parsed.firstOrDefault(CustomAgent.THINK, null),
                    parsed.firstOrDefault(CustomAgent.TEMPERATURE, null),
                    parsed.firstOrDefault(CustomAgent.EXTRA_BODY, null),
                    parsed.firstOrDefault(CustomAgent.TOOLS, null));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Applies one save to the file and leaves everything else in it alone: the prompt body, the agent's
     * own keys ({@code read-only}, {@code handover}, …) and the fields another tool wrote. A save that ends
     * with nothing to store writes nothing — no file, no directory (UC-WP-45's file is only rewritten
     * when it actually carried something).
     */
    public void write(Changes changes) {
        ReadWriteLock lock = lock();
        lock.writeLock().lock();
        try {
            apply(changes);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** The save itself — only ever called under the write lock, which is what makes it atomic. */
    private void apply(Changes changes) {
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

    /** One lock per file — the readers of the six slots share it, a writer holds it alone. */
    private ReadWriteLock lock() {
        return LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(),
                path -> new ReentrantReadWriteLock());
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

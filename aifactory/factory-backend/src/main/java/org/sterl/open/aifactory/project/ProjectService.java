package org.sterl.open.aifactory.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The project registry — the single writer of {@code <home>/projects.json}. A project only ever
 * enters the registry after its clone succeeded, and {@link #remove(String)} deletes entry and
 * checkout together (⏳ open point "remove = entry + clone").
 *
 * <p>The file is the state: every operation reads it, so nothing can drift and a moved home dir is
 * picked up immediately. Mutations are serialized by a lock (not by holding the monitor across a
 * clone) so a long clone never blocks a read.</p>
 *
 * <p>Same normalized URL twice → the existing entry, git is not called again (R-WP-3). The clone
 * target is {@code <home>/projects/<name>}, so a name that could escape that folder is rejected.</p>
 */
@Service
public class ProjectService {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectService.class);

    /** Folder-name shape we are willing to create below the projects dir — nothing else. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final FactoryHome home;
    private final GitService git;
    private final ObjectMapper mapper;
    private final Path registryFile;
    private final ReentrantLock writeLock = new ReentrantLock();

    public ProjectService(FactoryHome home, GitService git) {
        this.home = home;
        this.git = git;
        this.registryFile = home.registryFile();
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public List<Project> projects() {
        return read();
    }

    public Optional<Project> find(String id) {
        return read().stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    /**
     * @throws IllegalArgumentException blank/unsupported URL, a name that must not become a folder,
     *         a URL that is already registered (message names that project), or a failed clone
     *         (message is the git output)
     */
    public Project add(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new IllegalArgumentException("gitUrl must not be empty");
        }
        String url = gitUrl.trim();
        String normalized = normalizeUrl(url);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("not a git url: " + url);
        }
        String name = nameOf(normalized);
        Path target = home.projectDir(name);
        if (!SAFE_NAME.matcher(name).matches() || !target.startsWith(home.projectsDir())) {
            throw new IllegalArgumentException("unsupported project name \"" + name
                    + "\" — use letters, digits, dot, dash or underscore");
        }

        writeLock.lock();
        try {
            List<Project> entries = read();
            findByUrl(entries, normalized).ifPresent(existing -> {
                throw new IllegalArgumentException("Project \"" + existing.name()
                        + "\" is already present (" + existing.gitUrl() + ")");
            } );
            entries.stream().filter(entry -> entry.name().equalsIgnoreCase(name)).findFirst().ifPresent(present -> {
                throw new IllegalArgumentException("Project \"" + present.name()
                        + "\" is already present in " + present.localPath());
            } );

            git.clone(url, target); // throws CloneFailedException (400) / IllegalStateException (409)

            List<Project> updated = new ArrayList<>(entries);
            updated.add(new Project(idOf(name, normalized), name, url, target, ProjectState.READY));
            write(updated);
            Project added = updated.getLast();
            LOG.info("added project {} at {}", added.id(), target);
            return added;
        } finally {
            writeLock.unlock();
        }
    }

    /** Deletes the registry entry and its checkout — runtime state below the home has no keepers. */
    public void remove(String id) {
        writeLock.lock();
        Project project;
        try {
            List<Project> entries = new ArrayList<>(read());
            project = entries.stream().filter(entry -> entry.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ProjectNotFoundException(id));
            entries.remove(project);
            write(entries);
        } finally {
            writeLock.unlock();
        }
        deleteTree(project.localPath());
    }

    // ---------------------------------------------------------------- registry file

    private List<Project> read() {
        if (!Files.isRegularFile(registryFile)) {
            return List.of();
        }
        try {
            List<StoredProject> stored = mapper.readValue(Files.readAllBytes(registryFile),
                    new TypeReference<List<StoredProject>>() {
                    });
            return stored == null ? List.of()
                    : stored.stream().map(entry -> entry.toProject(home.root())).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + registryFile, e);
        }
    }

    /** Atomic temp + move, so a crash can never leave a half-written registry behind. */
    private void write(List<Project> entries) {
        try {
            Files.createDirectories(home.root());
            Path tmp = registryFile.resolveSibling(registryFile.getFileName() + ".tmp");
            Files.write(tmp, mapper.writeValueAsBytes(entries.stream()
                    .map(entry -> StoredProject.of(entry, home.root())).toList()));
            try {
                Files.move(tmp, registryFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, registryFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + registryFile, e);
        }
    }

    private static Optional<Project> findByUrl(List<Project> entries, String normalizedUrl) {
        return entries.stream()
                .filter(entry -> normalizeUrl(entry.gitUrl()).equals(normalizedUrl))
                .findFirst();
    }

    // ---------------------------------------------------------------- url / naming

    /** lowercase host, no scheme/credentials, no trailing slash, no trailing {@code .git}. */
    static String normalizeUrl(String gitUrl) {
        String value = gitUrl.trim();
        int schemeEnd = value.indexOf("://");
        if (schemeEnd >= 0) {
            value = value.substring(schemeEnd + 3);
        } else if (value.contains("@") && value.contains(":")) { // scp-like ssh url
            value = value.substring(value.indexOf('@') + 1);
        }
        if (value.contains("@")) { // strip credentials of an https url
            value = value.substring(value.indexOf('@') + 1);
        }
        int firstSlash = value.indexOf('/');
        if (firstSlash > 0) {
            String host = value.substring(0, firstSlash).toLowerCase(Locale.ROOT);
            value = host + value.substring(firstSlash);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith(".git")) {
            value = value.substring(0, value.length() - 4);
        }
        return value;
    }

    /** Last path segment of the normalized url — the folder name git would have created. */
    static String nameOf(String normalizedUrl) {
        int lastSlash = normalizedUrl.lastIndexOf('/');
        return lastSlash >= 0 ? normalizedUrl.substring(lastSlash + 1) : normalizedUrl;
    }

    /** Stable identity from the normalized url, so the same repo never yields two entries. */
    static String idOf(String name, String normalizedUrl) {
        String slug = name.replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(Locale.ROOT);
        if (slug.isEmpty()) {
            slug = "project";
        }
        return slug + "-" + shortHash(normalizedUrl);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 7);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }

    // ---------------------------------------------------------------- paths

    static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(dir)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not delete " + dir, e);
        }
    }
}

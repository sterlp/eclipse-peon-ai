package org.sterl.open.aifactory.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The project registry — the single writer of {@code <home>/projects.json}. A project only ever
 * enters the registry after its clone succeeded, and {@link #remove(String)} deletes entry and
 * checkout together (R-WP-19).
 *
 * <p>The file is the state: every operation reads it, so nothing can drift and a moved home dir is
 * picked up immediately. Mutations are serialized by a lock (not by holding the monitor across a
 * clone) so a long clone never blocks a read.</p>
 *
 * <p>Identity is the normalized URL — the same URL twice returns the existing project without
 * touching git (R-WP-2). The folder name comes from the URL, so two different repositories that
 * would share a folder are a conflict (R-WP-18), and only {@code https} urls without credentials are
 * accepted at all (R-WP-20).</p>
 *
 * <p>Path containment is a single rule in a single place: {@link ProjectPaths} in front of every
 * delete, and {@link StoredProject#toProject} on load. No operation here re-checks the folder prefix
 * by hand — {@code SAFE_NAME} already forbids the only character that could escape it.</p>
 */
@Service
public class ProjectService {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectService.class);

    /** Folder-name shape we are willing to create below the projects dir — nothing else. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final String HTTPS = "https";

    /**
     * R-WP-20 — v1 clones public repositories over {@code https} only. Static text on purpose: this
     * is what the 400 body, the log line and the UI alert show, so it can never repeat a credential.
     */
    static final String HTTPS_ONLY_HINT =
            "only public https:// git urls are supported — no ssh, no scp-like urls, no local paths";

    /** R-WP-20 — names no user, no host, no token, so echoing it anywhere stays safe. */
    static final String CREDENTIAL_HINT =
            "git urls must not carry credentials — public https repositories only";

    private final FactoryHome home;
    private final GitService git;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;
    private final Path registryFile;
    private final ReentrantLock writeLock = new ReentrantLock();

    public ProjectService(FactoryHome home, GitService git, ApplicationEventPublisher events) {
        this.home = home;
        this.git = git;
        this.events = events;
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
     * @return the fresh project, or the already registered one when the normalized url is known —
     *         then git is not called and nothing is written (R-WP-2 / UC-WP-3)
     * @throws IllegalArgumentException blank url, a url outside public {@code https} or carrying
     *         credentials (UC-WP-30 / UC-WP-33, the message never repeats the url), a name that must
     *         not become a folder, a name another project already occupies (message names that
     *         project, R-WP-18), or a failed clone (message is the git output)
     */
    public AddProjectResult add(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new IllegalArgumentException("gitUrl must not be empty");
        }
        String url = gitUrl.trim();
        requirePublicHttpsUrl(url); // before any normalization, lock or git call — nothing may be stored
        String normalized = normalizeUrl(url);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("not a git url: " + url);
        }
        String name = nameOf(normalized);
        Path target = home.projectDir(name);
        // SAFE_NAME forbids '/', so an accepted name cannot leave the projects dir — the containment
        // rule that is actually exercised is ProjectPaths behind every delete, plus StoredProject on load.
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("unsupported project name \"" + name
                    + "\" — use letters, digits, dot, dash or underscore");
        }

        writeLock.lock();
        try {
            List<Project> entries = read();
            Optional<Project> present = findByUrl(entries, normalized);
            if (present.isPresent()) {
                LOG.info("project {} already registered, reusing its clone", present.get().id());
                return new AddProjectResult(present.get(), false);
            }
            entries.stream().filter(entry -> entry.name().equalsIgnoreCase(name)).findFirst()
                    .ifPresent(occupant -> {
                        throw new IllegalArgumentException("Project \"" + occupant.name()
                                + "\" is already present in " + occupant.localPath());
                    } );

            git.clone(url, target); // throws CloneFailedException (400) / IllegalStateException (409)

            List<Project> updated = new ArrayList<>(entries);
            Project added = new Project(idOf(name, normalized), name, url, target, ProjectState.READY);
            updated.add(added);
            write(updated);
            LOG.info("added project {} at {}", added.id(), target);
            return new AddProjectResult(added, true);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Deletes the registry entry and its checkout (UC-WP-28). The path stored in the registry is only
     * ever <em>compared</em>, never obeyed: what gets deleted is {@code <home>/projects/<name>} and
     * nothing else (UC-WP-29).
     *
     * <p>Once both are gone it publishes {@link ProjectRemoved} — a fact about itself and nothing more.
     * Last, so nobody can rebuild what was just dropped from a request that still saw the entry.</p>
     */
    public void remove(String id) {
        writeLock.lock();
        Path checkout;
        try {
            List<Project> entries = new ArrayList<>(read());
            Project project = entries.stream().filter(entry -> entry.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ProjectNotFoundException(id));
            checkout = requireOwnCheckout(project);
            entries.remove(project);
            write(entries);
        } finally {
            writeLock.unlock();
        }
        deleteTree(checkout);
        events.publishEvent(new ProjectRemoved(id));
    }

    /** The stored path must be exactly the projects-dir path of that project's name. */
    private Path requireOwnCheckout(Project project) {
        Path allowed = ProjectPaths.requireInsideProjectsDir(home.projectsDir(), project.localPath());
        Path expected = home.projectDir(project.name()).toAbsolutePath().normalize();
        if (!allowed.equals(expected)) {
            throw new IllegalStateException("refusing to delete " + allowed + " — project \""
                    + project.id() + "\" should live in " + expected);
        }
        return allowed;
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
                    : stored.stream().map(entry -> entry.toProject(home.root(), home.projectsDir())).toList();
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

    /**
     * R-WP-20: {@code https} without credentials, nothing else — checked before {@link #normalizeUrl}
     * so a rejected url can never reach git, the registry file or a folder (UC-WP-30 / UC-WP-33).
     * The scp-like form ({@code git@host:org/repo.git}) is not a parsable URI and falls into the
     * {@code https} hint as well.
     */
    static void requirePublicHttpsUrl(String url) {
        URI uri = parseQuietly(url);
        if (uri != null && uri.getUserInfo() != null) {
            throw new IllegalArgumentException(CREDENTIAL_HINT);
        }
        if (uri == null || !HTTPS.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(HTTPS_ONLY_HINT);
        }
    }

    /** An unparsable url is user input, not a bug — answered by the hint, never by its syntax error. */
    private static URI parseQuietly(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
    }

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

    /** Reachable only behind {@link ProjectPaths#requireInsideProjectsDir} — never on a raw stored path. */
    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
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

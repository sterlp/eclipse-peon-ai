package org.sterl.open.aifactory.project;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.sterl.open.aifactory.project.dto.ProjectDirectoryDto;
import org.sterl.open.aifactory.project.dto.ProjectEntryDto;
import org.sterl.open.aifactory.project.dto.ProjectFileDto;
import org.sterl.open.aifactory.project.dto.ProjectFileRoot;

/**
 * The read edge of the file area (R-WP-14): one {@code GET}, two roots, no write path — there is
 * nothing for a caller to send here, which is why a {@code PUT} on the same route is a 405 and not a
 * slower way to write (UC-WP-19). Reading is not an agent ability, so no {@code WriteValidator} is
 * asked ({@code aifactory/docs/aifactory-architektur.md}, "Datei-Bereich").
 *
 * <p><b>The guard</b> is this class's own: it compares <em>real path against real path</em> —
 * {@code candidate.toRealPath()} against {@code root.toRealPath()}, never a raw root against a
 * resolved target, because on macOS the tmp root is itself reached through a symlink
 * ({@code /var → /private/var}) and a one-sided comparison would reject every honest tmp listing.
 * Containment is checked twice: lexically on the normalized target, which rejects {@code ..} whether
 * or not the target exists, and again on the real path, which rejects a symlink leaving its root.</p>
 *
 * <p>A rejection names the <em>root</em> and never the target — the resolved path of the file someone
 * was not allowed to read is itself the leak UC-WP-42 is about. It appears in no body at all, and a
 * successful body carries only names relative to its root.</p>
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectFilesController {

    /** Bounds against a 200 MB answer, not product promises (UC-WP-19 A4). */
    static final int MAX_ENTRIES = 500;
    static final int MAX_TEXT_BYTES = 65536;

    /** A NUL this early means the file is not text — reading on would only buy a bigger payload. */
    private static final int BINARY_PROBE_BYTES = 8192;

    private final ProjectService projects;

    public ProjectFilesController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping("/{id}/files")
    public Object files(@PathVariable("id") String id,
            @RequestParam(name = "root", defaultValue = "checkout") String root,
            @RequestParam(name = "path", defaultValue = "") String path) {
        ProjectFileRoot requestedRoot = ProjectFileRoot.from(root);
        Project project = projects.find(id).orElseThrow(() -> new ProjectNotFoundException(id));
        Path rootReal = realOrNotFound(requestedRoot.resolve(project.localPath()), requestedRoot);
        Path target = contain(requestedRoot, rootReal, path);
        String relative = relativize(rootReal, target);
        return Files.isDirectory(target)
                ? list(requestedRoot, relative, target)
                : read(requestedRoot, relative, target);
    }

    // ------------------------------------------------------------------ the guard

    /**
     * @param rootReal the already-resolved root — resolving <em>both</em> sides is what makes
     *        {@code /var} on macOS one directory instead of two names
     * @throws IllegalArgumentException the path is absolute or leaves the root (400, via the shared
     *         advice); the message names the root, never the target
     * @throws ResponseStatusException nothing lives at that path (404)
     */
    private static Path contain(ProjectFileRoot root, Path rootReal, String path) {
        Path relative = relativeOrReject(path, root.wireName());
        Path target = rootReal.resolve(relative).normalize();
        if (!target.startsWith(rootReal)) {
            throw leavesRoot(root, rootReal);
        }
        Path targetReal = realOrNotFound(target, root);
        if (!targetReal.startsWith(rootReal)) {
            throw leavesRoot(root, rootReal);
        }
        return targetReal;
    }

    private static Path relativeOrReject(String path, String rootName) {
        if (path == null || path.isBlank()) {
            return Path.of("");
        }
        try {
            Path relative = Path.of(path);
            if (relative.isAbsolute()) {
                throw new IllegalArgumentException("path must be relative to the " + rootName + " root");
            }
            return relative;
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("not a readable path below the " + rootName + " root");
        }
    }

    private static IllegalArgumentException leavesRoot(ProjectFileRoot root, Path rootReal) {
        return new IllegalArgumentException("path leaves the " + root.wireName() + " root " + rootReal);
    }

    /** A missing root or file is 404 — the request was well formed, there is only nothing there. */
    private static Path realOrNotFound(Path path, ProjectFileRoot root) {
        try {
            return path.toRealPath();
        } catch (NoSuchFileException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "nothing at that path below the " + root.wireName() + " root");
        } catch (IOException e) {
            throw new UncheckedIOException("could not resolve a path below " + root.wireName(), e);
        }
    }

    /**
     * The contained path as seen from its root, {@code /}-joined and empty for the root itself. Never
     * the raw query value: that may still hold a {@code ./} or a {@code ..} the guard has resolved
     * away, and echoing it would let the answer depend on how the request was spelled.
     */
    private static String relativize(Path rootReal, Path target) {
        StringBuilder joined = new StringBuilder();
        for (Path segment : rootReal.relativize(target)) {
            if (!joined.isEmpty()) {
                joined.append('/');
            }
            joined.append(segment);
        }
        return joined.toString();
    }

    // ------------------------------------------------------------------ the two shapes

    private static ProjectDirectoryDto list(ProjectFileRoot root, String path, Path directory) {
        List<ProjectEntryDto> entries = new ArrayList<>();
        boolean truncated;
        try (Stream<Path> children = Files.list(directory)) {
            List<Path> visible = children.limit(MAX_ENTRIES + 1L).toList();
            truncated = visible.size() > MAX_ENTRIES;
            visible.stream().limit(MAX_ENTRIES).map(ProjectFilesController::entry).forEach(entries::add);
        } catch (IOException e) {
            throw new UncheckedIOException("could not list a directory below " + root.wireName(), e);
        }
        entries.sort(Comparator.comparing(ProjectEntryDto::directory).reversed()
                .thenComparing(ProjectEntryDto::name));
        return ProjectDirectoryDto.of(root, path, entries, truncated);
    }

    /** A child unreadable between listing and stat is still an entry — a listing is a directory, not an index. */
    private static ProjectEntryDto entry(Path child) {
        boolean directory = Files.isDirectory(child);
        long size = directory || !Files.isRegularFile(child) ? 0L : sizeOf(child);
        return new ProjectEntryDto(child.getFileName().toString(), directory, size);
    }

    private static ProjectFileDto read(ProjectFileRoot root, String path, Path file) {
        byte[] head = readHead(file);
        int readable = Math.min(head.length, MAX_TEXT_BYTES);
        boolean truncated = head.length > MAX_TEXT_BYTES;
        boolean binary = containsNul(head, Math.min(readable, BINARY_PROBE_BYTES));
        String text = binary ? null : new String(head, 0, readable, StandardCharsets.UTF_8);
        return ProjectFileDto.of(root, path, text, sizeOf(file), binary, truncated);
    }

    /** One byte past the limit is enough to know the file is longer — never the whole file. */
    private static byte[] readHead(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(MAX_TEXT_BYTES + 1);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read a file below " + file.getParent(), e);
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static boolean containsNul(byte[] bytes, int limit) {
        for (int index = 0; index < limit; index++) {
            if (bytes[index] == 0) {
                return true;
            }
        }
        return false;
    }
}

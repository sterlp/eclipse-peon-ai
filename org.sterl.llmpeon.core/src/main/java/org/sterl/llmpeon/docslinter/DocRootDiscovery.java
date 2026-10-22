package org.sterl.llmpeon.docslinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * R-DL-26: finds doc roots by convention — directories literally named "docs" up to
 * {@link #MAX_DEPTH} levels below the scan root, each carrying at least one Markdown file.
 * Foreign build/vendor trees are never entered. Pure logic: no tool API, no disclosure,
 * no logging — the tool layer resolves and discloses.
 */
final class DocRootDiscovery {

    /** directory levels below the scan root a "docs" directory may sit in: docs/, {@code *}/docs/, {@code *}/{@code *}/docs/ */
    static final int MAX_DEPTH = 2;

    static final Set<String> SKIPPED_DIRS = Set.of(
            "node_modules", "target", "bin", "build", "out", ".git", ".settings", ".gradle");

    /**
     * @param roots   root-relative '/'-paths of dirs literally named "docs" with >= 1 .md;
     *                sorted, "docs" first
     * @param ignored docs-named dirs rejected for carrying no Markdown (disclosure-relevant honesty)
     */
    record Discovery(List<String> roots, List<String> ignored) {
    }

    static Discovery discover(Path root) {
        var roots = new ArrayList<String>();
        var ignored = new ArrayList<String>();
        collect(root, root, roots, ignored);
        roots.sort(Comparator.naturalOrder());
        if (roots.remove("docs")) {
            roots.add(0, "docs");
        }
        return new Discovery(List.copyOf(roots), List.copyOf(ignored));
    }

    private static void collect(Path current, Path root, List<String> roots, List<String> ignored) {
        if (root.relativize(current).getNameCount() > MAX_DEPTH) {
            return;
        }
        try (Stream<Path> children = Files.list(current)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                var name = child.getFileName().toString();
                if (SKIPPED_DIRS.contains(name)) {
                    continue;
                }
                if ("docs".equals(name)) {
                    var relative = root.relativize(child).toString().replace('\\', '/');
                    if (hasMarkdown(child)) {
                        roots.add(relative);
                    } else {
                        ignored.add(relative);
                    }
                } else {
                    collect(child, root, roots, ignored);
                }
            }
        } catch (IOException e) {
            // unreadable directory — it cannot contribute a root; the found list stays honest
        }
    }

    private static boolean hasMarkdown(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase().endsWith(".md"));
        }
    }

    private DocRootDiscovery() {
    }
}

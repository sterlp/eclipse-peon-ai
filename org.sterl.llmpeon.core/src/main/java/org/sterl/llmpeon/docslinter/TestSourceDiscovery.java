package org.sterl.llmpeon.docslinter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * R-DL-28 (docs/docs-linter.md)
 */
final class TestSourceDiscovery extends SimpleFileVisitor<Path> {

    private final Path searchRoot;
    private final Path projectRoot;
    private final List<Path> docRoots;
    private final List<Pattern> globPatterns;
    private final List<Path> found = new ArrayList<>();

    TestSourceDiscovery(Path searchRoot, Path projectRoot, List<Path> docRoots, List<Pattern> globPatterns) {
        this.searchRoot = searchRoot;
        this.projectRoot = projectRoot;
        this.docRoots = docRoots;
        this.globPatterns = globPatterns;
    }

    static List<Path> discover(List<Path> roots, Path projectRoot,
            List<Path> docRoots, List<Pattern> globPatterns) throws IOException {
        var seen = new LinkedHashSet<Path>();
        for (Path searchRoot : roots) {
            var visitor = new TestSourceDiscovery(searchRoot, projectRoot, docRoots, globPatterns);
            Files.walkFileTree(searchRoot, visitor);
            seen.addAll(visitor.found);
        }
        var files = new ArrayList<>(seen);
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        return !dir.equals(searchRoot) && ScanDirectoryPolicy.isExcluded(dir)
                ? FileVisitResult.SKIP_SUBTREE
                : FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (!Files.isRegularFile(file)) {
            return FileVisitResult.CONTINUE;
        }
        for (Path docRoot : docRoots) {
            if (file.startsWith(docRoot)) {
                return FileVisitResult.CONTINUE;
            }
        }
        String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        for (Pattern globPattern : globPatterns) {
            if (globPattern.matcher(relativePath).matches()) {
                found.add(file);
                break;
            }
        }
        return FileVisitResult.CONTINUE;
    }
}

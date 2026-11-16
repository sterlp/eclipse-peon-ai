package org.sterl.llmpeon.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.sterl.llmpeon.shared.FileUtils;

/**
 * A {@link WriteValidator} with dynamic roots (ADR-0043): the raw path is resolved against
 * {@code configDir} — the same base the write tool uses ({@link FileUtils#resolve}) — and is
 * allowed only when the resolved path's <b>real</b> location equals a root or lies below one
 * (R7, UC-WPV-1, UC-WPV-2). Roots are read at validate time, so they may change between calls
 * (projects opened/closed).
 */
public class DynamicRootsWriteValidator implements WriteValidator {

    private final Path configDir;
    private final Supplier<List<Path>> additionalRoots;

    public DynamicRootsWriteValidator(Path configDir, Supplier<List<Path>> additionalRoots) {
        this.configDir = configDir.toAbsolutePath().normalize();
        this.additionalRoots = additionalRoots == null ? List::of : additionalRoots;
    }

    @Override
    public void validate(String path) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        Path resolved = FileUtils.resolve(configDir, path);
        Path realTarget;
        try {
            realTarget = realLocation(resolved);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Write denied: '" + path + "' resolves to '" + resolved + "' which is outside this agent's allowed roots "
                            + allowedRoots() + ". You may only write to those roots.", e);
        }
        if (isUnderRealRoot(realTarget, configDir) || underAnyAdditionalRoot(realTarget)) return;
        throw new IllegalArgumentException(
                "Write denied: '" + path + "' resolves to '" + resolved + "' which is outside this agent's allowed roots "
                        + allowedRoots() + ". You may only write to those roots.");
    }

    @Override
    public String pathScopePrompt() {
        return "You may only write to these roots: " + allowedRoots() + ".";
    }

    private boolean underAnyAdditionalRoot(Path realTarget) {
        return additionalRoots().stream().anyMatch(root -> isUnderRealRoot(realTarget, root));
    }

    private List<Path> additionalRoots() {
        return additionalRoots.get().stream()
                .filter(Objects::nonNull)
                .map(root -> root.toAbsolutePath().normalize())
                .toList();
    }

    private List<Path> allowedRoots() {
        var roots = new ArrayList<Path>();
        roots.add(configDir);
        roots.addAll(additionalRoots());
        return roots;
    }

    private static boolean isUnderRealRoot(Path realTarget, Path root) {
        try {
            Path realRoot = realLocation(root);
            return realTarget.equals(realRoot) || realTarget.startsWith(realRoot);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Resolves a path to its real location, following symlinks. If the path does not exist,
     * walks up to the nearest existing ancestor, resolves that ancestor's real path, and
     * appends the remaining non-existent segments.
     * <p>
     * Only a {@code NoSuchFileException} from the NOFOLLOW existence probe triggers the parent
     * fallback — any failure from {@code toRealPath()} (dangling link, access denied, link loop)
     * propagates immediately so the caller can reject the write.
     */
    private static Path realLocation(Path path) throws IOException {
        Path candidate = path.toAbsolutePath().normalize();
        var remaining = new ArrayList<Path>();

        while (true) {
            boolean exists;
            try {
                Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                exists = true;
            } catch (NoSuchFileException e) {
                exists = false;
            }
            if (!exists) {
                Path name = candidate.getFileName();
                if (name == null) throw new NoSuchFileException(candidate.toString());
                remaining.add(name);
                candidate = candidate.getParent();
                continue;
            }
            Path real = candidate.toRealPath();
            if (remaining.isEmpty()) return real;
            Path result = real;
            for (int i = remaining.size() - 1; i >= 0; i--) {
                result = result.resolve(remaining.get(i));
            }
            return result;
        }
    }
}
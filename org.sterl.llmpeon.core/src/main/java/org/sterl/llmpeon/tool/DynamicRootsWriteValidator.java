package org.sterl.llmpeon.tool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.sterl.llmpeon.shared.FileUtils;

/**
 * A {@link WriteValidator} with dynamic roots (ADR-0043): the raw path is resolved against
 * {@code configDir} — the same base the write tool uses ({@link FileUtils#resolve}) — and is
 * allowed only when the resolved path equals a root or lies below one. Roots are read at
 * validate time, so they may change between calls (projects opened/closed).
 *
 * <p>Traversal ({@code ..}) is killed by resolution + normalization, absolute paths stay
 * absolute. Denials throw {@link IllegalArgumentException} listing the allowed roots, which the
 * tool loop surfaces to the model via onProblem.</p>
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
        if (isUnderRoot(resolved, configDir) || underAnyAdditionalRoot(resolved)) return;
        throw new IllegalArgumentException(
                "Write denied: '" + path + "' resolves to '" + resolved + "' which is outside this agent's allowed roots "
                        + allowedRoots() + ". You may only write to those roots.");
    }

    private boolean underAnyAdditionalRoot(Path resolved) {
        return additionalRoots().stream().anyMatch(root -> isUnderRoot(resolved, root));
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

    private static boolean isUnderRoot(Path resolved, Path root) {
        return resolved.equals(root) || resolved.startsWith(root);
    }
}

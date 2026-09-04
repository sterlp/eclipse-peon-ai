package org.sterl.llmpeon.tool;

import java.util.List;

import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.shared.RegexUtils;

/**
 * A {@link WriteValidator} that allows a write only when the <b>normalized</b> path matches one of a
 * set of globs (OR). Normalization resolves {@code .}/{@code ..} segments so path traversal cannot
 * bypass the allowlist. Globs are translated to regex and compiled once + cached in {@link RegexUtils}.
 */
public class AllowlistWriteValidator implements WriteValidator {

    private final List<String> globs;

    public AllowlistWriteValidator(String... globs) {
        this.globs = List.of(globs);
    }

    @Override
    public void validate(String path) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        String normalized = FileUtils.normalizeSegments(path);
        for (String glob : globs) {
            if (RegexUtils.globToPattern(glob).matcher(normalized).matches()) return;
        }
        throw new IllegalArgumentException(
                "Write denied: '" + path + "' is outside this agent's allowed paths " + globs
                + ". You may only write to " + globs + ".");
    }
}

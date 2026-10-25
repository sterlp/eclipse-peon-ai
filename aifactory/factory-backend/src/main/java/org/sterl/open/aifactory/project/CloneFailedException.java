package org.sterl.open.aifactory.project;

import java.nio.file.Path;

/**
 * A clone that did not succeed. An {@link IllegalArgumentException} on purpose: the API contract is
 * {@code 400} whose message is the git output verbatim — that is what the UI prints at the input
 * (R-WP-4).
 */
public class CloneFailedException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String gitUrl;
    private final Path target;
    private final String gitOutput;

    public CloneFailedException(String gitUrl, Path target, String gitOutput) {
        super(gitOutput);
        this.gitUrl = gitUrl;
        this.target = target;
        this.gitOutput = gitOutput;
    }

    public String gitUrl() {
        return gitUrl;
    }

    public Path target() {
        return target;
    }

    public String gitOutput() {
        return gitOutput;
    }
}

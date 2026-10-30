package org.sterl.llmpeon.tool;

/**
 * Vets the raw path an agent's model passes to a write tool. Provided per request by the agent
 * (see {@code AiAgent.getWriteValidator()}) and enforced at the write tool's path entry.
 */
public interface WriteValidator {

    public static final String JON_PATH_SCOPE_PROMPT = """
            You may write ANY file type under any `docs/` directory, plus `*.md` files anywhere.""";
    /**
     * @param path the raw path string the model supplied to the write tool
     * @throws IllegalArgumentException if this agent may not write to that path
     */
    void validate(String path);

    /**
     * How this validator's scope is worded for the agent's system prompt. Empty (the default) means
     * there is nothing to say and the prompt gains no scope paragraph.
     *
     * <p>Invariant: whoever enforces the boundary also words the claim, so a prompt can never promise
     * a scope its own {@link #validate(String)} would deny
     * (docs/write-path-validator.md R6).</p>
     */
    default String pathScopePrompt() {
        return "";
    }

    /** No restriction — the default for every agent except Jon. */
    WriteValidator ALLOW_ALL = path -> { /* everything is allowed */ };

    /** Jon's scope: a docs folder at any depth, plus any Markdown file. */
    WriteValidator DOCS = new AllowlistWriteValidator("*/docs/*", "*.md");

    /** Denies every write — for read-only agents like Review. */
    WriteValidator DENY_ALL = new DenyAllWriteValidator();
}

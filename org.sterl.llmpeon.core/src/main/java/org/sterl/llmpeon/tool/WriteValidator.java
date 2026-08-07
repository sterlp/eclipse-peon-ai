package org.sterl.llmpeon.tool;

/**
 * Vets the raw path an agent's model passes to a write tool. Provided per request by the agent
 * (see {@code AiAgent.getWriteValidator()}) and enforced at the write tool's path entry.
 */
public interface WriteValidator {

    /**
     * @param path the raw path string the model supplied to the write tool
     * @throws IllegalArgumentException if this agent may not write to that path
     */
    void validate(String path);

    /** No restriction — the default for every agent except Jon. */
    WriteValidator ALLOW_ALL = path -> { /* everything is allowed */ };

    /** Jon's scope: a docs folder at any depth, plus any Markdown file. */
    WriteValidator DOCS = new AllowlistWriteValidator("*/docs/*", "*.md");
}

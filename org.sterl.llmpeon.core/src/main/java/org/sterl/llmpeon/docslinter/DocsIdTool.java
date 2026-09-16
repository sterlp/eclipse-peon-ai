package org.sterl.llmpeon.docslinter;

import java.util.List;

import org.sterl.llmpeon.tool.tools.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Narrow, read-only facade for ID allocation.
 *
 * Delegates to the shared {@link DocsLinterTool} core instance so there is exactly one
 * project root and one implementation of the allocation logic.
 */
public final class DocsIdTool extends AbstractTool {

    private final DocsLinterTool core;

    public DocsIdTool(DocsLinterTool core) {
        this.core = core;
    }

    @Tool("Return next available rule and use-case numbers per prefix from opted-in Markdown docs.")
    public String nextIds(
            @P(required = false, name = "root") String root,
            @P(required = false, name = "docRoots") List<String> docRoots,
            @P(required = false, name = "prefix") String prefix) {

        return core.nextIds(root, docRoots, prefix);
    }
}

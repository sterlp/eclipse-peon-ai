package org.sterl.llmpeon.parts.tools;

import java.util.List;

/**
 * Owns all tools and the shared {@link ToolContext}.
 * Tools are stable instances — only the context changes when the user selects a different file/project.
 */
public class ToolService {

    private final ToolContext context = new ToolContext();
    private final SearchFilesTool searchFilesTool = new SearchFilesTool(context);
    private final ReadFileTool readFileTool = new ReadFileTool(context);
    private final ReadSelectedFileTool readSelectedFileTool = new ReadSelectedFileTool(context);
    private final UpdateSelectedFileTool updateSelectedFileTool = new UpdateSelectedFileTool(context);

    public ToolContext getContext() {
        return context;
    }

    public List<Object> getTools() {
        return List.of(searchFilesTool, readFileTool, readSelectedFileTool, updateSelectedFileTool);
    }
}

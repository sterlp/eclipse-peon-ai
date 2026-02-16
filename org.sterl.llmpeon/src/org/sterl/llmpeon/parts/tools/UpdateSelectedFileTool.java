package org.sterl.llmpeon.parts.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class UpdateSelectedFileTool {

    private final ToolContext context;

    public UpdateSelectedFileTool(ToolContext context) {
        this.context = context;
    }

    @Tool("Replaces the complete content of the currently selected file with the provided new content. "
            + "Use this after reading the current by the developer selected file to apply your changes.")
    public String updateCurrentFile(
            @P("The complete new file content that will replace the existing content") String newContent) {

        String selected = context.getSelectedFile();
        if (selected == null) return "No file is currently selected";

        return context.writeFile(selected, newContent);
    }
}

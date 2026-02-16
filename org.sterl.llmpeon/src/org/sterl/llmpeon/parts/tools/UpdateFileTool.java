package org.sterl.llmpeon.parts.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class UpdateFileTool {

    private final ToolContext context;

    public UpdateFileTool(ToolContext context) {
        this.context = context;
    }

    @Tool("Replaces the complete content of a file given its path with the provided new content. "
            + "Use searchFiles first to find the path of the file you want to update.")
    public String updateFile(
            @P("The path of the file to update, e.g. '/ProjectName/src/Main.java'") String filePath,
            @P("The complete new file content that will replace the existing content") String newContent) {

        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath must not be empty";
        }

        return context.writeFile(filePath, newContent);
    }
}

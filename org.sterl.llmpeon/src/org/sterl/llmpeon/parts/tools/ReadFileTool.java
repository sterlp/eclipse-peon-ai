package org.sterl.llmpeon.parts.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class ReadFileTool {

    private final EclipseToolContext context;

    public ReadFileTool(EclipseToolContext context) {
        this.context = context;
    }

    @Tool("Reads and returns the complete content of a file given its path. "
            + "Use searchFiles first to find the path of the file you want to read.")
    public String readFile(
            @P("The path of the file to read, e.g. '/ProjectName/src/Main.java'") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath must not be empty";
        }

        return context.readFile(filePath);
    }
}

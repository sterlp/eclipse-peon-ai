package org.sterl.llmpeon.parts.tools;

import dev.langchain4j.agent.tool.Tool;

public class ReadSelectedFileTool {

    private final EclipseToolContext context;

    public ReadSelectedFileTool(EclipseToolContext context) {
        this.context = context;
    }

    @Tool("Reads and returns the complete content of the currently selected file in the eclipse workspace")
    public String readCurrentFile() {
        return context.readSelectedFile();
    }
}

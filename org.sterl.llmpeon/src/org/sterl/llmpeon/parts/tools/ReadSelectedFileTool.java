package org.sterl.llmpeon.parts.tools;

import org.sterl.llmpeon.tool.model.AbstractTool;

import dev.langchain4j.agent.tool.Tool;

public class ReadSelectedFileTool extends AbstractTool {

    private final EclipseToolContext context;

    public ReadSelectedFileTool(EclipseToolContext context) {
        this.context = context;
    }

    @Tool("Reads and returns the complete content of the currently selected file in the eclipse workspace")
    public String readCurrentFile() {
        monitorMessage("Reading selected file");
        return context.readSelectedFile();
    }
    
    @Override
    public boolean isActive() {
        return context.getSelectedFile() != null;
    }
}

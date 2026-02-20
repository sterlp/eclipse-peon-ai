package org.sterl.llmpeon.parts.tools;

import org.sterl.llmpeon.tool.model.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class UpdateSelectedFileTool extends AbstractTool {

    private final EclipseToolContext context;

    public UpdateSelectedFileTool(EclipseToolContext context) {
        this.context = context;
    }

    @Tool("Replaces the complete content of the currently selected file with the provided new content. "
            + "Use this after reading the current by the developer selected file to apply your changes.")
    public String updateCurrentFile(
            @P("The complete new file content that will replace the existing content") String newContent) {

        String filePath = context.getSelectedFile();
        if (filePath == null) return "No file is currently selected";

        var result = context.writeFile(filePath, newContent);
        if (hasMonitor()) monitor.onFileUpdate(result);
        return "Selected file " + result.file() + " updated.";
    }
    
    @Override
    public boolean isActive() {
        return context.getSelectedFile() != null;
    }
}

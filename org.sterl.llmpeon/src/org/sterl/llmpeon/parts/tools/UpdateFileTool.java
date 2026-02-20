package org.sterl.llmpeon.parts.tools;

import org.sterl.llmpeon.tool.model.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class UpdateFileTool extends AbstractTool {

    protected final EclipseToolContext context;

    public UpdateFileTool(EclipseToolContext context) {
        this.context = context;
    }

    @Tool("Updates the complete content of an existing workspace file. "
            + "Only works for files that already exist — does not create new files. "
            + "Use searchFiles first to find the correct workspace path. "
            + "Returns an error if the file cannot be found.")
    public String updateFile(
            @P("The workspace-relative path of the file to update, e.g. '/ProjectName/src/Main.java' or 'src/Main.java'") String filePath,
            @P("The complete new file content that will replace the existing content") String newContent) {

        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath must not be empty";
        }
        if (newContent == null || newContent.isBlank()) {
            return "Error: newContent must not be empty";
        }
        var result = context.writeFile(filePath, newContent);
        if (hasMonitor()) monitor.onFileUpdate(result);
        return "File " + result.file() + " updated.";
    }
}

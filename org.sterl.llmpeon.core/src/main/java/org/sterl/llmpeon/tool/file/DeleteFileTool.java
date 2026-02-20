package org.sterl.llmpeon.tool.file;

import org.sterl.llmpeon.tool.model.AbstractTool;
import org.sterl.llmpeon.tool.model.FileContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class DeleteFileTool extends AbstractTool {

    private final FileContext context;

    public DeleteFileTool(FileContext context) {
        this.context = context;
    }

    @Tool("Deletes a file at the given path. "
            + "Use searchFiles first to find the correct path. "
            + "Returns a status message indicating success or failure.")
    public String deleteFile(
            @P("The path of the file to delete") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath must not be empty";
        }

        monitorMessage("Deleting " + filePath);
        return context.deleteFile(filePath);
    }
}

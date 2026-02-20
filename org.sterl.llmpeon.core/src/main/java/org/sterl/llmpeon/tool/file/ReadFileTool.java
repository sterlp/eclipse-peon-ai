package org.sterl.llmpeon.tool.file;

import org.sterl.llmpeon.tool.model.AbstractTool;
import org.sterl.llmpeon.tool.model.FileContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class ReadFileTool extends AbstractTool {

    private final FileContext context;

    public ReadFileTool(FileContext context) {
        this.context = context;
    }

    @Tool("Reads and returns the complete content of a file given its path. "
            + "Use searchFiles first to find the path of the file you want to read.")
    public String readFile(
            @P("The path of the file to read") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            monitorMessage("Not found " + filePath);
            return "Error: filePath must not be empty";
        }

        monitorMessage("Reading " + filePath);
        return context.readFile(filePath);
    }
}

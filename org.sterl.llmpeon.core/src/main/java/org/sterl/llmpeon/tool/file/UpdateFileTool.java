package org.sterl.llmpeon.tool.file;

import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.model.AbstractTool;
import org.sterl.llmpeon.tool.model.FileContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class UpdateFileTool extends AbstractTool {

    private final FileContext context;

    public UpdateFileTool(FileContext context) {
        this.context = context;
    }

    @Tool("Updates the complete content of an existing file. "
            + "Only works for files that already exist — does not create new files. "
            + "Use searchFiles first to find the correct path. "
            + "Returns an error if the file cannot be found.")
    public String updateFile(
            @P("The file path of the file to update") String filePath,
            @P("The complete new file content that will replace the existing content") String newContent) {

        if (StringUtil.hasNoValue(filePath)) return "Error: File path required";
        if (StringUtil.hasNoValue(newContent)) return "Error: File content required";
        
        var result = context.writeFile(filePath, newContent);
        if (hasMonitor()) monitor.onFileUpdate(result);
        System.err.println("updateFile: " + filePath +" written to " + result.file());
        return "File " + result.file() + " updated.";
    }
}

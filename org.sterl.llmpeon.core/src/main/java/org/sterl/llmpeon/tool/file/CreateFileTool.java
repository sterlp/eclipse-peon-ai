package org.sterl.llmpeon.tool.file;

import java.nio.file.Path;

import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.AbstractTool;
import org.sterl.llmpeon.tool.model.FileContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CreateFileTool extends AbstractTool {

    private final FileContext context;

    public CreateFileTool(FileContext context) {
        this.context = context;
    }

    @Tool("""
          Creates a new file or overwrites an existing one.
          The path can include the project name ('myproject/src/com/Foo.java') or be project-relative ('src/com/Foo.java').
          Missing parent folders are created automatically. Ensure to create the files in the correct position, folder and project structure.
          """)
    public String createFile(
            @P("File path, e.g. 'src/com/example' or 'myproject/src/com/example'") String path,
            @P("File name, e.g. 'Foo.java' or 'Foo.java'") String name,
            @P("File content to write") String newContent) {

        if (StringUtil.hasNoValue(path)) return "Error: File path required";
        if (StringUtil.hasNoValue(name)) return "Error: File name required";
        if (StringUtil.hasNoValue(newContent)) return "Error: File content required";

        var filePath = Path.of(path, name).toString();
        if (newContent == null || newContent.isBlank()) {
            return "Error: newContent must not be empty";
        }

        monitorMessage("Created " + filePath);
        return context.createFile(filePath, newContent);
    }
}

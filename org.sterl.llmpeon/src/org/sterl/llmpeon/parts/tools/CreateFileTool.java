package org.sterl.llmpeon.parts.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CreateFileTool {

    private final EclipseToolContext context;

    public CreateFileTool(EclipseToolContext context) {
        this.context = context;
    }

    @Tool("Creates a new file or overwrites an existing one in the Eclipse workspace. "
            + "The path can include the project name ('myproject/src/com/Foo.java') or be project-relative ('src/com/Foo.java'). "
            + "Missing parent folders are created automatically. "
            + "Uses the project's configured encoding for the file content. "
            + "If no active project can be determined, returns the available project names to choose from.")
    public String createFile(
            @P("File path, e.g. 'src/com/example/Foo.java' or 'myproject/src/com/example/Foo.java'") String filePath,
            @P("Complete file content to write") String newContent) {

        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath must not be empty";
        }
        if (newContent == null || newContent.isBlank()) {
            return "Error: newContent must not be empty";
        }

        return context.createFile(filePath, newContent);
    }
}

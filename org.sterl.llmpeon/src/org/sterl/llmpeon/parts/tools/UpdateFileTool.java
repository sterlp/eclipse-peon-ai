package org.sterl.llmpeon.parts.tools;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Generic file update tool – no Eclipse dependencies.
 * Receives a {@link Path} at construction time and lets the LLM
 * replace the full file content.
 */
public class UpdateFileTool {

    private final Path filePath;
    private final Charset charset;

    public UpdateFileTool(Path filePath) {
        this(filePath, StandardCharsets.UTF_8);
    }

    public UpdateFileTool(Path filePath, Charset charset) {
        this.filePath = filePath;
        this.charset = charset;
    }

    @Tool("Replaces the complete content of the currently selected file with the provided new content. "
            + "Use this after reading the file to apply your changes.")
    public String updateFile(
            @P("The complete new file content that will replace the existing content") String newContent) {

        if (filePath == null) {
            return "Error: no file path configured";
        }
        if (newContent == null) {
            return "Error: newContent must not be null";
        }

        try {
            Files.writeString(filePath, newContent, charset);
            return "Successfully updated file: " + filePath.getFileName();
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    public Path getFilePath() {
        return filePath;
    }
}

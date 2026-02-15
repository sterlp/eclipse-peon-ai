package org.sterl.llmpeon.parts.tools;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Generic file update tool – no Eclipse dependencies.
 * Receives a {@link Path} at construction time and lets the LLM
 * replace the full file content.
 */
public class UpdateFileTool {

    private final IFile file;

    public UpdateFileTool(IFile file) {
        this.file = file;
    }

    @Tool("Replaces the complete content of the currently selected file with the provided new content. "
            + "Use this after reading the file to apply your changes.")
    public String updateCurrentFile(
            @P("The complete new file content that will replace the existing content") String newContent) {

        try {
            try {
                Files.writeString(file.getFullPath().toPath(), newContent, Charset.forName(file.getCharset()));
            } catch (CoreException e) {
                System.err.println("Failed to get charset " + e.getMessage());
                Files.writeString(file.getFullPath().toPath(), newContent);
            }
            return "Successfully updated file: " + file.getFullPath();
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }
}

package org.sterl.llmpeon.parts.tools;

import org.eclipse.core.resources.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.Tool;

public class SelectedFileTool {
    private final IFile file;
    
    public SelectedFileTool(IFile file) {
        this.file = file;
    }
    
    @Tool("Reads and returns the complete content of the currently selected file in the Eclipse workspace")
    public String readCurrentFile() {
        if (file == null) {
            return "No file is currently selected";
        }
        
        try (InputStream contents = file.getContents();
            var reader = new BufferedReader(new InputStreamReader(contents, file.getCharset()))) {
            System.err.println("Readinmg " + file.getName());
            return reader.lines().collect(Collectors.joining("\n"));
            
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
    
    @Tool("Returns metadata about the currently selected file including name, path, size, and type")
    public String getFileInfo() {
        if (file == null) {
            return "No file is currently selected";
        }
        
        try {
            return String.format("""
                File: %s
                Full path: %s
                Location: %s
                Size: %d bytes
                Type: %s
                Charset: %s
                """,
                file.getName(),
                file.getFullPath().toString(),
                file.getLocation() != null ? file.getLocation().toString() : "N/A",
                file.getLocation() != null ? file.getLocation().toFile().length() : 0,
                file.getFileExtension(),
                file.getCharset()
            );
        } catch (Exception e) {
            return "Error getting file info: " + e.getMessage();
        }
    }
}
package org.sterl.llmpeon.parts.tools;

import org.eclipse.core.resources.IFile;

import dev.langchain4j.agent.tool.Tool;

public class SelectedFileTool extends AbstractEclipseFileTool {
    private final IFile file;
    
    public SelectedFileTool(IFile file) {
        this.file = file;
    }
    
    @Tool("Reads and returns the complete content of the currently selected file in the eclipse workspace")
    public String readCurrentFile() {
        if (file == null) {
            return "No file is currently selected";
        }
        return getFileInfo(file) + "Content:\n" + read(file);
    }
}
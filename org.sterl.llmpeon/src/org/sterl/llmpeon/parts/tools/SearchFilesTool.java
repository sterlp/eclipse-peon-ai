package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Eclipse-specific tool that searches for files in the workspace.
 * Returns metadata including the full filesystem path so that
 * generic tools (like {@link ReadFileTool}) can operate on the results.
 */
public class SearchFilesTool extends AbstractEclipseFileTool {

    @Tool("Searches for files in the Eclipse workspace whose name contains the given query string. "
            + "Returns a list of matching files with name and meta data.")
    public String searchFiles(
            @P("Part of the file name to search for, e.g. 'Controller' or '.xml'") String query) {

        if (query == null || query.isBlank()) {
            return "Error: query must not be empty";
        }

        var matches = new ArrayList<String>();
        String lowerQuery = query.toLowerCase();

        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isOpen()) continue;

            try {
                project.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (resource.getType() == IResource.FILE
                                && resource.getName().toLowerCase().contains(lowerQuery)) {
                                IFile file = (IFile) resource;
                                matches.add(getFileInfo(file));
                            }
                        return true; // continue visiting children
                    }
                });
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
        }

        System.err.println("Searched file " + query + " found " + matches.size() + " files ...");

        if (matches.isEmpty()) {
            return "No files found matching '" + query + "'";
        }
        

        var result = "Found " + matches.size() + " file(s):\n" + String.join("\n", matches);
        System.err.println(result);
        return result;
    }
}

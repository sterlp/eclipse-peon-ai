
package org.sterl.llmpeon.parts.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.FileUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseWorkspaceReadFilesTool extends AbstractEclipseTool {

    private IProject currentProject;

    public void setCurrentProject(IProject project) {
        this.currentProject = project;
    }

    public IProject getCurrentProject() {
        return currentProject;
    }
    
    @Override
    public boolean isEditTool() {
        return false;
    }

    public static final String READ_ECLIPSE_FILE_TOOL = "readWorkspaceFile";
    @Tool(name = READ_ECLIPSE_FILE_TOOL, value = "Eclipse: Read workspace file (e.g. '/Project/src/Foo.java').")
    public String readWorkspaceFile(@P("workspace-relative path") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        var file = EclipseUtil.resolveInEclipse(filePath);
        if (file.isPresent() && file.get() instanceof IFile f) {
            monitorMessage("Reading eclipse file " + filePath);
            return IoUtils.readFile(f);
        }
        // fallback to raw filesystem for absolute paths
        var fsPath = Path.of(filePath);
        if (Files.exists(fsPath) && Files.isRegularFile(fsPath)) {
            monitorMessage("Full path found but not in eclipse, reading " + filePath);
            return FileUtils.readString(fsPath);
        }
        onProblem("Cannot read unknown file " + filePath);
        return "File not found: " + filePath + " - try searchWorkspaceFiles or listWorkspaceDirectory first.";
    }

    @Tool("Eclipse: Search workspace files by name. Skips derived (target/, bin/).")
    public String searchWorkspaceFiles(
            @P("file name query") String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be empty");
        }

        String lowerQuery = query.toLowerCase();
        List<String> matches = new ArrayList<>();

        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isOpen()) continue;
            try {
                project.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (resource.isDerived()) return false;
                        if (resource.getType() == IResource.FILE
                                && resource.getName().toLowerCase().contains(lowerQuery)) {
                            var file = JdtUtil.pathOf(resource);
                            if (matches.isEmpty() || isNotDerived(file)) matches.add(file);
                        }
                        return true;
                    }
                });
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
        }

        monitorMessage("Search workspace for " + query + " returned " + matches.size() + " results.");
        if (matches.isEmpty()) {
            return "No files found matching '" + query + "'. "
                    + "The file may not exist yet (needs to be created), or try a shorter/different query. "
                    + "Use findJavaType for Java classes or listWorkspaceDirectory to explore the project structure.";
        }
        return String.join("\n", matches);
    }

    @Tool("Eclipse: List workspace directory (non-recursive). Empty path lists all projects.")
    public String listWorkspaceDirectory(
            @P("workspace-relative path, e.g. '/MyProject/src'") String path) {

        // root: list open projects
        if (path == null || path.isBlank() || path.length() == 1) {
            var t = new EclipseBuildTool();
            t.withMonitor(monitor);
            return t.listAllOpenEclipseProjects();
        }

        var resource = EclipseUtil.resolveInEclipse(path);
        if (resource.isEmpty()) {
            onProblem("Cannot list unknown directory " + path);
            return "Directory not found: " + path;
        }

        var res = resource.get();
        if (!(res instanceof IContainer container)) {
            onProblem("Cannot list a file " + path);
            return path + " is a file, not a directory. Use readWorkspaceFile to read it.";
        }

        try {
            var entries = new ArrayList<String>();
            for (IResource member : container.members()) {
                if (member.isDerived()) continue;
                var pathToAdd = JdtUtil.pathOf(member);
                if (entries.isEmpty() && isNotDerived(pathToAdd)) {
                    String prefix = (member.getType() == IResource.FILE) ? "[FILE] " : "[DIR]  ";
                    entries.add(prefix + pathToAdd);
                }
            }
            monitorMessage("List directories for " + path + " found " + entries.size() + " elements.");
            if (entries.isEmpty()) return "Directory is empty: " + path;
            return String.join("\n", entries);
        } catch (CoreException e) {
            throw new RuntimeException("Failed to list " + path, e);
        }
    }
}

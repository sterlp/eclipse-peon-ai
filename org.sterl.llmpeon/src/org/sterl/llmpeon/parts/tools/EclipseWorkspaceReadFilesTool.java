
package org.sterl.llmpeon.parts.tools;

import java.nio.file.Files;
import java.util.ArrayList;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.tool.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseWorkspaceReadFilesTool extends AbstractTool {

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

    @Tool("Reads a file from the Eclipse workspace. "
            + "Accepts workspace-relative paths (e.g. '/MyProject/src/Foo.java') "
            + "or project-relative paths (e.g. 'src/Foo.java'). "
            + "Use searchWorkspaceFiles first to find the correct file path."
            + "Use the disk tools as fallback.")
    public String readWorkspaceFile(
            @P("Workspace-relative or project-relative path or disk absolute path as fallback") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        var file = EclipseUtil.resolveInEclipse(filePath);
        if (file.isPresent() && file.get() instanceof IFile f) {
            return done(IoUtils.readFile(f));
        }
        // fallback to raw filesystem for absolute paths
        var fsPath = java.nio.file.Path.of(filePath);
        if (Files.exists(fsPath) && Files.isRegularFile(fsPath)) {
            return done(FileUtils.readString(fsPath));
        }
        return done("File not found: " + filePath);
    }

    @Tool("Searches for files whose name contains the given query string in the Eclipse workspace. "
            + "Searches all open projects, skips derived resources (target/, bin/)."
            + "Use this method first, before searching directly witg the disk tools.")
    public String searchWorkspaceFiles(
            @P("Part of the file name to search for, e.g. 'Controller' or '.xml'") String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be empty");
        }

        String lowerQuery = query.toLowerCase();
        var matches = new ArrayList<String>();

        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isOpen()) continue;
            try {
                project.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (resource.isDerived()) return false;
                        if (resource.getType() == IResource.FILE
                                && resource.getName().toLowerCase().contains(lowerQuery)) {
                            matches.add(resource.getFullPath().toPortableString());
                        }
                        return true;
                    }
                });
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
        }

        if (matches.isEmpty()) {
            return done("No files found matching '" + query + "' adjust your query or use the disk tool or list listWorkspaceDirectory");
        }
        return done("Found " + matches.size() + " for: " + query, "Found file(s):\n" + String.join("\n", matches));
    }

    @Tool("Lists files and folders directly in eclipse workspace directory (non-recursive). "
            + "Use this to navigate and explore the workspace and project structure in eclipse. "
            + "If the path is empty or root, lists all open Eclipse projects. "
            + "Returns entries prefixed with [DIR] or [FILE].")
    public String listWorkspaceDirectory(
            @P("Workspace-relative path, e.g. '/MyProject/src'. Empty or '/' lists projects.") String path) {

        // root: list open projects
        if (path == null || path.isBlank() || path.length() == 1) {
            var t = new EclipseBuildTool();
            t.withMonitor(monitor);
            return t.listAllOpenEclipseProjects();
        }

        var resource = EclipseUtil.resolveInEclipse(path);
        if (resource.isEmpty()) {
            return done("Directory not found: " + path);
        }

        var res = resource.get();
        if (!(res instanceof IContainer container)) {
            return done(path + " is a file, not a directory. Use readWorkspaceFile to read it.");
        }

        try {
            var entries = new ArrayList<String>();
            for (IResource member : container.members()) {
                if (member.isDerived()) continue;
                String prefix = (member.getType() == IResource.FILE) ? "[FILE] " : "[DIR]  ";
                entries.add(prefix + member.getFullPath().toPortableString());
            }
            if (entries.isEmpty()) return done("Directory is empty: " + path);
            return done("List " + res.getFullPath() + " " + entries.size(), 
                    "Contents of " + res.getFullPath().toPortableString() + ":\n" + String.join("\n", entries));
        } catch (CoreException e) {
            throw new RuntimeException("Failed to list " + path, e);
        }
    }
}

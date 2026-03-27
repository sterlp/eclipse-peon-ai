
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
import org.sterl.llmpeon.shared.StringMatcher;

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
            onTool("Reading eclipse file " + filePath);
            return IoUtils.readFile(f);
        }
        // fallback: try workspace root and current project
        Path wsRoot = ResourcesPlugin.getWorkspace().getRoot().getRawLocation().toFile().toPath();
        for (Path base : resolveBases(wsRoot)) {
            Path fsPath = FileUtils.resolve(base, filePath);
            if (fsPath != null && Files.isRegularFile(fsPath)) {
                onTool("Resolved outside Eclipse, reading " + filePath);
                return FileUtils.readString(fsPath);
            }
        }
        onProblem("Cannot read unknown file " + filePath);
        return "File not found: " + filePath + " - try searchWorkspaceFiles or listWorkspaceDirectory first.";
    }

    @Tool("Eclipse: Search workspace files by name")
    public String searchWorkspaceFiles(
            @P("file name query - wildcard *, ? is supported.") String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be empty");
        }

        final var matcher = StringMatcher.wildCardMatcher(query);
        final List<String> matches = new ArrayList<>();

        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isOpen()) continue;
            try {
                project.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (resource.isDerived()) return false;
                        if (resource.getType() == IResource.FILE) {
                            var file = JdtUtil.pathOf(resource);
                            var match = matcher.match(file)
                                    || matcher.match(resource.getName());

                            if (match && (matches.isEmpty() || isNotDerived(file))) matches.add(file);
                        }
                        return true;
                    }
                });
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
        }

        onTool("Search workspace for " + query + " returned " + matches.size() + " results.");
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
                String pathToAdd = JdtUtil.pathOf(member);
                if (isNotDerived(pathToAdd)) {
                    String prefix = (member.getType() == IResource.FILE) ? "[FILE] " : "[DIR]  ";
                    entries.add(prefix + pathToAdd);
                }
            }
            onTool("List directories for " + path + " found " + entries.size() + " elements.");
            if (entries.isEmpty()) return "Directory is empty: " + path;
            return String.join("\n", entries);
        } catch (CoreException e) {
            throw new RuntimeException("Failed to list " + path, e);
        }
    }

    /** Returns candidate base paths: workspace root first, then current project (if set). */
    private List<Path> resolveBases(Path wsRoot) {
        var bases = new ArrayList<Path>();
        bases.add(wsRoot);
        if (currentProject != null && currentProject.isOpen()) {
            var loc = currentProject.getRawLocation();
            if (loc != null) bases.add(loc.toFile().toPath());
        }
        return bases;
    }
}

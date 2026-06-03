
package org.sterl.llmpeon.parts.tools;

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
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.shared.StringMatcher;
import org.sterl.llmpeon.tool.AiReponseBuilder;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseWorkspaceReadFileTool extends AbstractEclipseTool {

    @Override
    public boolean isEditTool() {
        return false;
    }

    public static final String READ_ECLIPSE_FILE_TOOL = "readWorkspaceFile";
    @Tool(name = READ_ECLIPSE_FILE_TOOL, value = "Eclipse: Read workspace file (e.g. '/Project/src/Foo.java').")
    public String readWorkspaceFile(
            @P(description = "workspace-relative path", name = "filePath") String filePath,
            @P(description = "first line, 1-based; 0 = file start", required = false, name = "startLine") Integer startLine,
            @P(description = "last line, 1-based; 0 = file end", required = false, name = "endLine") Integer endLine) {
        
        ArgsUtil.requireNonBlank(filePath, "filePath");
        
        if (startLine == null) startLine = 0;
        if (endLine == null) endLine = 0;

        var file = EclipseUtil.resolveInEclipse(filePath);
        if (file.isPresent() && file.get() instanceof IFile f) {
            var lines = "";
            if (startLine > 0 && endLine > 0) lines = " from " + startLine + " to " + endLine;
            onTool("Reading eclipse" + lines + " file " + filePath);
            String content;
            try {
                content = f.readString();
            } catch (CoreException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            return  FileLines.extract(content, startLine, endLine);
        }
        onProblem("No eclipse file found for " + filePath);
        return "No eclipse file found for '" + filePath + "' use searchWorkspaceFiles to find the correct file name and path.";
    }

    @Tool("Eclipse: Search workspace files by name. Use '*' to list all files recursively.")
    public String searchWorkspaceFiles(
            @P(description = "file name query - only *, ? wildcard is supported.", name = "query") 
            String query,
            @P(description = "max results to return. 0 = unlimited.", required = false, name = "limit") 
            Integer inLimit) {

        ArgsUtil.requireNonBlank(query, "query");
        final int limit = inLimit == null ? 0 : inLimit;

        query = FileUtils.normalizePath(query);
        final var matcher = StringMatcher.wildCardMatcher(query);
        final List<String> matches = new ArrayList<>();

        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isOpen()) continue;
            try {
                project.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (limit > 0 && matches.size() >= limit) return false;
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
                if (limit > 0 && matches.size() >= limit) break;
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
        }

        onTool("Search workspace for " + query + " returned " + matches.size() + " results.");
        String suffix = null;
        if (matches.isEmpty()) {
            suffix =  "Use findJavaType for Java classes or " + LIST_WORKSPACE_NAME + " to explore the project structure.";
        }
        return AiReponseBuilder.searchComplete(matches, suffix);
    }

    public static final String LIST_WORKSPACE_NAME = "listWorkspace";
    @Tool(name = LIST_WORKSPACE_NAME, value = "Eclipse: List workspace directory/projects (non-recursive). Empty path lists all projects.")
    public String listWorkspace(
            @P(description = "workspace-relative path, e.g. '/MyProject/src'", required = false, name = "path") 
            String path) {

        // root: list open projects
        if (path == null || path.isBlank() || path.length() == 1) {
            var t = new EclipseBuildTool();
            t.withMonitor(monitor);
            return t.listAllOpenEclipseProjects();
        }

        var resource = EclipseUtil.resolveInEclipse(path);
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("Directory not found: " + path);
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
                    String prefix = (member.getType() == IResource.FILE) ? "[FILE] " : "[DIR] ";
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
}

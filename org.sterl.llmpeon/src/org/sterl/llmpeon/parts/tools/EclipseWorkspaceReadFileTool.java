
package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.PlatformUI;
import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.shared.StringMatcher;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.AiReponseBuilder;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseWorkspaceReadFileTool extends AbstractEclipseTool {

    @Override
    public boolean isEditTool() {
        return false;
    }

    @Tool("Open a workspace file, not directory, in the Eclipse editor to show it to the user e.g. a plan or summary.")
    public String eclipseOpenFileInEditor(@P(description = "workspace-relative path", name = "filePath") String filePath) {
        ArgsUtil.requireNonBlank(filePath, "filePath");
        var resource = EclipseUtil.resolveInEclipse(filePath);
        if (resource.isEmpty()) {
            onProblem("No eclipse file found for " + filePath);
            return "Cannot open: no file found at '" + filePath + "'. Use searchWorkspaceFiles to find the correct path.";
        }
        var r = resource.get();
        if (!(r instanceof IFile file)) {
            onProblem("Path is not a file: " + filePath);
            return "Cannot open editor: '" + filePath + "' is a directory, not a file.";
        }
        try {
            PlatformUI.getWorkbench().getDisplay().asyncExec(() -> EclipseUtil.openInEditor(file));
            onTool("Opened file in editor: " + filePath);
            return "Opened: " + filePath;
        } catch (Exception e) {
            throw new RuntimeException("Could not open editor for " + filePath, e);
        }
    }

    public static final String READ_ECLIPSE_FILE_TOOL = "eclipseReadFile";
    @Tool(name = READ_ECLIPSE_FILE_TOOL, value = "Read workspace file (e.g. '/Project/src/Foo.java').")
    public String eclipseReadFile(
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

    @Tool("Find any files workspace-wide by name (*, ? wildcard supported). Default file-path finder.")
    public String eclipseSearchFiles(
            @P(description = "file name query - only *, ? wildcard is supported.", name = "query")
            String query,
            @P(name = "projectName", required = false) 
            String projectName,
            @P(description = "max results to return. 0 = unlimited. Default 50.", required = false, name = "limit") 
            Integer inLimit) {

        ArgsUtil.requireNonBlank(query, "query");
        final int limit = inLimit == null ? 50 : inLimit;

        query = FileUtils.normalizePath(query);
        final var matcher = StringMatcher.wildCardMatcher(query);
        final List<String> matches = new LinkedList<>();
        
        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isPresent() && project.get().isOpen()) {
            matches.addAll(searchProjectFor(project.get(), matcher, limit));
        } else {
            for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
                if (!p.isOpen()) continue;
                matches.addAll(searchProjectFor(p, matcher, limit - matches.size()));
                if (limit > 0 && matches.size() >= limit) break;
            }
        }

        onTool("Search workspace " + StringUtil.trimToEmpty(projectName) + " for " + query + " returned " + matches.size() + " results.");
        String suffix = null;
        if (matches.isEmpty()) {
            suffix =  "Use findJavaType for Java classes or " + LIST_WORKSPACE_NAME + " to explore the project structure. Try a wildcard e.g. *folder*FileName*.java or grepWorkspaceFiles for content search.";
        }
        return AiReponseBuilder.searchComplete(matches, suffix);
    }

    private @NonNull List<String> searchProjectFor(IProject project, final StringMatcher matcher, final int limit) {
        var results = new LinkedList<String>();
        try {
            project.accept(new IResourceVisitor() {
                @Override
                public boolean visit(IResource resource) {
                    if (limit > 0 && results.size() >= limit) return false;
                    if (resource.isDerived()) return false;
                    if (resource.getType() == IResource.FILE) {
                        var file = JdtUtil.pathOf(resource);
                        var match = matcher.match(file)
                                || matcher.match(resource.getName());

                        if (match && (results.isEmpty() || isNotDerived(file))) results.add(file);
                    }
                    return true;
                }
            });
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    public static final String LIST_WORKSPACE_NAME = "eclipseList";
    @Tool(name = LIST_WORKSPACE_NAME, value = "List workspace directory/projects (non-recursive). Empty path lists all projects.")
    public String eclipseList(
            @P(description = "workspace-relative path, e.g. '/MyProject/src'", required = false, name = "path") 
            String path) {

        // root: list open projects
        if (path == null || path.isBlank() || path.length() == 1) {
            var t = new EclipseBuildTool();
            t.withToolRequest(request);
            return t.eclipseListAllOpenProjects();
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

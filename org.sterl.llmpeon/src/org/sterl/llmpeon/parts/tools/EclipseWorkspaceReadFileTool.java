
package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
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

    private static final ILog LOG = Platform.getLog(EclipseWorkspaceReadFileTool.class);

    private IProject currentProject;

    public void setCurrentProject(IProject currentProject) {
        this.currentProject = currentProject;
    }

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
    
    @Tool("Reads the content of current open eclipse workspace file by the user - may be different to the file as it can contain unsaved user edits.")
    public String eclipseReadOpenFile() {
        
        final CompletableFuture<String> result = EclipseUtil.runInUiThread(() -> {
            var e = EclipseUtil.getOpenEditor();
            onTool("Reading open editor");
            if (e.isEmpty()) return "Nothing currently open.";
            var text = EclipseUtil.getTextEditor(e.get());
            IDocumentProvider provider = text.getDocumentProvider();
            IDocument document = provider.getDocument(text.getEditorInput());
            return document.get();
        });
        try {
            return result.get(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new IllegalStateException("Timeout during eclipse editor read", e);
        }

    }

    public static final String READ_ECLIPSE_FILE_TOOL = "eclipseReadFile";
    @Tool(name = READ_ECLIPSE_FILE_TOOL, value = "Read a file from the Eclipse workspace (not disk). startLine/endLine for partial reads.")
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
            @P(description = "max results to return. Default 100, max 1000.", required = false, name = "limit") 
            Integer inLimit) {

        ArgsUtil.requireNonBlank(query, "query");
        if (inLimit == null) inLimit = 100;
        if (inLimit == 0) inLimit = 1000;
        final int limit = Math.max(1, Math.min(inLimit, 1000));

        query = FileUtils.normalizePath(query);
        final var matcher = StringMatcher.wildCardMatcher(query);
        final Map<String, String> matches = new LinkedHashMap<>();

        var project = EclipseUtil.findOpenProject(projectName);
        var scope = project.map(List::of)
                .orElseGet(() -> EclipseUtil.openProjectsPreferring(currentProject));
        var refreshTargets = new ArrayList<IProject>();
        project.ifPresentOrElse(refreshTargets::add, () -> {
            if (currentProject != null && currentProject.isOpen()) refreshTargets.add(currentProject);
        });
        searchScope(scope, matcher, limit, matches);
        if (matches.isEmpty() && !refreshTargets.isEmpty()) {
            refreshScope(refreshTargets);
            searchScope(scope, matcher, limit, matches);
        }

        onTool("Search workspace " + StringUtil.trimToEmpty(projectName) + " for " + query 
                + " returned " + matches.size() + " results.");
        String suffix = null;
        if (matches.isEmpty()) {
            var searchedScope = project.map(IProject::getName)
                    .orElse("all open projects (" + scope.size() + ")");
            suffix = "Searched: " + searchedScope + " · pattern: " + matcher.getPattern() + "\n"
                    + "Use findJavaType for Java classes or " + LIST_WORKSPACE_NAME + " to explore the project structure. Try a wildcard e.g. *folder*FileName*.java or grepWorkspaceFiles for content search.";
        }
        return AiReponseBuilder.searchComplete(new ArrayList<>(matches.values()), suffix);
    }

    private void searchScope(List<IProject> scope, StringMatcher matcher, int limit,
            Map<String, String> results) {
        for (IProject project : scope) {
            searchProjectFor(project, matcher, limit, results);
            if (results.size() >= limit) break;
        }
    }

    protected void refreshScope(List<IProject> scope) {
        try {
            for (IProject project : scope) {
                project.refreshLocal(IResource.DEPTH_INFINITE, getProgressMonitor());
            }
        } catch (CoreException e) {
            LOG.warn("Failed to refresh search scope", e);
        }
    }

    private void searchProjectFor(IProject project, StringMatcher matcher, int limit,
            Map<String, String> results) {
        try {
            project.accept(new IResourceVisitor() {
                @Override
                public boolean visit(IResource resource) {
                    if (results.size() >= limit) return false;
                    if (resource.isDerived()) return false;
                    if (resource.getType() == IResource.FILE) {
                        var file = JdtUtil.pathOf(resource);
                        var match = matcher.match(file)
                                || matcher.match(resource.getName());

                        if (match && isNotDerived(file)) {
                            var diskPath = JdtUtil.diskPathOf(resource);
                            results.putIfAbsent(diskPath == null ? file : diskPath, file);
                        }
                    }
                    return true;
                }
            });
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
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

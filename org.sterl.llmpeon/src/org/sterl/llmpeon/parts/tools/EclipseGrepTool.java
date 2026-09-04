package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.SearchQuery;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.shared.TextFileTypes;
import org.sterl.llmpeon.tool.AiReponseBuilder;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseGrepTool extends AbstractEclipseTool {

    private static final ILog LOG = Platform.getLog(EclipseGrepTool.class);

    private IProject currentProject;

    public void setCurrentProject(IProject currentProject) {
        this.currentProject = currentProject;
    }

    @Tool("Search Eclipse workspace files for text. Scope to project path and file extension.")
    public String eclipseGrepFiles(
            @P(description = "text to match with contains in content of any file", name = "query") String query,
            @P(description = "project or folder path to search in", required = false, name = "path") String path,
            @P(description = "file extension, e.g. .java", required = false, name = "extension") String extension) {

        ArgsUtil.requireNonBlank(query, "query");

        var searchQuery = SearchQuery.of(query);
        var allProjects = path == null || path.length() <= 1;
        var matches = new LinkedHashMap<String, Integer>(); // file path -> count

        // Determine containers to search
        var containers = new ArrayList<IContainer>();
        var refreshTargets = new ArrayList<IContainer>();
        if (allProjects) {
            containers.addAll(EclipseUtil.openProjectsPreferring(currentProject));
            if (currentProject != null && currentProject.isOpen()) {
                refreshTargets.add(currentProject);
            }
        } else {
            var resource = EclipseUtil.resolveInEclipse(path);
            if (resource.isEmpty()) {
                throw new IllegalArgumentException("Path not found: " + path + " check your query or leave the path empty.");
            }
            if (resource.get() instanceof IContainer c) {
                containers.add(c);
                refreshTargets.add(c);
            } else if (resource.get() instanceof IFile f) {
                int count = countOccurrences(f, searchQuery);
                if (count > 0) matches.put(JdtUtil.pathOf(resource.get()), count);
            } else {
                onProblem("Eclipse grep could not read " + JdtUtil.pathOf(resource.get()));
                return "Couldn't read " + JdtUtil.pathOf(resource.get());
            }
        }

        searchScope(containers, extension, searchQuery, matches);
        if (matches.isEmpty() && !refreshTargets.isEmpty()) {
            refreshScope(refreshTargets);
            searchScope(containers, extension, searchQuery, matches);
        }

        onTool("Eclipse grep '" + query + "' type '" + StringUtil.getOrDefault(extension, "*")
                + "' found " + matches.size() + " matches");

        String result = AiReponseBuilder.grepComplete(
                matches, searchQuery, AiReponseBuilder.MAX_GREP_FILES, extension);
        if (matches.isEmpty()) {
            var searchedScope = allProjects ? "all open projects (" + containers.size() + ")" : path;
            result += System.lineSeparator() + "Searched: " + searchedScope + " · pattern: " + query;
        }
        return result;
    }

    private void searchScope(List<IContainer> scope, String extension, SearchQuery searchQuery,
            LinkedHashMap<String, Integer> matches) {
        for (IContainer container : scope) {
            try {
                container.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (matches.size() >= AiReponseBuilder.MAX_GREP_FILES) return false;
                        if (resource.isDerived()) return false;
                        if (!isNotDerived(JdtUtil.pathOf(resource))) return false;

                        if (resource.getType() == IResource.FILE && resource instanceof IFile file) {
                            if (StringUtil.hasValue(extension)
                                    ? file.getName().toLowerCase().endsWith(extension.trim().toLowerCase())
                                    : TextFileTypes.isTextFile(file.getName())) {
                                int count = countOccurrences(file, searchQuery);
                                if (count > 0) matches.put(JdtUtil.pathOf(file), count);
                            }
                        }
                        return true;
                    }
                });
            } catch (CoreException e) {
                // skip container on error
            }
            if (matches.size() >= AiReponseBuilder.MAX_GREP_FILES) break;
        }
    }

    private int countOccurrences(IFile file, SearchQuery query) {
        try {
            String content = file.readString();
            return query.count(content);
        } catch (CoreException e) { return 0; }
    }

    protected void refreshScope(List<IContainer> scope) {
        try {
            for (IContainer container : scope) {
                container.refreshLocal(IResource.DEPTH_INFINITE, getProgressMonitor());
            }
        } catch (CoreException e) {
            LOG.warn("Failed to refresh grep scope", e);
        }
    }
}

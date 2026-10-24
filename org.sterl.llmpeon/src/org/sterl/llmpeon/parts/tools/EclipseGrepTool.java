package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.sterl.llmpeon.shared.GrepHit;
import org.sterl.llmpeon.shared.SearchQuery;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.shared.TextFileTypes;
import org.sterl.llmpeon.tool.AiReponseBuilder;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseGrepTool extends AbstractEclipseTool {

    private static final ILog LOG = Platform.getLog(EclipseGrepTool.class);

    private volatile IProject currentProject;

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
        // The extension filter is a property of the container walk: an explicitly named file is
        // never filtered, so the disclosure must not claim a filter that did not happen (C-EXT, R-OD-7)
        String effectiveExtension = extension;
        var hits = new ArrayList<GrepHit>(); // matched lines, in file-processing order
        // R-WN-1/R-WN-2: one entry per Disk file (key = diskDedupKey) — drives the file cap
        var diskEntries = new LinkedHashMap<String, EclipseUtil.ProjectPathEntry>();

        // Determine containers to search
        var containers = new ArrayList<IContainer>();
        var refreshTargets = new ArrayList<IContainer>();
        if (allProjects) {
            containers.addAll(EclipseUtil.openProjectsPreferring(currentProject));
            if (currentProject != null && currentProject.isOpen()) {
                refreshTargets.add(currentProject);
            }
        } else {
            var resource = EclipseUtil.resolveInEclipse(path, Optional.ofNullable(currentProject));
            if (resource.isEmpty()) {
                throw new IllegalArgumentException("Path not found: " + path + EclipseUtil.unresolvedHint(path)
                        + " Check your query or leave the path empty.");
            }
            if (resource.get() instanceof IContainer c) {
                containers.add(c);
                refreshTargets.add(c);
            } else if (resource.get() instanceof IFile f) {
                effectiveExtension = null;
                addHits(f, searchQuery, hits, diskEntries);
            } else {
                onProblem("Eclipse grep could not read " + JdtUtil.pathOf(resource.get()));
                return "Couldn't read " + JdtUtil.pathOf(resource.get());
            }
        }

        searchScope(containers, extension, searchQuery, hits, diskEntries);
        if (hits.isEmpty() && !refreshTargets.isEmpty()) {
            refreshScope(refreshTargets);
            searchScope(containers, extension, searchQuery, hits, diskEntries);
        }

        String result = AiReponseBuilder.grepComplete(
                hits, searchQuery, AiReponseBuilder.MAX_GREP_FILES, AiReponseBuilder.MAX_GREP_LINES, effectiveExtension);
        if (hits.isEmpty()) {
            var searchedScope = allProjects ? "all open projects (" + containers.size() + ")" : path;
            result += System.lineSeparator() + "Searched: " + searchedScope + " · pattern: " + query;
        }
        onTool("🔍 grep '" + query + "' type '" + StringUtil.getOrDefault(effectiveExtension, "*")
                + "' found " + hits.size() + " matched lines" + " " + StringUtil.charsSuffix(result));
        return result;
    }

    private void searchScope(List<IContainer> scope, String extension, SearchQuery searchQuery,
            List<GrepHit> hits, Map<String, EclipseUtil.ProjectPathEntry> diskEntries) {
        for (IContainer container : scope) {
            try {
                container.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (diskEntries.size() >= AiReponseBuilder.MAX_GREP_FILES) return false;
                        if (resource.isDerived()) return false;
                        if (!isNotDerived(JdtUtil.pathOf(resource))) return false;

                        if (resource.getType() == IResource.FILE && resource instanceof IFile file) {
                            if (StringUtil.hasValue(extension)
                                    ? file.getName().toLowerCase().endsWith(extension.trim().toLowerCase())
                                    : TextFileTypes.isTextFile(file.getName())) {
                                addHits(file, searchQuery, hits, diskEntries);
                            }
                        }
                        return true;
                    }
                });
            } catch (CoreException e) {
                // skip container on error
            }
            if (diskEntries.size() >= AiReponseBuilder.MAX_GREP_FILES) break;
        }
    }

    private void addHits(IFile file, SearchQuery query, List<GrepHit> hits,
            Map<String, EclipseUtil.ProjectPathEntry> diskEntries) {
        try {
            var lineHits = query.matchingLines(file.readString());
            if (lineHits.isEmpty()) return;
            // R-WN-1/R-WN-2: one Disk file = one entry and its lines are recorded once —
            // the deepest project view wins (plan D2/D3)
            var key = EclipseUtil.diskDedupKey(file);
            var candidate = new EclipseUtil.ProjectPathEntry(JdtUtil.pathOf(file), file.getProject());
            var existing = diskEntries.get(key);
            var winner = EclipseUtil.mostSpecificEntry(existing, candidate);
            diskEntries.put(key, winner);
            if (existing == null) {
                lineHits.forEach(lh -> hits.add(new GrepHit(winner.path(), lh.line(), lh.text())));
            } else if (winner == candidate) {
                // a deeper view was found after a shallower one was delivered: rewrite the
                // already recorded hits of this Disk file in place — no new hits (same lines)
                for (int i = 0; i < hits.size(); i++) {
                    if (hits.get(i).file().equals(existing.path())) {
                        hits.set(i, new GrepHit(winner.path(), hits.get(i).line(), hits.get(i).text()));
                    }
                }
            }
        } catch (CoreException e) {
            // skip unreadable file
        }
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

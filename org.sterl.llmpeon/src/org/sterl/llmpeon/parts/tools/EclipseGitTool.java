package org.sterl.llmpeon.parts.tools;

import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.shared.ArgsUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseGitTool {

    
    public static Optional<Repository> findGitRepositoryFor(IProject project) {
        if (project == null) return Optional.empty();

        var mapping = RepositoryMapping.getMapping((IResource) project);
        if (mapping == null) {
            return Optional.empty();
        }

        var repo = mapping.getRepository();
        return Optional.ofNullable(repo);
    }
    private Optional<Repository> findRepositoryForProject(IProject project) {
        if (project == null) return Optional.empty();

        RepositoryMapping mapping = RepositoryMapping.getMapping((IResource) project);
        if (mapping == null) {
            return Optional.empty();
        }

        Repository repo = mapping.getRepository();
        return Optional.ofNullable(repo);
    }
    
    @Tool("Eclipse: List recent Git commits for the current project")
    public String listGitHistory(
            @P("projectName") String projectName,
            @P(description = "Max number of commits to list", required = false, name = "maxCommits")
            Integer maxCommits) {

        ArgsUtil.requireNonBlank(projectName, "projectName");
        if (maxCommits == null) maxCommits = 10;
        var project = EclipseUtil.findOpenProject(projectName);
        
        if (project.isEmpty()) return projectName + " not found. Known: " + EclipseUtil.openProjectsNames();

        var repoOpt = findRepositoryForProject(project.get());
        if (repoOpt.isEmpty()) {
            return "No Git repository mapped for project: " + project.get().getName();
        }

        int limit = maxCommits != null && maxCommits > 0 ? maxCommits : 20;

        try (Repository repo = repoOpt.get();
             Git git = new Git(repo)) {

            Iterable<RevCommit> commits = git.log().setMaxCount(limit).call();

            StringBuilder sb = new StringBuilder();
            for (RevCommit c : commits) {
                sb.append(c.getId().abbreviate(8).name())
                  .append(" | ")
                  .append(c.getAuthorIdent().getWhen())
                  .append(" | ")
                  .append(c.getShortMessage())
                  .append("\n");
            }
            return sb.length() == 0 ? "No commits found." : sb.toString().trim();
        } catch (Exception e) {
            return "Error while reading Git history: " + e.getMessage();
        }
    }
}

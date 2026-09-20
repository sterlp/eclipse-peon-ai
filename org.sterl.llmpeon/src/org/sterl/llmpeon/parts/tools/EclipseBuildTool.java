package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.shared.ArgsUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseBuildTool extends AbstractEclipseTool {

    @Tool("List open workspace projects with their eclipse paths, disk paths, and natures.")
    public String eclipseListAllOpenProjects() {
        var sb = new StringBuilder();
        var projects = EclipseUtil.openProjects();
        if (projects.isEmpty()) {
            sb.append("No eclipse projects are open - ask the developer to create one.");
        } else {
            sb.append("Known open eclipse projects are:\n");
            for (IProject p : projects) {
                sb.append(EclipseUtil.projectInfo(p))
                  .append("\n---");
            }
        }
        onTool("List workspace with " + projects.size() + " open projects");
        return sb.toString();
    }
    
    @Tool("List compile errors and warnings for a project. Optional: files (comma-separated) and severity (ERROR, WARNING) filter the result scope.")
    public String eclipseReadProjectProblems(@P(name = "projectName") String projectName,
            @P(name = "files", description = "Comma-separated file paths (project-relative or workspace) to limit the problem list; empty = whole project.", required = false) String files,
            @P(name = "severity", description = "Limit to one severity: ERROR or WARNING; empty = errors and warnings.", required = false) String severity) {
        ArgsUtil.requireNonBlank(projectName, "projectName");
        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            onProblem("Cannot read problems of unknown project " + projectName);
            return projectName + " not found.\n" + eclipseListAllOpenProjects();
        }
        var projectRef = project.get();
        return readProblems(projectRef, files, severity);
    }

    private String readProblems(IProject projectRef) {
        return readProblems(projectRef, null, null);
    }

    private String readProblems(IProject projectRef, String files, String severity) {
        int severityFilter = parseSeverity(severity);
        var paths = splitPaths(files);
        if (paths.isEmpty()) {
            return readProjectWide(projectRef);
        }
        return readFiltered(projectRef, paths, severityFilter);
    }

    /**
     * Default mode (no file filter): project-wide problems — unchanged behaviour.
     */
    private String readProjectWide(IProject projectRef) {
        try {
            var status = new Status();
            readProjectStatus(projectRef, status);
            onTool("Reading problems of " + projectRef.getName() + ": " + status.countProblems());
            if (status.hasProblems()) {
                return "Project " + projectRef.getName() + " problems:\n" + status.toString();
            } else {
                return "Project build " + projectRef.getName() + " has no errors or warning.";
            }
        } catch (CoreException e) {
            throw new RuntimeException("Failed to build " + projectRef.getName(), e);
        }
    }

    /**
     * Filtered mode: markers of the given files only (DEPTH_ZERO), optionally one severity.
     * Every scope restriction is named in the output; unresolved paths are reported honestly
     * and never fall back to the project-wide list.
     */
    private String readFiltered(IProject project, List<String> paths, int severityFilter) {
        var notFound = new ArrayList<String>();
        var resolvedPaths = new ArrayList<String>();
        var resolvedFiles = new ArrayList<IFile>();
        for (String path : paths) {
            var file = resolveFile(project, path);
            if (file == null) {
                notFound.add(path);
            } else {
                resolvedPaths.add(path);
                resolvedFiles.add(file);
            }
        }

        var out = new StringBuilder();
        for (String path : notFound) {
            out.append("No problems found for ").append(path)
               .append(" in project ").append(project.getName())
               .append(" (project-relative path expected)")
               .append(System.lineSeparator());
        }
        if (!notFound.isEmpty()) {
            onProblem("Problems filter: " + notFound.size() + " of " + paths.size()
                    + " path(s) not found in " + project.getName());
        }
        if (resolvedFiles.isEmpty()) {
            return out.toString();
        }

        try {
            var status = new Status();
            status.severityFilter = severityFilter;
            for (IFile file : resolvedFiles) {
                readFileStatus(file, status);
            }
            onTool("Reading filtered problems of " + project.getName() + ": " + status.countProblems());

            String filesLabel = String.join(", ", resolvedPaths);
            String severityLabel = severityFilter == 0 ? "" : ", severity " + severityName(severityFilter);
            if (status.hasProblems()) {
                out.append("Problems in ").append(filesLabel)
                   .append(" (project ").append(project.getName()).append(severityLabel).append("):")
                   .append(System.lineSeparator())
                   .append(status.toString());
            } else {
                out.append("No problems in ").append(filesLabel)
                   .append(" (scope: project ").append(project.getName()).append(severityLabel).append(")");
            }
            return out.toString();
        } catch (CoreException e) {
            throw new RuntimeException("Failed to read problems of " + project.getName(), e);
        }
    }

    /**
     * Resolves a project-relative or workspace-absolute path to an IFile of the given project.
     *
     * @return the file, or {@code null} when the path does not resolve to a file inside the project
     */
    private static IFile resolveFile(IProject project, String path) {
        IResource resource = path.startsWith("/")
                ? ResourcesPlugin.getWorkspace().getRoot().findMember(IPath.fromOSString(path))
                : project.findMember(IPath.fromOSString(path));
        return resource instanceof IFile file && file.getProject().equals(project) ? file : null;
    }

    private static List<String> splitPaths(String files) {
        var result = new ArrayList<String>();
        if (files == null) {
            return result;
        }
        for (String raw : files.split(",")) {
            String path = raw.trim();
            // blank entries are discarded: whitespace-only input = unset = whole project
            if (!path.isEmpty()) {
                result.add(path);
            }
        }
        return result;
    }

    private static int parseSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return 0;
        }
        switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "ERROR":
                return IMarker.SEVERITY_ERROR;
            case "WARNING":
                return IMarker.SEVERITY_WARNING;
            default:
                throw new IllegalArgumentException(
                        "Invalid severity '" + severity + "'. Allowed values: ERROR, WARNING.");
        }
    }

    private static String severityName(int severityFilter) {
        return severityFilter == IMarker.SEVERITY_ERROR ? "ERROR" : "WARNING";
    }

    @Tool("Refresh and clean build the project. Returns errors/warnings. Preferred way to verify code changes or full refresh.")
    public String eclipseBuildProject(@P(name ="projectName") String projectName) {
        ArgsUtil.requireNonBlank(projectName, "projectName");

        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            onProblem("Cannot build unknown project " + projectName);
            return projectName + " not found. " + eclipseListAllOpenProjects();
        }
        IProject projectRef = project.get();
        try {
            onTool("Building " + projectName);
            projectRef.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
            projectRef.refreshLocal(IResource.DEPTH_INFINITE, getProgressMonitor());
            // CLEAN
            projectRef.build(IncrementalProjectBuilder.CLEAN_BUILD, getProgressMonitor());
            // BUILD
            projectRef.build(IncrementalProjectBuilder.FULL_BUILD, getProgressMonitor());

            return readProblems(projectRef);
        } catch (CoreException e) {
            throw new RuntimeException("Filed to build " + projectRef.getName(), e);
        }
    }
    
    @Tool("Refresh/sync a project with the disk status - if changes have been made outside eclipse e.g. with disk tools.")
    public String eclipseRefreshProject(@P(name ="projectName") String projectName) {
        ArgsUtil.requireNonBlank(projectName, "projectName");

        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            onProblem("Cannot build unknown project " + projectName);
            return projectName + " not found. " + eclipseListAllOpenProjects();
        }
        IProject projectRef = project.get();
        try {
            onTool("Refresh " + projectName);
            projectRef.refreshLocal(IResource.DEPTH_INFINITE, getProgressMonitor());
            return "Success";
        } catch (CoreException e) {
            throw new RuntimeException("Filed to build " + projectRef.getName(), e);
        }
    }

    static class Status {
        List<IMarker> errors = new ArrayList<>();
        List<IMarker> warnings = new ArrayList<>();
        int severityFilter = 0; // 0 = no filter (ERROR + WARNING), else only this severity

        void addMarker(IMarker marker) {
            int severity = marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
            if (severityFilter != 0 && severity != severityFilter) {
                return;
            }
            switch (severity) {
            case IMarker.SEVERITY_ERROR:
                errors.add(marker);
                break;
            case IMarker.SEVERITY_WARNING:
                warnings.add(marker);
                break;
            }
        }

        boolean hasProblems() {
            return !errors.isEmpty() || !warnings.isEmpty();
        }

        public int countProblems() {
            return errors.size() + warnings.size();
        }

        @Override
        public String toString() {
            var result = new StringBuilder();
            for (IMarker m : errors) {
                result.append(markerToAiString(m)).append("\n");
            }
            for (IMarker m : warnings) {
                result.append(markerToAiString(m)).append("\n");
            }
            return result.toString();
        }
    }

    private void readProjectStatus(IProject project, Status status) throws CoreException {
        IMarker[] markers = project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
        for (IMarker marker : markers) {
            status.addMarker(marker);
        }
    }

    private void readFileStatus(IFile file, Status status) throws CoreException {
        IMarker[] markers = file.findMarkers(IMarker.PROBLEM, false, IResource.DEPTH_ZERO);
        for (IMarker marker : markers) {
            status.addMarker(marker);
        }
    }

    private static String markerToAiString(IMarker marker) {
        String message = marker.getAttribute(IMarker.MESSAGE, "");
        var file = marker.getResource().getFullPath().toPortableString();
        int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);

        return message + " @ line " + line + " @ file " + file;
    }

}

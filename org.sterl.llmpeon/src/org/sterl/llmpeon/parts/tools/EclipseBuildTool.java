package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.shared.ArgsUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseBuildTool extends AbstractEclipseTool {

    @Tool("List open workspace projects with their eclipse paths, disk paths, and natures.")
    public String listAllOpenEclipseProjects() {
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
    
    @Tool("Eclipse: List build errors/warnings of a project.")
    public String readProjectProblems(@P(name = "projectName") String projectName) {
        ArgsUtil.requireNonBlank(projectName, "projectName");
        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            onProblem("Cannot read problems of unknown project " + projectName);
            return projectName + " not found.\n" + listAllOpenEclipseProjects();
        }
        var projectRef = project.get();
        return readProblems(projectRef);
    }

    private String readProblems(IProject projectRef) {
        try {
            var status = readProjectStatus(projectRef);
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

    @Tool("Eclipse: Build project and return errors/warnings. Preferred way to verify code changes.")
    public String buildEclipseProject(@P(name ="projectName") String projectName) {
        ArgsUtil.requireNonBlank(projectName, "projectName");

        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            onProblem("Cannot build unknown project " + projectName);
            return projectName + " not found. " + listAllOpenEclipseProjects();
        }
        IProject projectRef = project.get();
        try {
            onTool("Building " + projectName);
            projectRef.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
            projectRef.refreshLocal(IResource.DEPTH_INFINITE, getProgressMonitor());
            projectRef.build(IncrementalProjectBuilder.CLEAN_BUILD, getProgressMonitor());
            projectRef.build(IncrementalProjectBuilder.FULL_BUILD, getProgressMonitor());

            return readProblems(projectRef);
        } catch (CoreException e) {
            throw new RuntimeException("Filed to build " + projectRef.getName(), e);
        }
    }

    static class Status {
        List<IMarker> errors = new ArrayList<>();
        List<IMarker> warnings = new ArrayList<>();

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

    private Status readProjectStatus(IProject project) throws CoreException {
        var result = new Status();
        IMarker[] markers = project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);

        for (IMarker marker : markers) {
            int severity = marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
            switch (severity) {
            case IMarker.SEVERITY_ERROR:
                result.errors.add(marker);
                break;
            case IMarker.SEVERITY_WARNING:
                result.warnings.add(marker);
                break;
            }
        }

        return result;
    }

    private static String markerToAiString(IMarker marker) {
        String message = marker.getAttribute(IMarker.MESSAGE, "");
        var file = marker.getResource().getFullPath().toPortableString();
        int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);

        return message + " @ line " + line + " @ file " + file;
    }

}

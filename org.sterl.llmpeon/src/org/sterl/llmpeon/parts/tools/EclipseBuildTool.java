package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseBuildTool extends AbstractEclipseTool {

    @Tool("""
          Lists all open eclipse projects in the workspace - use this to find open developer projects.
          The result 'Eclipse path' can be used together with listWorkspaceDirectory to list the file in an eclipse project.
          """)
    public String listAllOpenEclipseProjects() {
        var sb = new StringBuilder();
        var projects = EclipseUtil.openProjects();
        if (projects.isEmpty()) {
            sb.append("No eclipse projects are open - ask the developer to create one.");
        } else {
            sb.append("Known open eclipse projects are:\n");
            for (IProject p : projects) {
                sb.append("Project name: ").append(p.getName())
                  .append("\nEclipse path: ").append(JdtUtil.pathOf(p))
                  .append("\nDisk path: ").append(p.getRawLocation().toPortableString())
                  .append("\nNatures: ").append(projectNatures(p))
                  .append("\n---");
            }
        }
        monitorMessage("List workspace with " + projects.size() + " open projects");
        return sb.toString();
    }
    
    @Tool("Reads all eclipse build problems of an eclipse code project - use this to check if where any problems or warning in the project.")
    public String readProjectProblems(@P("The project name or path") String projectName) {
        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            onProblem("Cannot read problems of unknown project " + projectName);
            return projectName + " not found. " + listAllOpenEclipseProjects();
        }
        var projectRef = project.get();
        return readProblems(projectRef);
    }

    private String readProblems(IProject projectRef) {
        try {
            var status = readProjectStatus(projectRef);
            monitorMessage("Reading problems of " + projectRef.getName() + ": " + status.countProblems());
            if (status.hasProblems()) {
                return "Project " + projectRef.getName() + " problems:\n" + status.toString();
            } else {
                return "Project build " + projectRef.getName() + " has no errors or warning.";
            }
        } catch (CoreException e) {
            throw new RuntimeException("Failed to build " + projectRef.getName(), e);
        }
    }

    @Tool("""
            Builds an eclipse project in the worksspace and returns all build errors and warnings.
            Run this tool to verify code modifications.
            This is the prefered way to build projects in eclipse.
            """)
    public String buildEclipseProject(@P("The project name or path to build") String projectName) {
        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            onProblem("Cannot build unknown project " + projectName);
            return projectName + " not found. " + listAllOpenEclipseProjects();
        }
        IProject projectRef = project.get();
        try {
            monitorMessage("Building " + projectName);
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

    private static String projectNatures(IProject project) {
        try {
            String[] ids = project.getDescription().getNatureIds();
            if (ids.length == 0) return "none";
            var sb = new StringBuilder();
            for (String id : ids) {
                if (!sb.isEmpty()) sb.append(", ");
                // show short name for well-known natures, full id otherwise
                sb.append(switch (id) {
                    case "org.eclipse.jdt.core.javanature" -> "java";
                    case "org.eclipse.pde.PluginNature" -> "pde-plugin";
                    case "org.eclipse.m2e.core.maven2Nature" -> "maven";
                    case "org.eclipse.buildship.core.gradleprojectnature" -> "gradle";
                    default -> id;
                });
            }
            return sb.toString();
        } catch (CoreException e) {
            return "unknown";
        }
    }

    private static String markerToAiString(IMarker marker) {
        String message = marker.getAttribute(IMarker.MESSAGE, "");
        var file = marker.getResource().getFullPath().toPortableString();
        int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);

        return message + " @ line " + line + " @ file " + file;
    }

}

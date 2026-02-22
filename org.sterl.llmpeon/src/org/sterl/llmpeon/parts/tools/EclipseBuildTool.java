package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.tool.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseBuildTool extends AbstractTool {

    @Tool("""
            Lists all open eclipse projects in the workspace
            """)
    public String listAllOpenEclipseProjects() {
        var result = "Known open projects are:\n ";
        var projects = EclipseUtil.openProjects();
        monitorMessage("Reading eclipse projects " + projects.size());
        if (projects.isEmpty()) result += "No projects are open";
        else {
            for (IProject p : projects) {
                result += "\nEclipse path: " + p.getFullPath().toPortableString()
                        + "\nDisk path: " + p.getRawLocation();
            }
        }
        return result;
    }
    
    @Tool("Reads all eclipse projects problem")
    public String readProjectProblems(@P("The project name or path") String projectName) {
        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            if (monitor != null) monitor.onProblem(projectName + " not found.");
            return projectName + " not found. " + listAllOpenEclipseProjects();
        }
        
        monitorMessage("Reading problems of " + projectName);
        var projectRef = project.get();
        try {
            var status = readProjectStatus(projectRef);
            if (status.hasProblems()) {
                return "Project build " + projectRef.getName() + " results:\n" + status.toString();
            } else {
                return "Project " + projectRef.getName() + " has no errors or warning.";
            }
        } catch (CoreException e) {
            throw new RuntimeException("Filed to build " + projectRef.getName(), e);
        }

    }

    @Tool("""
            Refresh cleans and builds an eclipse project in the worksspace and returns errors and warnings.
            Run this tool to verify code modifications - after a clean project build.
            This is the prefered way to build projects in eclipse.
            """)
    public String buildEclipseProject(@P("The project name or path to build") String projectName) {
        var project = EclipseUtil.findOpenProject(projectName);
        if (project.isEmpty()) {
            if (monitor != null) monitor.onProblem("Build: " + projectName + " not found.");
            return projectName + " not found. " + listAllOpenEclipseProjects();
        }
        
        monitorMessage("Building " + projectName);
        IProject projectRef = project.get();
        try {
            // clear
            projectRef.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
            projectRef.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
            // build
            projectRef.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor());
            projectRef.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());

            var status = readProjectStatus(projectRef);

            if (status.hasProblems()) {
                return "Project build " + projectRef.getName() + " results:\n" + status.toString();
            } else {
                return "Project " + projectRef.getName() + " build with no errors or warning.";
            }
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

        @Override
        public String toString() {
            var result = new StringBuilder();
            for (IMarker m : errors) {
                result.append(markerToAiString(m)).append("\n");
            }
            for (IMarker m : errors) {
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

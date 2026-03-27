package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseGrepTool extends AbstractEclipseTool {

    @Tool("Eclipse: Search workspace file contents.")
    public String grepWorkspaceFiles(
            @P("text to search for") String query,
            @P("optional project or folder path") String path,
            @P("optional file extension, e.g. .java") String extension) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be empty");
        }
        var allProjects = path == null || path.length() <= 1;
        String lowerQuery = query.toLowerCase();
        var matches = new LinkedHashMap<String, Integer>(); // file path -> count
        final int MAX_FILES = 100;

        // Determine containers to search
        var containers = new ArrayList<IContainer>();
        if (allProjects) {
            containers.addAll(EclipseUtil.openProjects());
        } else {
            var resource = EclipseUtil.resolveInEclipse(path);
            if (resource.isEmpty()) {
                throw new IllegalArgumentException("Path not found: " + path + " check your query or leave the path empty.");
            }
            if (resource.get() instanceof IContainer c) {
                containers.add(c);
            } else if (resource.get() instanceof IFile f) {
                int count = countOccurrences(f, lowerQuery);
                if (count > 0) matches.put(JdtUtil.pathOf(resource.get()), count);
            } else {
                onProblem("Eclipse grep could not read " + JdtUtil.pathOf(resource.get()));
                return "Couldn't read " + JdtUtil.pathOf(resource.get());
            }
        }

        for (IContainer container : containers) {
            try {
                container.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (matches.size() >= MAX_FILES) return false;
                        if (resource.isDerived()) return false;
                        if (!isNotDerived(JdtUtil.pathOf(resource))) return false;

                        if (resource.getType() == IResource.FILE && resource instanceof IFile file) {
                            if (isTextFile(file, extension)) {
                                int count = countOccurrences(file, lowerQuery);
                                if (count > 0) matches.put(JdtUtil.pathOf(file), count);
                            }
                        }
                        return true;
                    }
                });
            } catch (CoreException e) {
                // skip container on error
            }
            if (matches.size() >= MAX_FILES) break;
        }

        onTool("Grep " + query + " found " + matches.size() + " matches.");

        if (matches.isEmpty()) {
            return "No files contain '" + query + "' in the searched path.";
        }
        var sb = new StringBuilder();
        sb.append("Found \"").append(query).append("\" in ").append(matches.size()).append(" file(s):\n");
        matches.forEach((filePath, count) -> sb.append(filePath).append(": ").append(count).append(" occurrence(s)\n"));

        if (matches.size() >= MAX_FILES) {
            sb.append("... result capped at ").append(MAX_FILES).append(" files. Narrow your search path.");
        }
        return sb.toString();
    }

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "xml", "json", "yaml", "yml", "properties", "txt", "md",
            "html", "css", "js", "ts", "jsx", "tsx", "sql", "sh", "bat",
            "gradle", "kt", "groovy", "scala", "py", "rb", "php", "c", "h",
            "cpp", "hpp", "rs", "go", "swift", "cfg", "ini", "toml", "csv",
            "mf", "prefs", "product", "target", "project", "classpath", "bnd");

    private static boolean isTextFile(IFile file, String extension) {
        var name = file.getName().toLowerCase();
        if (StringUtil.hasValue(extension)) return name.endsWith(extension.trim().toLowerCase());

        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return TEXT_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private int countOccurrences(IFile file, String lowerQuery) {
        String content = IoUtils.readFile(file).toLowerCase();
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(lowerQuery, idx)) != -1) {
            count++;
            idx += lowerQuery.length();
        }
        return count;
    }
}

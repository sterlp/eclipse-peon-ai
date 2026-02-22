package org.sterl.llmpeon.parts.tools;

import java.io.InputStream;
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
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseGrepTool extends AbstractTool {

    @Tool("Searches the content of all files in the Eclipse workspace for a given string. "
            + "Returns matching files with occurrence count. "
            + "If a path is '.' or '/' or empty, searches all open projects. "
            + "Otherwise searches only within the given directory/project path. "
            + "Skips derived resources (target/, bin/) and binary files.")
    public String grepWorkspaceFiles(
            @P("Mandatory text string to search for in file contents") String query,
            @P("Optional directory path to search in. Use '.' or '/' to search all open projects.") String path,
            @P("Optional file extension or suffix to search in e.g. .java, .css, .tsx") String extension) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be empty");
        }
        var allProjects = path == null || path.length() <= 1;
        monitorMessage("Grep for '" + query + "' in " + (allProjects ? "all projects" : path) + (StringUtil.hasValue(extension) ? " type " + extension : ""));
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
                return "Path not found: " + path;
            }
            if (resource.get() instanceof IContainer c) {
                containers.add(c);
            } else if (resource.get() instanceof IFile f) {
                // single file grep
                int count = countOccurrences(f, lowerQuery);
                if (count > 0) {
                    return "Found \"" + query + "\" in 1 file:\n"
                            + f.getFullPath().toPortableString() + ": " + count + " occurrence(s)";
                }
                return "No occurrences of \"" + query + "\" in " + f.getFullPath().toPortableString();
            } else {
                return path + " found but it can not be read.";
            }
        }

        for (IContainer container : containers) {
            try {
                container.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (matches.size() >= MAX_FILES) return false;
                        if (resource.isDerived()) return false;
                        // skip common output directories that may not be marked derived
                        if (resource instanceof IContainer && SKIP_DIRS.contains(resource.getName())) return false;
                        if (resource.getType() == IResource.FILE && resource instanceof IFile file) {
                            if (isTextFile(file, extension)) {
                                int count = countOccurrences(file, lowerQuery);
                                if (count > 0) {
                                    matches.put(file.getFullPath().toPortableString(), count);
                                }
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

        if (matches.isEmpty()) {
            return "No files contain \"" + query + "\" in the searched path.";
        }

        var sb = new StringBuilder();
        sb.append("Found \"").append(query).append("\" in ").append(matches.size()).append(" file(s):\n");
        matches.forEach((filePath, count) ->
            sb.append(filePath).append(": ").append(count).append(" occurrence(s)\n"));
        if (matches.size() >= MAX_FILES) {
            sb.append("... result capped at ").append(MAX_FILES).append(" files. Narrow your search path.");
        }
        monitorMessage("Grep found " + matches.size() + " files");
        return sb.toString().trim();
    }

    private static final Set<String> SKIP_DIRS = Set.of(
            "bin", "target", "node_modules", ".git", ".settings", ".metadata");

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
        try (InputStream is = file.getContents()) {
            String content = IoUtils.toString(is, file.getCharset()).toLowerCase();
            int count = 0;
            int idx = 0;
            while ((idx = content.indexOf(lowerQuery, idx)) != -1) {
                count++;
                idx += lowerQuery.length();
            }
            return count;
        } catch (Exception e) {
            return 0; // skip binary or unreadable files
        }
    }
}

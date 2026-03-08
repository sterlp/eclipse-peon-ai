package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseCodeNavigationTool extends AbstractEclipseTool {

    private static final int MAX_TYPE_RESULTS = 25;
    private static final int MAX_REFERENCE_RESULTS = 50;
    private static final int MAX_IMPL_RESULTS = 50;

    // -------------------------------------------------------------------------
    // Tool 1: Find a type by name — returns metadata only, no source
    // -------------------------------------------------------------------------

    @Tool("""
            Java only: Finds a Java type (class, interface, enum, record) by name in the Eclipse workspace.
            Returns location, kind, Javadoc, superclass and interfaces — no source code.
            Accepts simple names ('Foo'), wildcard patterns ('Foo*'), or fully qualified names ('com.example.Foo').
            Use getTypeSource afterwards to read the full source of a found type.
            Try this first before falling back to a file search.
            """)
    public String findJavaType(
            @P("Type name to find. Simple, wildcard (*), or fully qualified.") String typeName,
            @P("Optional project name to limit search. Empty searches all projects.") String projectName) {

        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }

        try {
            boolean isFqn = typeName.contains(".") && !typeName.contains("*");
            List<IType> found = isFqn ? findByFqn(typeName, projectName) : findBySearch(typeName, projectName);

            if (found.isEmpty()) {
                return done("No type found matching '" + typeName + "'. "
                        + "Try a wildcard pattern like '*" + typeName + "*' or check the spelling.");
            }

            var sb = new StringBuilder();
            sb.append("Found ").append(found.size()).append(" type(s):\n\n");
            for (int i = 0; i < Math.min(found.size(), MAX_TYPE_RESULTS); i++) {
                appendTypeSummary(sb, found.get(i));
                if (i < found.size() - 1) sb.append("\n---\n");
            }
            if (found.size() > MAX_TYPE_RESULTS) {
                sb.append("\n... ").append(found.size() - MAX_TYPE_RESULTS)
                  .append(" more results. Narrow your search.");
            }

            return done("Searching " + typeName + " found " + found.size(), sb.toString());
        } catch (JavaModelException e) {
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Tool 2: Read source — given a FQN, returns the full source
    // -------------------------------------------------------------------------

    @Tool("""
            Java only: Returns the full source code of a Java type by its fully qualified name.
            For binary types (JDK / library JARs without source), returns public field and method signatures.
            Use findJavaType first to discover the correct fully qualified name.
            """)
    public String getTypeSource(
            @P("Fully qualified type name, e.g. 'com.example.MyClass'") String fqn,
            @P("Optional project name to limit search. Empty searches all projects.") String projectName) {

        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("fqn must not be empty");
        }

        try {
            List<IType> found = findByFqn(fqn, projectName);
            if (found.isEmpty()) {
                return done("Type not found: " + fqn + ". Use findJavaType to get the correct fully qualified name.");
            }

            IType type = bestType(found);
            var sb = new StringBuilder();
            sb.append("File: ").append(JdtUtil.pathOf(type)).append("\n");

            String source = type.getSource();
            if (StringUtil.hasValue(source)) {
                sb.append(source);
            } else {
                sb.append("Source code not available.\n");
                String javadoc = JdtUtil.javadoc(type);
                if (!javadoc.isEmpty()) sb.append("Javadoc:\n").append(javadoc).append("\n");
                appendTypeSummary(sb, type);
                appendBinarySignatures(sb, type);
            }

            return done("Type search for " + fqn + " found: " 
                    + type.getFullyQualifiedName() + (StringUtil.hasValue(source) ? " returned source " : " returned binary info"), 
                    sb.toString());
        } catch (JavaModelException e) {
            throw new RuntimeException("Read source failed: " + e.getMessage(), e);
        }
    }
    
    private IType bestType(List<IType> found) throws JavaModelException {
        IType result = found.get(0);
        for (IType t : found) {
            if (StringUtil.hasValue(t.getSource())) result = t;
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Tool 3: Find references
    // -------------------------------------------------------------------------

    @Tool("Java only: Finds all references/usages of a Java type or method across the workspace. "
            + "Returns file paths with line numbers where the element is referenced. "
            + "Use findJavaType first to get the fully qualified type name.")
    public String findReferences(
            @P("Fully qualified type name, e.g. 'com.example.MyClass'") 
            String typeName,
            @P("Optional method name to find references to that specific method. Empty finds references to the type itself.") 
            String methodName,
            @P("Optional project name to limit scope. Empty searches all projects.") 
            String projectName) {

        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }

        try {
            var typeOpt = JdtUtil.findType(typeName);
            if (typeOpt.isEmpty()) {
                return done("Type not found: " + typeName + ". Use findJavaType to find the correct name.");
            }
            IType type = typeOpt.get();

            IJavaElement target;
            if (methodName != null && !methodName.isBlank()) {
                IMethod method = findMethodByName(type, methodName);
                if (method == null) {
                    return done("Method '" + methodName + "' not found on " + typeName
                            + ". Available methods: " + listMethodNames(type));
                }
                target = method;
            } else {
                target = type;
            }

            SearchPattern pattern = SearchPattern.createPattern(target, IJavaSearchConstants.REFERENCES);
            IJavaSearchScope scope = resolveScope(projectName);

            var matches = new ArrayList<SearchMatch>();
            new SearchEngine().search(
                    pattern,
                    new SearchParticipant[]{ SearchEngine.getDefaultSearchParticipant() },
                    scope,
                    new SearchRequestor() {
                        @Override
                        public void acceptSearchMatch(SearchMatch match) {
                            if (matches.size() < MAX_REFERENCE_RESULTS) matches.add(match);
                        }
                    },
                    new NullProgressMonitor());

            if (matches.isEmpty()) {
                return done("No references found for: " + target.getElementName() + ".");
            }

            var byFile = new LinkedHashMap<String, List<String>>();
            for (SearchMatch match : matches) {
                String filePath = match.getResource() != null
                        ? match.getResource().getFullPath().toPortableString() : "unknown";
                String line = "?";
                if (match.getElement() instanceof IJavaElement je) {
                    try {
                        var cu = je.getAncestor(IJavaElement.COMPILATION_UNIT);
                        if (cu instanceof org.eclipse.jdt.core.ICompilationUnit icu) {
                            String src = icu.getSource();
                            // TODO this looks wrong, it returnes not the code but an int
                            if (src != null) line = String.valueOf(JdtUtil.offsetToLine(src, match.getOffset()));
                        }
                    } catch (JavaModelException ignored) {}
                }
                byFile.computeIfAbsent(filePath, k -> new ArrayList<>()).add(line);
            }

            var sb = new StringBuilder();
            sb.append("Found ").append(matches.size()).append(" reference(s):\n\n");
            for (var entry : byFile.entrySet()) {
                sb.append(entry.getKey()).append(" @ lines ")
                  .append(String.join(", ", entry.getValue())).append("\n");
            }
            if (matches.size() >= MAX_REFERENCE_RESULTS) {
                sb.append("\n... capped at ").append(MAX_REFERENCE_RESULTS)
                  .append(" results. Narrow with a project name.");
            }

            return done("Find reference " + typeName + "." + methodName + " returned " + byFile.size(), sb.toString());
        } catch (Exception e) {
            throw new RuntimeException("Reference search failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Tool 4: Find workspace resources by name pattern
    // -------------------------------------------------------------------------

    @Tool("Finds files and folders in the Eclipse workspace by name or glob pattern. "
            + "Supports wildcards: '*' matches any sequence of characters, '?' matches one character. "
            + "Example: '*.xml', 'application*.properties', 'pom.xml'. "
            + "Use this for non-Java resources like XML, JSON, YAML, properties files.")
    public String findResource(
            @P("File name or glob pattern, e.g. '*.xml' or 'application.properties'") String namePattern,
            @P("Optional project name to limit search. Empty searches all projects.") String projectName) {

        if (namePattern == null || namePattern.isBlank()) {
            throw new IllegalArgumentException("namePattern must not be empty");
        }

        // Normalize to always be a glob
        String glob = namePattern.contains("*") || namePattern.contains("?")
                ? namePattern : "*" + namePattern + "*";

        var matches = new ArrayList<String>();
        try {
            var projects = projectName != null && !projectName.isBlank()
                    ? EclipseUtil.findOpenProject(projectName).map(List::of).orElse(EclipseUtil.openProjects())
                    : EclipseUtil.openProjects();

            for (var project : projects) {
                project.accept(resource -> {
                    if (resource.getType() == IResource.FILE
                            && matches(resource.getName(), glob)
                            && matches.size() < MAX_REFERENCE_RESULTS) {
                        matches.add(resource.getFullPath().toPortableString());
                    }
                    return true; // recurse into children
                });
            }
        } catch (CoreException e) {
            throw new RuntimeException("Resource search failed: " + e.getMessage(), e);
        }

        if (matches.isEmpty()) {
            return done("No resources found matching '" + namePattern + "'.");
        }

        var sb = new StringBuilder();
        sb.append("Found ").append(matches.size()).append(" resource(s):\n\n");
        matches.forEach(p -> sb.append(p).append("\n"));
        if (matches.size() >= MAX_REFERENCE_RESULTS) {
            sb.append("\n... capped at ").append(MAX_REFERENCE_RESULTS)
              .append(" results. Narrow with a project name or more specific pattern.");
        }

        return done("Find resource " + namePattern + " returned " + matches.size(), sb.toString());
    }

    // -------------------------------------------------------------------------
    // Tool 5: Read content of any workspace resource
    // -------------------------------------------------------------------------

    @Tool("Reads the full content of any file in the Eclipse workspace by its workspace-relative path. "
            + "Works for any file type: XML, JSON, YAML, properties, Java source, etc. "
            + "Use findResource or findJavaType to get the workspace path first.")
    public String getResourceContent(
            @P("Workspace-relative path, e.g. '/my.project/src/main/resources/application.properties'") String path) {

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be empty");
        }

        IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(
                org.eclipse.core.runtime.Path.fromPortableString(path));

        if (!file.exists()) {
            return done("File not found: " + path + ". Use findResource to get the correct workspace path.");
        }

        return done("Reading resource: " + path, IoUtils.readFile(file));
    }

    private static boolean matches(String name, String glob) {
        // Convert glob to regex: escape dots, replace * and ?
        String regex = glob.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return name.matches("(?i)" + regex);
    }

    // -------------------------------------------------------------------------
    // Tool 5: Find implementations
    // -------------------------------------------------------------------------

    @Tool("Java only: Finds all classes that implement a given interface or extend a given class. "
            + "Returns each implementing type with its file path and kind. "
            + "Use findJavaType first to get the fully qualified type name.")
    public String findImplementations(
            @P("Fully qualified interface or class name, e.g. 'com.example.IService'") String typeName,
            @P("Optional project name. Empty searches all projects.") String projectName) {

        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }

        try {
            var typeOpt = JdtUtil.findType(typeName);
            if (typeOpt.isEmpty()) {
                return done("Type not found: " + typeName + ". Use findJavaType to find the correct name.");
            }
            IType type = typeOpt.get();

            ITypeHierarchy hierarchy = type.newTypeHierarchy(new NullProgressMonitor());
            IType[] subtypes = hierarchy.getAllSubtypes(type);

            if (subtypes.length == 0) {
                return done("No implementations found for: " + typeName + ".");
            }

            var sb = new StringBuilder();
            sb.append("Found ").append(subtypes.length).append(" implementation(s) of ")
              .append(typeName).append(":\n\n");
            for (int i = 0; i < Math.min(subtypes.length, MAX_IMPL_RESULTS); i++) {
                IType sub = subtypes[i];
                sb.append(JdtUtil.kindOf(sub)).append(" ")
                  .append(sub.getFullyQualifiedName())
                  .append(" @ ").append(JdtUtil.pathOf(sub)).append("\n");
            }
            if (subtypes.length > MAX_IMPL_RESULTS) {
                sb.append("\n... ").append(subtypes.length - MAX_IMPL_RESULTS)
                  .append(" more. Narrow with a project name.");
            }

            return done("Search implementation of " + typeName + " found " + subtypes.length, sb.toString());
        } catch (JavaModelException e) {
            throw new RuntimeException("Hierarchy search failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<IType> findByFqn(String fqn, String projectName) {
        var results = new ArrayList<IType>();
        if (projectName != null && !projectName.isBlank()) {
            EclipseUtil.findOpenProject(projectName).ifPresent(p ->
                JdtUtil.findType(fqn, JavaCore.create(p)).ifPresent(results::add));
        } else {
            JdtUtil.findType(fqn).ifPresent(results::add);
        }
        return results;
    }

    private List<IType> findBySearch(String typeName, String projectName) throws JavaModelException {
        var results = new ArrayList<IType>();
        int matchRule = (typeName.contains("*") || typeName.contains("?"))
                ? SearchPattern.R_PATTERN_MATCH
                : SearchPattern.R_PREFIX_MATCH | SearchPattern.R_CASE_SENSITIVE;

        new SearchEngine().searchAllTypeNames(
                null, SearchPattern.R_EXACT_MATCH,
                typeName.toCharArray(), matchRule,
                IJavaSearchConstants.TYPE,
                resolveScope(projectName),
                new TypeNameMatchRequestor() {
                    @Override
                    public void acceptTypeNameMatch(TypeNameMatch match) {
                        if (results.size() < MAX_TYPE_RESULTS + 10) results.add(match.getType());
                    }
                },
                IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
                new NullProgressMonitor());

        return results;
    }

    private static void appendTypeSummary(StringBuilder sb, IType type) throws JavaModelException {
        sb.append(JdtUtil.kindOf(type)).append(" ").append(type.getFullyQualifiedName()).append("\n");
        String superclass = type.getSuperclassName();
        if (superclass != null) sb.append("Extends: ").append(superclass).append("\n");

        String[] ifaces = type.getSuperInterfaceNames();
        if (ifaces != null && ifaces.length > 0) {
            sb.append("Implements: ").append(String.join(", ", ifaces)).append("\n");
        }
    }

    private static void appendBinarySignatures(StringBuilder sb, IType type) throws JavaModelException {
        sb.append("Public signatures:\n");
        for (IField f : type.getFields()) {
            if (!Flags.isPrivate(f.getFlags())) {
                sb.append("  ").append(Signature.toString(f.getTypeSignature()))
                  .append(" ").append(f.getElementName()).append("\n");
            }
        }
        for (IMethod m : type.getMethods()) {
            if (!Flags.isPrivate(m.getFlags())) {
                sb.append("  ").append(formatMethodSignature(m)).append("\n");
            }
        }
    }

    private static String formatMethodSignature(IMethod method) throws JavaModelException {
        var sb = new StringBuilder();
        sb.append(Signature.toString(method.getReturnType())).append(" ");
        sb.append(method.getElementName()).append("(");
        String[] paramTypes = method.getParameterTypes();
        String[] paramNames = method.getParameterNames();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(Signature.toString(paramTypes[i]));
            if (paramNames != null && i < paramNames.length) sb.append(" ").append(paramNames[i]);
        }
        sb.append(")");
        return sb.toString();
    }

    private static IMethod findMethodByName(IType type, String name) throws JavaModelException {
        for (IMethod m : type.getMethods()) {
            if (m.getElementName().equals(name)) return m;
        }
        return null;
    }

    private static String listMethodNames(IType type) {
        try {
            var names = new ArrayList<String>();
            for (IMethod m : type.getMethods()) names.add(m.getElementName());
            return names.isEmpty() ? "(none)" : String.join(", ", names);
        } catch (JavaModelException e) {
            return "(unable to list)";
        }
    }

    private static IJavaSearchScope resolveScope(String projectName) {
        if (projectName != null && !projectName.isBlank()) {
            var project = EclipseUtil.findOpenProject(projectName);
            if (project.isPresent()) {
                return SearchEngine.createJavaSearchScope(new IJavaElement[]{ JavaCore.create(project.get()) });
            }
        }
        return SearchEngine.createWorkspaceScope();
    }
}

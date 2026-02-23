package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
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
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.tool.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseCodeNavigationTool extends AbstractTool {

    private static final int MAX_SOURCE_LINES = 200;
    private static final int MAX_TYPE_RESULTS = 10;
    private static final int MAX_REFERENCE_RESULTS = 50;
    private static final int MAX_IMPL_RESULTS = 30;

    @Tool("Finds a Java type (class, interface, enum, record) by name in the Eclipse workspace. "
            + "Returns its location, kind, Javadoc, superclass, interfaces, and source code. "
            + "Accepts simple names ('Foo'), wildcard patterns ('Foo*'), camelCase ('NPE'), "
            + "or fully qualified names ('com.example.Foo'). "
            + "Use this to go to a class definition and read its code."
            + "This is the prefered way to search code classes in an eclipse workspace try this first before falling back to a name or even file search.")
    public String findJavaType(
            @P("Type name to find. Simple, wildcard (*), camelCase, or fully qualified.") String typeName,
            @P("Optional project name to limit search. Empty searches all projects.") String projectName,
            @P("Optional max lines of source code to include. Default is "
                    + MAX_SOURCE_LINES + " lines.") String maxSourceLines) {

        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }

        int sourceLimit = MAX_SOURCE_LINES;
        if (maxSourceLines != null && !maxSourceLines.isBlank()) {
            try {
                sourceLimit = Integer.parseInt(maxSourceLines.trim());
            } catch (NumberFormatException e) {
                // ignore, use default
            }
        }

        monitorMessage("Eclipse finding type " + typeName);

        try {
            boolean isFqn = typeName.contains(".") && !typeName.contains("*");
            List<IType> found;

            if (isFqn) {
                found = findByFqn(typeName, projectName);
            } else {
                found = findBySearch(typeName, projectName);
            }

            if (found.isEmpty()) {
                return "No type found matching '" + typeName + "'. "
                        + "Try a wildcard pattern like '*" + typeName + "*' or check the spelling.";
            }

            var sb = new StringBuilder();
            sb.append("Found ").append(found.size()).append(" type(s):\n\n");

            for (int i = 0; i < Math.min(found.size(), MAX_TYPE_RESULTS); i++) {
                IType type = found.get(i);
                appendTypeInfo(sb, type, i == 0 ? sourceLimit : 0);
                if (i < found.size() - 1) sb.append("\n---\n");
            }
            if (found.size() > MAX_TYPE_RESULTS) {
                sb.append("\n... ").append(found.size() - MAX_TYPE_RESULTS)
                  .append(" more results. Narrow your search.");
            }

            monitorMessage("Found " + found.size() + " type(s)");
            return sb.toString();
        } catch (JavaModelException e) {
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    @Tool("Finds all references/usages of a Java type or method across the workspace. "
            + "Returns file paths with line numbers where the element is referenced. "
            + "Use findJavaType first to get the fully qualified type name.")
    public String findReferences(
            @P("Fully qualified type name, e.g. 'com.example.MyClass'") String typeName,
            @P("Optional method name to find references to that specific method. "
                    + "Empty finds references to the type itself.") String methodName,
            @P("Optional project name to limit scope. Empty searches all projects.") String projectName) {

        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }

        monitorMessage("Finding references to " + typeName
                + (methodName != null && !methodName.isBlank() ? "." + methodName : ""));

        try {
            var typeOpt = JdtUtil.findType(typeName);
            if (typeOpt.isEmpty()) {
                return "Type not found: " + typeName + ". Use findJavaType to find the correct name.";
            }
            IType type = typeOpt.get();

            // Determine element to search for
            IJavaElement target;
            if (methodName != null && !methodName.isBlank()) {
                IMethod method = findMethodByName(type, methodName);
                if (method == null) {
                    return "Method '" + methodName + "' not found on " + typeName
                            + ". Available methods: " + listMethodNames(type);
                }
                target = method;
            } else {
                target = type;
            }

            // Search
            SearchPattern pattern = SearchPattern.createPattern(
                    target, IJavaSearchConstants.REFERENCES);
            IJavaSearchScope scope = resolveScope(projectName);

            var matches = new ArrayList<SearchMatch>();
            new SearchEngine().search(
                    pattern,
                    new SearchParticipant[]{ SearchEngine.getDefaultSearchParticipant() },
                    scope,
                    new SearchRequestor() {
                        @Override
                        public void acceptSearchMatch(SearchMatch match) {
                            if (matches.size() < MAX_REFERENCE_RESULTS) {
                                matches.add(match);
                            }
                        }
                    },
                    new NullProgressMonitor());

            if (matches.isEmpty()) {
                return "No references found for " + target.getElementName() + ".";
            }

            // Group by file
            var byFile = new LinkedHashMap<String, List<String>>();
            for (SearchMatch match : matches) {
                String filePath = match.getResource() != null
                        ? match.getResource().getFullPath().toPortableString()
                        : "unknown";
                String line = "?";
                if (match.getElement() instanceof IJavaElement je) {
                    try {
                        var cu = je.getAncestor(IJavaElement.COMPILATION_UNIT);
                        if (cu instanceof org.eclipse.jdt.core.ICompilationUnit icu) {
                            String src = icu.getSource();
                            if (src != null) {
                                line = String.valueOf(JdtUtil.offsetToLine(src, match.getOffset()));
                            }
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

            monitorMessage("Found " + matches.size() + " references");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Reference search failed: " + e.getMessage(), e);
        }
    }

    @Tool("Finds all classes that implement a given interface or extend a given class. "
            + "Returns each implementing type with its file path and kind. "
            + "Use findJavaType first to get the fully qualified type name.")
    public String findImplementations(
            @P("Fully qualified interface or class name, e.g. 'com.example.IService'") String typeName,
            @P("Optional project name. Empty searches all projects.") String projectName) {

        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }

        monitorMessage("Finding implementations of " + typeName);

        try {
            var typeOpt = JdtUtil.findType(typeName);
            if (typeOpt.isEmpty()) {
                return "Type not found: " + typeName + ". Use findJavaType to find the correct name.";
            }
            IType type = typeOpt.get();

            ITypeHierarchy hierarchy = type.newTypeHierarchy(new NullProgressMonitor());
            IType[] subtypes = hierarchy.getAllSubtypes(type);

            if (subtypes.length == 0) {
                return "No implementations found for " + typeName + ".";
            }

            var sb = new StringBuilder();
            sb.append("Found ").append(subtypes.length).append(" implementation(s) of ")
              .append(typeName).append(":\n\n");

            int count = 0;
            for (IType sub : subtypes) {
                if (count >= MAX_IMPL_RESULTS) break;
                sb.append(JdtUtil.kindOf(sub)).append(" ")
                  .append(sub.getFullyQualifiedName())
                  .append(" @ ").append(JdtUtil.pathOf(sub)).append("\n");
                count++;
            }
            if (subtypes.length > MAX_IMPL_RESULTS) {
                sb.append("\n... ").append(subtypes.length - MAX_IMPL_RESULTS)
                  .append(" more. Narrow with a project name.");
            }

            monitorMessage("Found " + subtypes.length + " implementations");
            return sb.toString();
        } catch (JavaModelException e) {
            throw new RuntimeException("Hierarchy search failed: " + e.getMessage(), e);
        }
    }

    // -- helpers --

    private List<IType> findByFqn(String fqn, String projectName) throws JavaModelException {
        var results = new ArrayList<IType>();
        if (projectName != null && !projectName.isBlank()) {
            var project = EclipseUtil.findOpenProject(projectName);
            if (project.isPresent()) {
                IJavaProject jp = JavaCore.create(project.get());
                JdtUtil.findType(fqn, jp).ifPresent(results::add);
            }
        } else {
            JdtUtil.findType(fqn).ifPresent(results::add);
        }
        return results;
    }

    private List<IType> findBySearch(String typeName, String projectName) throws JavaModelException {
        var results = new ArrayList<IType>();
        IJavaSearchScope scope = resolveScope(projectName);

        // Determine match rule
        int matchRule;
        if (typeName.contains("*") || typeName.contains("?")) {
            matchRule = SearchPattern.R_PATTERN_MATCH;
        } else if (typeName.equals(typeName.toUpperCase()) || hasIntermediateUpperCase(typeName)) {
            matchRule = SearchPattern.R_CAMELCASE_MATCH;
        } else {
            matchRule = SearchPattern.R_PREFIX_MATCH | SearchPattern.R_CASE_SENSITIVE;
        }

        new SearchEngine().searchAllTypeNames(
                null, SearchPattern.R_EXACT_MATCH,
                typeName.toCharArray(), matchRule,
                IJavaSearchConstants.CLASS_AND_INTERFACE,
                scope,
                new TypeNameMatchRequestor() {
                    @Override
                    public void acceptTypeNameMatch(TypeNameMatch match) {
                        if (results.size() < MAX_TYPE_RESULTS + 10) {
                            results.add(match.getType());
                        }
                    }
                },
                IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
                new NullProgressMonitor());

        return results;
    }

    private void appendTypeInfo(StringBuilder sb, IType type, int sourceLines) throws JavaModelException {
        sb.append(JdtUtil.kindOf(type)).append(" ").append(type.getFullyQualifiedName()).append("\n");
        sb.append("Path: ").append(JdtUtil.pathOf(type)).append("\n");

        String superclass = type.getSuperclassName();
        if (superclass != null) sb.append("Extends: ").append(superclass).append("\n");

        String[] ifaces = type.getSuperInterfaceNames();
        if (ifaces != null && ifaces.length > 0) {
            sb.append("Implements: ").append(String.join(", ", ifaces)).append("\n");
        }

        String javadoc = JdtUtil.javadoc(type);
        if (!javadoc.isEmpty()) {
            sb.append("Javadoc:\n").append(javadoc).append("\n");
        }

        if (sourceLines > 0) {
            try {
                String source = type.getSource();
                if (source != null) {
                    String[] lines = source.split("\n");
                    int limit = Math.min(lines.length, sourceLines);
                    sb.append("Source (").append(lines.length).append(" lines):\n");
                    for (int i = 0; i < limit; i++) {
                        sb.append(lines[i]).append("\n");
                    }
                    if (lines.length > limit) {
                        sb.append("... truncated ").append(lines.length - limit).append(" more lines\n");
                    }
                } else {
                    // Binary type — show signatures
                    appendBinarySignatures(sb, type);
                }
            } catch (JavaModelException e) {
                appendBinarySignatures(sb, type);
            }
        }
    }

    private void appendBinarySignatures(StringBuilder sb, IType type) throws JavaModelException {
        sb.append("Source not available. Signatures:\n");
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
            if (paramNames != null && i < paramNames.length) {
                sb.append(" ").append(paramNames[i]);
            }
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
            for (IMethod m : type.getMethods()) {
                names.add(m.getElementName());
            }
            return names.isEmpty() ? "(none)" : String.join(", ", names);
        } catch (JavaModelException e) {
            return "(unable to list)";
        }
    }

    private static IJavaSearchScope resolveScope(String projectName) {
        if (projectName != null && !projectName.isBlank()) {
            var project = EclipseUtil.findOpenProject(projectName);
            if (project.isPresent()) {
                IJavaProject jp = JavaCore.create(project.get());
                return SearchEngine.createJavaSearchScope(new IJavaElement[]{ jp });
            }
        }
        return SearchEngine.createWorkspaceScope();
    }

    private static boolean hasIntermediateUpperCase(String s) {
        for (int i = 1; i < s.length(); i++) {
            if (Character.isUpperCase(s.charAt(i))) return true;
        }
        return false;
    }
}

package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
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
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.StringMatcher;
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

    @Tool("Eclipse/Java: Find types by name/wildcard. Searches also libs and JDK. Metadata only, no source.")
    public String findJavaType(
            @P("simple name, wildcard (*) or FQN") String typeName,
            @P("optional project name") String projectName) {

        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }

        try {
            boolean isFqn = typeName.contains(".") && !typeName.contains("*");
            List<IType> found = isFqn ? findByFqn(typeName, projectName) : findBySearch(typeName, projectName);

            if (found.isEmpty()) {
                onProblem("No type found matching '" + typeName + "'. ");
                return "Type '" + typeName + "' not found. Try a wildcard pattern like '*" + typeName + "*' or check the spelling.";
            }

            var sb = new StringBuilder();
            for (int i = 0; i < Math.min(found.size(), MAX_TYPE_RESULTS); i++) {
                appendTypeSummary(sb, found.get(i));
                if (i < found.size() - 1) sb.append("\n---\n");
            }
            if (found.size() > MAX_TYPE_RESULTS) {
                sb.append("\n... ").append(found.size() - MAX_TYPE_RESULTS)
                  .append(" more results. Narrow your search.");
            }
            onTool("Found " + found.size() + " matching " + typeName);
            return sb.toString();
        } catch (JavaModelException e) {
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Tool 2: Read source — given a FQN, returns the full source
    // -------------------------------------------------------------------------

    @Tool("Eclipse/Java: Get full source of a type by FQN. Covers JDK and dependency libs — prefer over decompiling JARs.")
    public String getTypeSource(
            @P("fully qualified type name (FQN)") String fqn,
            @P("optional; narrows JDK/lib search scope") String projectName) {

        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("fqn must not be empty");
        }

        try {
            List<IType> found = findByFqn(fqn, projectName);
            if (found.isEmpty()) {
                onProblem("No type " + fqn + " found" + (StringUtil.hasValue(projectName) ? " in " + projectName : ""));
                return "Type not found: " + fqn + ". Use findJavaType to get the correct fully qualified name.";
            }

            IType type = bestType(found);
            var sb = new StringBuilder();

            String source = JdtUtil.getSource(type);
            if (StringUtil.hasValue(source)) {
                sb.append(source);
            } else {
                sb.append("Source code not available.\n");
                sb.append("File: ").append(JdtUtil.pathOf(type)).append("\n");
                String javadoc = JdtUtil.javadoc(type);
                if (!javadoc.isEmpty()) sb.append("Javadoc:\n").append(javadoc).append("\n");
                appendTypeSummary(sb, type);
                appendBinarySignatures(sb, type);
            }

            onTool("Reading type " + fqn + " " + projectName + (StringUtil.hasNoValue(source) ? " source" : " binary"));
            return sb.toString();
        } catch (JavaModelException e) {
            throw new RuntimeException("Read source failed: " + e.getMessage(), e);
        }
    }
    
    private IType bestType(List<IType> found) throws JavaModelException {
        IType result = found.get(0);
        for (IType t : found) {
            if (StringUtil.hasValue(t.getSource())) result = t;
            else if (JdtUtil.getSource(result) != null) result = t;
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Tool 3: Find references
    // -------------------------------------------------------------------------

    @Tool("Eclipse/Java: Find all usages of a type or method.")
    public String findReferences(
            @P("fully qualified type name (FQN)") String typeName,
            @P("optional method name") String methodName,
            @P("optional project name") String projectName) {

        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }

        try {
            var typeOpt = JdtUtil.findType(typeName);
            if (typeOpt.isEmpty()) {
                onProblem("Cannot read references of unknown type " + typeName);
                return "Type not found: " + typeName + ". Use findJavaType to find the correct name.";
            }
            IType type = typeOpt.get();

            IJavaElement target;
            if (methodName != null && !methodName.isBlank()) {
                IMethod method = findMethodByName(type, methodName);
                if (method == null) {
                    onProblem("Unknown method " + methodName + " of type " + typeName);
                    return "Method '" + methodName + "' not found on " + typeName + ". Available methods: " + listMethodNames(type);
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
                    getProgressMonitor());

            if (matches.isEmpty()) {
                return "No references found for: " + target.getElementName() + ".";
            }

            var byFile = new LinkedHashMap<String, List<String>>();
            for (SearchMatch match : matches) {
                if (match.getElement() instanceof IJavaElement je) {
                    String filePath = je.getElementName();
                    if (match.getResource() != null) filePath = JdtUtil.pathOf(match.getResource());
                    String line = "";
                    try {
                        var src = JdtUtil.getSource(je);
                        if (src != null && match.getOffset() > 0) {
                            line = String.valueOf(JdtUtil.offsetToLine(src, match.getOffset()));
                        }
                    } catch (JavaModelException ignored) {}
                    byFile.computeIfAbsent(filePath, k -> new ArrayList<>()).add(line);
                } else {
                    System.err.println("findReferences with no java source! " + match);
                    continue;
                }
            }

            var sb = new StringBuilder();
            for (var entry : byFile.entrySet()) {
                sb.append(entry.getKey()).append(" @ lines: ")
                  .append(String.join(", ", entry.getValue())).append("\n");
            }
            if (matches.size() >= MAX_REFERENCE_RESULTS) {
                sb.append("\n... capped at ").append(MAX_REFERENCE_RESULTS)
                  .append(" results. Narrow with a project name.");
            }
            onTool("Reading references of " + target.getElementName() + " found " + matches.size() + " matches.");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Reference search failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Tool 4: Find workspace resources by name pattern
    // -------------------------------------------------------------------------

    @Tool("Eclipse/JDT: Find workspace files/folders by name or glob (*, ?).")
    public String findResource(
            @P("file name or glob pattern") String namePattern,
            @P("optional project name") String projectName) {

        if (namePattern == null || namePattern.isBlank()) {
            throw new IllegalArgumentException("namePattern must not be empty");
        }

        // Normalize to always be a glob
        String glob = namePattern.contains("*") || namePattern.contains("?")
                ? namePattern : "*" + namePattern + "*";

        var matches = new ArrayList<String>();
        var projects = projectName != null && !projectName.isBlank()
                ? EclipseUtil.findOpenProject(projectName).map(List::of).orElse(EclipseUtil.openProjects())
                        : EclipseUtil.openProjects();
        try {

            var mather = new StringMatcher(glob, true, false);

            for (var project : projects) {
                project.accept(resource -> {
                    if (resource.getType() == IResource.FILE
                            && mather.match(resource.getName())
                            && matches.size() < MAX_REFERENCE_RESULTS) {
                        matches.add(resource.getFullPath().toPortableString());
                    }
                    return true; // recurse into children
                });
            }
        } catch (CoreException e) {
            throw new RuntimeException("Resource search failed: " + e.getMessage(), e);
        }

        onTool("Search for " + glob + " in " + projects.size() + " projects.");
        
        if (matches.isEmpty()) {
            return "No resources found matching '" + namePattern + "' in following projects: " + projects.stream().map(p -> p.getName());
        }
        var sb = new StringBuilder();
        sb.append("Found ").append(matches.size()).append(" resource(s):\n\n");
        matches.forEach(p -> sb.append(p).append("\n"));
        if (matches.size() >= MAX_REFERENCE_RESULTS) {
            sb.append("\n... capped at ").append(MAX_REFERENCE_RESULTS)
              .append(" results. Narrow with a project name or more specific pattern.");
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Tool 5: Find implementations
    // -------------------------------------------------------------------------

    @Tool("Eclipse/Java: Find implementations/subclasses.")
    public String findImplementations(
            @P("fully qualified interface or class name") String typeName,
            @P("optional project name") String projectName) {

        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }

        try {
            var typeOpt = JdtUtil.findType(typeName, projectName);

            if (typeOpt.isEmpty()) {
                onProblem("Cannot find implementation of unknown type " + typeName);
                return "Type not found: " + typeName + ". Use findJavaType to find the correct name.";
            }
            IType type = typeOpt.get();

            ITypeHierarchy hierarchy = type.newTypeHierarchy(getProgressMonitor());
            IType[] subtypes = hierarchy.getAllSubtypes(type);

            
            onTool("Reading implementations " + subtypes.length + " of " + typeName);
            if (subtypes.length == 0) {
                return "No implementations found for: " + typeName + ".";
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

            return sb.toString();
        } catch (JavaModelException e) {
            throw new RuntimeException("Hierarchy search failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<IType> findByFqn(String fqn, String projectName) {
        var results = new ArrayList<IType>();
        if (StringUtil.hasValue(projectName)) {
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
                getProgressMonitor());

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
        sb.append("Project: ").append(type.getJavaProject().getProject().getName()).append("\n");
        if (type.getResource() != null) {
            sb.append("Path: ").append(JdtUtil.pathOf(type.getResource())).append("\n");
        }
        sb.append("Fully qualified type name for getTypeSource: ").append(type.getFullyQualifiedName());
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

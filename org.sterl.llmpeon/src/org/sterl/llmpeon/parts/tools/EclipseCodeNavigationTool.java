package org.sterl.llmpeon.parts.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
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
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.StringMatcher;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseCodeNavigationTool extends AbstractEclipseTool {

    private static final ILog LOG = Platform.getLog(EclipseCodeNavigationTool.class);

    private static final int MAX_TYPE_RESULTS = 25;
    private static final int MAX_REFERENCE_RESULTS = 50;

    @Tool("Eclipse/Java: Find Java types by name/wildcard. Searches in workspace, JDK and used JARs. Java type metadata only.")
    public String findJavaType(
            @P(description = "type name, only * or ? wildcard supported", name = "typeName") 
            String typeName,
            @P(description = "type package, only * or ? wildcard supported", name = "package", required = false) 
            String pkg,
            @P(name = "projectName", required = false) 
            String projectName) {

        ArgsUtil.requireNonBlank(typeName, "typeName");
        if (pkg == null || pkg.length() == 0) pkg = "*";

        try {
            boolean isFqn = pkg.contains(".") && !pkg.contains("*") && !typeName.contains("*");

            List<IType> found = isFqn 
                    ? findByFqn(pkg, typeName, projectName) 
                    : JdtUtil.findBySearch(pkg, typeName, projectName, getProgressMonitor());

            if (found.isEmpty()) {
                onProblem("No type found matching '" + toFQN(pkg, typeName) + "'. ");
                return "Type not found. Try a wildcard pattern like '*" + typeName + "*' or check the spelling.";
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
            onTool("Find type " + typeName + " reading " + found.size() + " results ...");
            return sb.toString();
        } catch (JavaModelException e) {
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    public static final String GET_TYPE_SOURCE = "readTypeSource";
    @Tool(name = GET_TYPE_SOURCE, 
          value = "Eclipse/Java: Read source or JavaDoc of the type. Covers JDK and used JARs — prefer over decompiling JARs. java.io.File etc.")
    public String readTypeSource(
            @P(description = "package name for this type e.g.: java.io", name = "package") String pkg,
            @P(description = "type name e.g.: File", name = "typeName") String typeName,
            @P(description = "project name to limit search scope (optional)", required = false, name = "projectName") String projectName) {

        ArgsUtil.requireNonBlank(pkg, "package");
        ArgsUtil.requireNonBlank(typeName, "typeName");

        try {
            List<IType> found = findByFqn(pkg, typeName, projectName);
            if (found.isEmpty()) {
                onProblem("Type " + toFQN(pkg, projectName) + " not found" + (StringUtil.hasValue(projectName) ? " in " + projectName : ""));
                return "Type not found. Check your parameters. findJavaType result for " + typeName + ":\n"
                    + findJavaType(typeName, null, projectName);
            }

            IType type = bestType(found);
            var sb = new StringBuilder();

            String source = JdtUtil.getSource(type);
            if (StringUtil.hasValue(source)) {
                boolean isFile = false;
                if (type.getResource() instanceof IFile f) {
                    isFile = true;
                    sb.append(JdtUtil.pathOf(f)).append("\n");
                }
                sb.append(isFile ? FileLines.format(source) : source);
            } else {
                sb.append("Source code not available.\n");
                sb.append("File: ").append(JdtUtil.pathOf(type)).append("\n");
                String javadoc = JdtUtil.javadoc(type);
                if (!javadoc.isEmpty()) sb.append("Javadoc:\n").append(javadoc).append("\n");
                appendTypeSummary(sb, type);
                appendBinarySignatures(sb, type);
            }

            onTool("Reading type " + typeName + " " + projectName + (StringUtil.hasValue(source) ? " source" : " binary"));
            
            return sb.toString();
        } catch (JavaModelException e) {
            throw new RuntimeException("Read source failed: " + e.getMessage(), e);
        }
    }
    
    private IType bestType(List<IType> found) throws JavaModelException {
        IType result = found.get(0);
        for (IType t : found) {
            if (StringUtil.hasValue(t.getSource())) result = t;
            else if (JdtUtil.getSource(t) != null) result = t;
        }
        return result;
    }

    @Tool("Eclipse/Java: Find usages of a type, or a method when methodName is set. Best way to find all java class usages.")
    public String findReferences(
            @P(description = "Package name of the class e.g. java.io", name = "package") String pkg,
            @P(description = "type name e.g. File", name = "typeName") String typeName,
            @P(description = "method name on the type; omit for type usages", required = false, name = "methodName") String methodName,
            @P(description = "project name to limit search scope (optional)", required = false, name = "projectName") String projectName) {

        ArgsUtil.requireNonBlank(pkg, "package");
        ArgsUtil.requireNonBlank(typeName, "typeName");

        try {
            var typeOpt = JdtUtil.findType(pkg, typeName, getProgressMonitor(), projectName);

            if (typeOpt.isEmpty()) {
                onProblem("Cannot read references of unknown type " + pkg + "." + typeName);
                return "Type not found. Check your parameters. findJavaType result:\n"
                    + findJavaType(typeName, null, projectName);
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
            IJavaSearchScope scope = JdtUtil.resolveScope(projectName);

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
                    LOG.warn("findReferences with no java source! " + match);
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

    @Tool("Eclipse/JDT: Find workspace files/folders by name or glob (*, ?).")
    public String findResource(
            @P(description = "file name or glob pattern", name = "namePattern") String namePattern,
            @P(name = "projectName", required = false) String projectName) {

        ArgsUtil.requireNonBlank(namePattern, "namePattern");

        var matches = new LinkedHashSet<String>();
        var projects = projectName != null && !projectName.isBlank()
                ? EclipseUtil.findOpenProject(projectName).map(List::of).orElse(EclipseUtil.openProjects())
                : EclipseUtil.openProjects();
        try {

            var mather = StringMatcher.wildCardMatcher(namePattern);

            for (var project : projects) {
                project.accept(resource -> {
                    if (resource.getType() == IResource.FILE 
                            && !resource.isDerived()
                            && matches.size() < MAX_REFERENCE_RESULTS) {
                        var path = JdtUtil.pathOf(resource);
                        if (mather.match(resource.getName()) || mather.match(path)) {
                            matches.add(path);
                        }
                    }
                    return true; // recurse into children
                });
            }
        } catch (CoreException e) {
            throw new RuntimeException("Resource search failed: " + e.getMessage(), e);
        }

        onTool("Search for " + namePattern + " found " + matches.size() + " matches in " + projects.size() + " projects ");
        
        if (matches.isEmpty()) {
            return "No resources found matching '" + namePattern + "' in following projects: " 
                    + projects.stream().map(p -> p.getName()).collect(Collectors.joining(", "));
        }
        var sb = new StringBuilder();
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

    /*
    @Tool("Eclipse/Java: Find implementations/subclasses of a type - use this before glob.")
    public String findImplementations(
            @P(description = "fully qualified interface or class name", name = "typeName") String typeName,
            @P(name = "projectName") String projectName) {

        ArgsUtil.requireNonBlank(typeName, "typeName");

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
    */

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<IType> findByFqn(String pkg, String typeQualifiedName, String projectName) {
        var results = new ArrayList<IType>();
        if (StringUtil.hasValue(projectName)) {
            EclipseUtil.findOpenProject(projectName)
                       .ifPresent(p -> JdtUtil.findType(pkg, typeQualifiedName, getProgressMonitor(), JavaCore.create(p))
                       .ifPresent(results::add));
        } else {
            JdtUtil.findType(pkg, typeQualifiedName, getProgressMonitor()).ifPresent(results::add);
        }
        return results;
    }

    private static void appendTypeSummary(StringBuilder sb, IType type) throws JavaModelException {
        sb.append(JdtUtil.kindOf(type)).append(": ").append(type.getElementName()).append("\n");
        String superclass = type.getSuperclassName();
        if (superclass != null) sb.append("Extends: ").append(superclass).append("\n");
        sb.append("Package: ").append(type.getPackageFragment().getElementName()).append("\n");

        String[] ifaces = type.getSuperInterfaceNames();
        if (ifaces != null && ifaces.length > 0) {
            sb.append("Implements: ").append(String.join(", ", ifaces)).append("\n");
        }
        sb.append("Project: ").append(type.getJavaProject().getProject().getName()).append("\n");
        if (type.getResource() != null) {
            sb.append("Eclipse workspace-relative path: ").append(JdtUtil.pathOf(type.getResource())).append("\n");
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
    
    private String toFQN(String pkg, String clazz) {
        if (pkg == null) return clazz;
        return pkg + "." + clazz;
    }
}

package org.sterl.llmpeon.parts.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.shared.StringUtil;

import jakarta.annotation.Nonnull;

public class JdtUtil {
    
    public static IJavaSearchScope resolveScope(String projectName) {
        if (projectName != null && !projectName.isBlank()) {
            var project = EclipseUtil.findOpenProject(projectName);
            if (project.isPresent()) {
                return SearchEngine.createJavaSearchScope(new IJavaElement[]{ JavaCore.create(project.get()) });
            }
        }
        return SearchEngine.createWorkspaceScope();
    }
    
    public static List<IType> findBySearch(
            @Nullable
            String pkg,
            @Nonnull
            String typeName,
            @Nullable
            String projectName,
            IProgressMonitor monitor) throws JavaModelException {
        var results = new ArrayList<IType>();
        
        int matchRule = (typeName.contains("*") || typeName.contains("?"))
                ? SearchPattern.R_PATTERN_MATCH
                : SearchPattern.R_PREFIX_MATCH;

        new SearchEngine().searchAllTypeNames(
                pkg == null ? "*".toCharArray() : pkg.toCharArray(), 
                SearchPattern.R_PATTERN_MATCH,
                typeName.toCharArray(), matchRule,
                IJavaSearchConstants.TYPE,
                resolveScope(projectName),
                new TypeNameMatchRequestor() {
                    @Override
                    public void acceptTypeNameMatch(TypeNameMatch match) {
                        results.add(match.getType());
                    }
                },
                IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
                monitor);

        return results;
    }

    /**
     * Find a type by FQN across all open Java projects. Returns first match.
     */
    public static Optional<IType> findType(
            String packageName, 
            String typeQualifiedName, 
            IProgressMonitor progressMonitor) {

        for (IProject p : EclipseUtil.openProjects()) {
            var jp = JavaCore.create(p);
            if (jp == null || !jp.exists()) continue;
            var result = findType(packageName, typeQualifiedName, progressMonitor, jp);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    /**
     * Find a type scoped to a specific project.
     */
    public static Optional<IType> findType(
            @Nonnull
            String packageName,
            @Nonnull
            String typeQualifiedName,
            @Nonnull
            IProgressMonitor progressMonitor,
            @Nonnull
            IJavaProject project) {
        
        packageName = StringUtil.stripToNull(packageName);
        try {
            IType type = project.findType(packageName, typeQualifiedName, progressMonitor);
            if (type != null && type.exists()) return Optional.of(type);
        } catch (JavaModelException e) {
            throw new RuntimeException("findType failed for " + typeQualifiedName, e);
        }
        return Optional.empty();
    }
    
    /**
     * Find a type scoped to a specific project.
     */
    public static Optional<IType> findType(
            @Nonnull
            String packageName,
            @Nonnull
            String typeQualifiedName,
            @Nonnull
            IProgressMonitor progressMonitor,
            String project) {
        var p = EclipseUtil.findOpenProject(project);
        if (p.isPresent() && p.get() instanceof IJavaProject jp) {
            return findType(packageName, typeQualifiedName, progressMonitor, jp);
        }
        return findType(packageName, typeQualifiedName, progressMonitor);
    }

    /**
     * Convert a source offset to a 1-based line number.
     */
    public static int offsetToLine(String source, int offset) {
        int line = 1;
        int limit = Math.min(offset, source.length());
        for (int i = 0; i < limit; i++) {
            if (source.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * Extract the Javadoc text from an IMember (IType, IMethod, IField).
     * Returns empty string if no Javadoc or source unavailable.
     */
    public static String javadoc(IMember member) {
        try {
            ISourceRange range = member.getJavadocRange();
            if (range == null) return "";
            ICompilationUnit cu = member.getCompilationUnit();
            if (cu == null) return "";
            String src = cu.getSource();
            if (src == null) return "";
            int start = range.getOffset();
            int end = start + range.getLength();
            if (end > src.length()) return "";
            return src.substring(start, end);
        } catch (JavaModelException e) {
            return "";
        }
    }

    /**
     * Workspace-relative path for a type.
     */
    @Nullable
    public static String pathOf(IType type) {
        if (type == null || type.getPath() == null) return null;
        return type.getPath().toPortableString();
    }
    
    /**
     * Workspace-relative path for a type.
     */
    @Nullable
    public static String pathOf(IResource value) {
        if (value == null || value.getFullPath() == null) return null;
        return value.getFullPath().toPortableString();
    }
    
    /**
     * Best-effort absolute filesystem path for a workspace resource (project, folder, file).
     * {@link IResource#getRawLocation()} may be {@code null} for some project layouts; falls back to
     * {@link IResource#getLocation()}.
     */
    @Nullable
    public static String diskPathOf(@Nullable IResource value) {
        if (value == null) return null;
        var path = value.getRawLocation();
        if (path == null) path = value.getLocation();
        if (path == null) return null;
        return path.toOSString();
    }

    /**
     * Human-readable kind label for a type.
     */
    public static String kindOf(IType type) {
        try {
            if (type.isInterface()) return "interface";
            if (type.isEnum()) return "enum";
            if (type.isRecord()) return "record";
            if (type.isAnnotation()) return "annotation";
            if (Flags.isAbstract(type.getFlags())) return "abstract class";
            return "class";
        } catch (JavaModelException e) {
            return "type";
        }
    }

    public static String getSource(IJavaElement je) throws JavaModelException {
        String result = null;
        var cu = je.getAncestor(IJavaElement.COMPILATION_UNIT);
        if (cu instanceof org.eclipse.jdt.core.ICompilationUnit icu) {
            result = icu.getSource();
        }
        if (StringUtil.hasValue(result)) return result;
        
        // get the type source
        if (je instanceof IType t) result = t.getSource();
        if (StringUtil.hasValue(result)) return result;
        
        // check for the resource
        if (je.getResource() instanceof IFile f && f.exists()) {
            try {
                result = f.readString();
            } catch (CoreException e) { throw new RuntimeException(e); }
        }
        return result;
    }
}

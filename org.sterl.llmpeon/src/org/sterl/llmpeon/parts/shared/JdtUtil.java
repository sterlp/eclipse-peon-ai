package org.sterl.llmpeon.parts.shared;

import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class JdtUtil {

    /**
     * Find a type by FQN across all open Java projects. Returns first match.
     */
    public static Optional<IType> findType(String fqn) {
        for (IProject p : EclipseUtil.openProjects()) {
            IJavaProject jp = JavaCore.create(p);
            if (jp == null || !jp.exists()) continue;
            try {
                IType type = jp.findType(fqn);
                if (type != null && type.exists()) return Optional.of(type);
            } catch (JavaModelException e) {
                // skip project
            }
        }
        return Optional.empty();
    }

    /**
     * Find a type scoped to a specific project.
     */
    public static Optional<IType> findType(String fqn, IJavaProject project) {
        try {
            IType type = project.findType(fqn);
            if (type != null && type.exists()) return Optional.of(type);
        } catch (JavaModelException e) {
            // ignore
        }
        return Optional.empty();
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
    public static String pathOf(IType type) {
        return type.getPath().toPortableString();
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
}

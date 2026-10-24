package org.sterl.llmpeon.parts.tools;

import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.sterl.llmpeon.parts.shared.JdtUtil;

/**
 * The one query-side view on the plan file — R-PT-2/R-PT-2a/R-PT-3
 * (org.sterl.llmpeon/docs/plan-tool.md). A plan written by the disk tools is unknown to the
 * workspace tree, so every "is there a plan?" answer has to refresh first: the tool path
 * ({@code planRead}/{@code hasPlan}) and the per-turn context share this implementation, they only
 * differ in where they report a problem (chat vs. log).
 * <p>
 * A query never throws for a refresh failure — {@code hasPlan()} runs in the turn path, where an
 * exception would leave a half-activated agent — it reports the problem and answers "no plan".
 * The archive path is the opposite case and keeps {@link #refreshDir(IProject, IProgressMonitor)}.
 */
public class PlanFileView {

    /** Seam for the workspace refresh; production is {@code IResource::refreshLocal}. */
    @FunctionalInterface
    public interface Refresh {
        void apply(IResource resource, int depth, IProgressMonitor monitor) throws CoreException;
    }

    private final Refresh refresh;

    public PlanFileView() {
        this(IResource::refreshLocal);
    }

    public PlanFileView(Refresh refresh) {
        this.refresh = refresh;
    }

    /**
     * The plan file as the workspace currently sees it, or {@code null} when there is none.
     * <p>
     * DEPTH_ZERO on the file itself: the query paths run on every turn and on every agent change, a
     * folder refresh would re-read the whole plan dir (archives included) for one exists().
     * <p>
     * A {@link CoreException} is reported to {@code problem} with path and cause and answered with
     * {@code null} — an honest "no plan", never a silent one. A user Stop is not covered by that:
     * {@code OperationCanceledException} is a RuntimeException and runs through untouched (R-PT-1).
     */
    public IFile visible(IProject project, IProgressMonitor monitor, Consumer<String> problem) {
        if (project == null) return null;
        var plan = project.getFile(PlanTool.OVERVIEW_FILE);
        try {
            refresh.apply(plan, IResource.DEPTH_ZERO, IProgressMonitor.nullSafe(monitor));
        } catch (CoreException e) {
            problem.accept("Plan refresh failed for " + JdtUtil.pathOf(plan) + " " + e.getMessage()
                    + " — reporting no plan");
            return null;
        }
        return plan.exists() ? plan : null;
    }

    /**
     * R-PT-1: the archive needs the whole plan dir in sync — the plan itself plus the names already
     * taken by earlier archives. A failure here is fatal for the tool call, so it is thrown, not
     * swallowed; the caller names path and cause.
     */
    public void refreshDir(IProject project, IProgressMonitor monitor) throws CoreException {
        refresh.apply(project.getFolder(PlanTool.PLAN_DIR), IResource.DEPTH_INFINITE,
                IProgressMonitor.nullSafe(monitor));
    }
}

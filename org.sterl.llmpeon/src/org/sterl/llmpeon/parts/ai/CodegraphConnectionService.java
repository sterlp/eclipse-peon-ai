package org.sterl.llmpeon.parts.ai;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.sterl.llmpeon.mcp.CodegraphProbe;
import org.sterl.llmpeon.mcp.CodegraphServerFactory;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.tool.ToolService;

/**
 * Lifecycle owner of the built-in Codegraph connection (ADR-0068): probes binary + index for
 * the active project and keeps the "codegraph" MCP group in sync across all agent-facing
 * registries. Failure-isolated from the user MCP group (R-CG-3), no reconnect churn (R-CG-4),
 * no connect attempts when the probe is negative (R-CG-1). State is RAM-only — no
 * persistence, no UI config (v1).
 */
public class CodegraphConnectionService {

    private static final ILog LOG = Platform.getLog(CodegraphConnectionService.class);

    /** The built-in MCP group — the reserved server name (R-CG-11). */
    public static final String GROUP = CodegraphServerFactory.NAME;

    public enum Kind { OFF, CONNECTED, ERROR }

    /** Connection state — the tooltip is built here so it stays OSGi-testable without SWT. */
    public record State(Kind kind, IProject project, String reason) {
        public String tooltip() {
            var name = project == null ? "" : project.getName();
            return switch (kind) {
                case CONNECTED -> "Codegraph: " + name + " — codegraph_explore available";
                case ERROR -> "Codegraph error (" + name + "): " + reason;
                case OFF -> "Codegraph OFF: " + reason;
            };
        }
    }

    private final Supplier<IProject> projectRef;
    private final List<ToolService> registries;
    private final CodegraphProbe probe;

    private volatile State state = new State(Kind.OFF, null, "no active project");
    /** Identity of the live connection (churn compare, R-CG-4). */
    private volatile Path connectedWorkDir;
    /** Server instructions captured from the initialize result (null when not connected). */
    private volatile String instructions;

    private final Job applyJob = Job.create("Codegraph apply", monitor -> {
        apply();
        return Status.OK_STATUS;
    });

    private Consumer<State> onStateChange = s -> {};

    public CodegraphConnectionService(Supplier<IProject> projectRef, List<ToolService> registries, CodegraphProbe probe) {
        this.projectRef = projectRef;
        this.registries = List.copyOf(registries);
        this.probe = probe;
    }

    /** Schedules the (coalescing) apply — schedule() is idempotent; a running job re-compares afterwards. */
    public void projectChanged() {
        applyJob.schedule();
    }

    /**
     * Synchronous apply body (test seam): probes the LATEST project state and brings the
     * "codegraph" MCP group in sync on every registry. Never touches the user MCP group.
     */
    public IStatus apply() {
        var project = projectRef.get();
        if (project == null) return applyOff(null, "no active project");
        var disk = JdtUtil.diskPathOf(project);
        var diskPath = disk == null ? null : Path.of(disk);
        if (diskPath == null) return applyOff(project, "no active project");
        var binary = probe.findBinary().orElse(null);
        if (binary == null) return applyOff(project, "codegraph binary not found — install codegraph");
        if (!probe.hasIndex(diskPath)) return applyOff(project, "no .codegraph index — run codegraph init in the project");

        // Churn compare: already connected to the desired workDir → nothing to do (R-CG-4)
        if (state.kind() == Kind.CONNECTED && connectedWorkDir != null && connectedWorkDir.equals(diskPath)) {
            return Status.OK_STATUS;
        }

        // Disconnect the codegraph group on every registry (only this group — R-CG-3),
        // then connect: exactly one live `serve --mcp` per workDir (R-CG-4)
        for (var registry : registries) registry.disconnectMcp(GROUP);
        var config = CodegraphServerFactory.create(binary, diskPath);
        try {
            for (var registry : registries) registry.connectMcp(GROUP, config);
        } catch (Exception e) {
            // Symmetric cleanup on failure
            for (var registry : registries) registry.disconnectMcp(GROUP);
            LOG.info("Codegraph connect failed for " + project.getName() + ": " + e.getMessage());
            connectedWorkDir = null;
            instructions = null;
            setState(new State(Kind.ERROR, project, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return new Status(IStatus.ERROR, PeonConstants.PLUGIN_ID, "Codegraph connect failed: " + e.getMessage(), e);
        }
        // Capture the instructions BEFORE the state flip (the state callback must see the new value)
        var captured = registries.isEmpty() ? null
                : registries.get(0).mcpInstructions(GROUP, CodegraphServerFactory.NAME).orElse(null);
        connectedWorkDir = diskPath;
        instructions = captured;
        setState(new State(Kind.CONNECTED, project, null));
        return Status.OK_STATUS;
    }

    /** Negative probe: disconnects a stale codegraph group (if any) and sets the OFF state. */
    private IStatus applyOff(IProject project, String reason) {
        for (var registry : registries) registry.disconnectMcp(GROUP);
        connectedWorkDir = null;
        instructions = null;
        setState(new State(Kind.OFF, project, reason));
        return Status.OK_STATUS;
    }

    public State state() {
        return state;
    }

    /**
     * Project info line for the turn context (R-CG-7) — only when connected: the disk path of
     * the Codegraph index, the codegraph_explore hint and the disk→Eclipse path mapping.
     */
    public String projectInfoLine() {
        var current = state;
        if (current.kind() != Kind.CONNECTED || current.project() == null) return "";
        var project = current.project();
        var disk = JdtUtil.diskPathOf(project);
        if (disk == null) return "";
        return "Codegraph index active — use the codegraph_explore tool (one call returns verbatim source; "
                + "prefer it over search loops). Its results use disk paths under " + disk
                + "; the eclipse*-tools use /" + project.getName() + " paths — map <" + disk + "/...> to /" + project.getName() + "/...";
    }

    /** Server instructions for the turn context (empty when not connected or the server sent none). */
    public Optional<String> instructions() {
        return Optional.ofNullable(instructions);
    }

    /** Dispose: cancels pending applies and disconnects the codegraph group on every registry. */
    public void disconnect() {
        applyJob.cancel();
        for (var registry : registries) registry.disconnectMcp(GROUP);
        connectedWorkDir = null;
        instructions = null;
        setState(new State(Kind.OFF, null, "disconnected"));
    }

    private void setState(State newState) {
        this.state = newState;
        onStateChange.accept(newState);
    }

    /** State callback (default No-Op) — invoked on the apply-job thread; the view wraps it in the UI thread. */
    public void setOnStateChange(Consumer<State> callback) {
        this.onStateChange = callback == null ? s -> {} : callback;
    }
}

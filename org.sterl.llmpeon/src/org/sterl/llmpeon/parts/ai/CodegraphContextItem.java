package org.sterl.llmpeon.parts.ai;

import java.util.Optional;
import java.util.function.Supplier;

import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.parts.shared.JdtUtil;

/**
 * The Codegraph turn-context item (R-CG-7 / R-CG-12): one item carries the whole codegraph
 * context — project info + usage hint + server instructions when CONNECTED, a short state line
 * when OFF/ERROR. The {@link #label()} is the chat signal (R-CG-12) via the existing
 * {@code renderTurnContext} path; the {@link #dedupKey()} keeps every state change to a single
 * injection.
 * <p>
 * Lives in {@code parts.ai} (next to the service): {@code context → parts.ai} would cycle against
 * the existing {@code parts.ai → context} edge, so the item cannot sit in the context package.
 * The project name/path is read from the live {@link CodegraphConnectionService.State} (which
 * carries the project) — no separate project supplier, so the label and render always agree on
 * which project they refer to.
 */
public class CodegraphContextItem implements ContextItem {

    private final Supplier<CodegraphConnectionService.State> state;
    private final Supplier<Optional<String>> instructions;

    public CodegraphContextItem(Supplier<CodegraphConnectionService.State> state,
            Supplier<Optional<String>> instructions) {
        this.state = state;
        this.instructions = instructions;
    }

    /**
     * R-CG-12 chat signal: {@code Codegraph <connected|off|failed> — <Projekt>}. Never null for a
     * live state (all three kinds produce a label), so {@code 📋 Loading …} always fires.
     */
    @Override
    public String label() {
        var s = state.get();
        if (s == null) return null;
        var name = s.project() == null ? null : s.project().getName();
        var stateLabel = switch (s.kind()) {
            case CONNECTED -> "connected";
            case OFF -> "off";
            case ERROR -> "failed";
        };
        return name == null ? "Codegraph " + stateLabel : "Codegraph " + stateLabel + " — " + name;
    }

    /**
     * R-CG-7: state + project name + a mark of the instructions. Unchanged state → same key
     * (deduped, already in history); any state change (kind, project or instructions) → new key,
     * injected exactly once. The instructions are hashed (not embedded) so the key stays short and
     * is not duplicated inside the rendered text.
     */
    @Override
    public String dedupKey() {
        var s = state.get();
        if (s == null) return null;
        var name = s.project() == null ? "" : s.project().getName();
        var instr = instructions.get().orElse(null);
        var instrMark = instr == null ? "none" : "#" + instr.length() + "h" + Integer.toHexString(instr.hashCode());
        return "Codegraph " + s.kind() + " " + name + " " + instrMark;
    }

    /**
     * R-CG-7: CONNECTED → project info (disk-path mapping) + usage hint + instructions;
     * OFF/ERROR → one short state line. {@code null} only when there is no state at all.
     */
    @Override
    public String render() {
        var s = state.get();
        if (s == null) return null;
        return switch (s.kind()) {
            case CONNECTED -> renderConnected(s);
            case OFF -> renderOff(s);
            case ERROR -> renderError(s);
        };
    }

    /** (a) disk→Eclipse path mapping, (b) one-call usage hint, (c) server instructions. */
    private String renderConnected(CodegraphConnectionService.State s) {
        var project = s.project();
        var name = project == null ? "" : project.getName();
        var disk = project == null ? null : JdtUtil.diskPathOf(project);
        var sep = System.lineSeparator();
        var sb = new StringBuilder();
        if (disk != null) {
            sb.append("codegraph_explore returns absolute disk paths under ").append(disk)
              .append("; the eclipse*-tools use /").append(name)
              .append(" paths — map <").append(disk).append("/...> to /").append(name).append("/...")
              .append(" (paths outside the project are not translated — use the disk*-tools with the original path there).");
        }
        sb.append(disk != null ? sep : "")
          .append("Prefer codegraph_explore: one call returns verbatim source — use it instead of a search loop.");
        var instr = instructions.get().orElse(null);
        if (instr != null && !instr.isBlank()) sb.append(sep).append(instr);
        return sb.toString();
    }

    private String renderOff(CodegraphConnectionService.State s) {
        var name = s.project() == null ? null : s.project().getName();
        if (name == null) return "Codegraph not active — no active project.";
        return "Codegraph not active for " + name + " — install codegraph and run 'codegraph init' in the project.";
    }

    private String renderError(CodegraphConnectionService.State s) {
        var reason = s.reason() == null || s.reason().isBlank() ? "connect failed" : s.reason();
        return "Codegraph failed — " + reason;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + (state.get() == null ? "?" : state.get().kind()) + "]";
    }
}

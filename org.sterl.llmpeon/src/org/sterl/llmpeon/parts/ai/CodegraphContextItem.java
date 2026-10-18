package org.sterl.llmpeon.parts.ai;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.parts.shared.JdtUtil;

/**
 * The Codegraph turn-context item (R-CG-7 / R-CG-12 / R-TR-4/5): one item carries the whole
 * codegraph context — project info + server instructions, but ONLY when CONNECTED.
 * OFF/ERROR render {@code null} (R-TR-4): no chat line, the state stays visible via the
 * icon + tooltip only. The {@link #label()} is the chat signal (R-CG-12) via the existing
 * {@code renderTurnContext} path; the {@link #dedupKey()} (block start line) keeps every
 * state change to a single injection.
 * <p>
 * Lives in {@code parts.ai} (next to the service): {@code context → parts.ai} would cycle against
 * the existing {@code parts.ai → context} edge, so the item cannot sit in the context package.
 * The project name/path is read from the live {@link CodegraphConnectionService.State} (which
 * carries the project and the captured instructions) — no separate suppliers, so the label and
 * render always agree on which project they refer to.
 */
public class CodegraphContextItem implements ContextItem {

    private final Supplier<CodegraphConnectionService.State> state;

    public CodegraphContextItem(Supplier<CodegraphConnectionService.State> state) {
        this.state = state;
    }

    /**
     * R-CG-12 chat signal: {@code Codegraph <connected|off|failed> — <Projekt>}. Never null for a
     * live state (all three kinds produce a label) — with R-TR-4 it is the only state signal
     * that can reach the chat (render is CONNECTED-only).
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
     * R-TR-3: the block's exact start line — only CONNECTED carries a block. Unchanged state →
     * same key (deduped, already in history); any state change (project or instructions) →
     * new key, injected exactly once. The instructions are hashed (not embedded) so the key
     * stays short and is not duplicated inside the rendered text.
     */
    @Override
    public String dedupKey() {
        var s = state.get();
        if (s == null || s.kind() != CodegraphConnectionService.Kind.CONNECTED) return null;
        return TaggedResult.PREFIX + head(s);
    }

    /**
     * R-TR-4: CONNECTED → project info (disk-path mapping) + the server instructions captured at
     * connect. OFF/ERROR and no state render {@code null} — no chat line.
     */
    @Override
    @Nullable
    public TaggedResult render() {
        var s = state.get();
        if (s == null || s.kind() != CodegraphConnectionService.Kind.CONNECTED) return null;
        return new TaggedResult(head(s), renderBody(s));
    }

    /** Block head ({@code codegraph: <project> <instrMark>}); the mark is length + hash of the instructions. */
    private String head(CodegraphConnectionService.State s) {
        var name = s.project() == null ? "" : s.project().getName();
        var instr = s.instructions();
        var instrMark = instr == null ? "none" : "#" + instr.length() + "h" + Integer.toHexString(instr.hashCode());
        return "codegraph: " + name + " " + instrMark;
    }

    /** (a) disk→Eclipse path mapping, (b) server instructions. */
    private String renderBody(CodegraphConnectionService.State s) {
        var project = s.project();
        var disk = project == null ? null : JdtUtil.diskPathOf(project);
        var ws = project == null ? null : JdtUtil.pathOf(project);
        var sep = System.lineSeparator();
        var sb = new StringBuilder();
        if (disk != null && ws != null) {
            sb.append("codegraph_explore returns absolute disk paths under ").append(disk)
              .append("; the eclipse*-tools use ").append(ws)
              .append(" paths — map <").append(disk).append("/...> to ").append(ws).append("/...")
              .append(" (paths outside the project are not translated — use the disk*-tools with the original path there).");
        }
        var instr = s.instructions();
        if (instr != null && !instr.isBlank()) sb.append(sb.isEmpty() ? "" : sep).append(instr);
        if (sb.isEmpty()) sb.append("codegraph_explore returns absolute disk paths; the eclipse*-tools use workspace paths.");
        return sb.toString();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + (state.get() == null ? "?" : state.get().kind()) + "]";
    }
}

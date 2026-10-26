package org.sterl.llmpeon.tool.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;

public class AbstractTool implements SmartTool {

    protected AiMonitor monitor = AiMonitor.NULL_MONITOR;
    protected ToolLoopRequest request;

    /**
     * Result attributes of the <b>current</b> call, one fresh map per {@code withToolRequest(request)} —
     * the hook that runs before every invocation. {@code withToolRequest(null)} (the executor's finally
     * block) deliberately leaves it: it runs before {@link org.sterl.llmpeon.tool.ToolService#execute}
     * builds the result, so clearing there would hand over an emptied map.
     */
    private Map<String, Object> resultAttributes = new LinkedHashMap<>();

    protected void onTool(String m) {
        monitor.onTool(m);
    }

    protected void onProblem(String m) {
        monitor.onProblem(m);
    }

    /**
     * Enforce the current request's {@link org.sterl.llmpeon.tool.WriteValidator} on a raw write path.
     * No-op when the tool is invoked without a request (e.g. direct unit-test calls).
     *
     * @throws IllegalArgumentException if the agent may not write to {@code path}
     */
    protected void validateWrite(String path) {
        if (request != null) request.getWriteValidator().validate(path);
    }

    /**
     * Attaches one entry to the {@link dev.langchain4j.data.message.ToolExecutionResultMessage} this call
     * returns. Attributes never reach the model and do survive a history round-trip
     * ({@link org.sterl.llmpeon.tool.ToolResultAttributes}), so a consumer can read a fact back out of the
     * history it already has instead of parsing the model-facing text or keeping a second store.
     * <p>
     * A null key or value is dropped — langchain4j copies attributes into an immutable map.
     */
    protected final void attachResultAttribute(String key, Object value) {
        if (key == null || value == null) return;
        resultAttributes.put(key, value);
    }

    /** The current call's attributes, empty when the tool attached none; never null. */
    public final Map<String, Object> resultAttributes() {
        return Map.copyOf(resultAttributes);
    }

    @Override
    public void withToolRequest(ToolLoopRequest request) {
        this.request = request;
        this.monitor = request == null ? AiMonitor.NULL_MONITOR : AiMonitor.nullSafety(request.getMonitor());
        if (request != null) this.resultAttributes = new LinkedHashMap<>();
    }
}

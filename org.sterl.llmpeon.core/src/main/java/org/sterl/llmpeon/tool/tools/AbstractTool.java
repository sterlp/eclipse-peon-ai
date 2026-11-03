package org.sterl.llmpeon.tool.tools;

import java.nio.file.Path;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.PictureFile;
import org.sterl.llmpeon.shared.PictureMessage;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;

public class AbstractTool implements SmartTool {

    protected AiMonitor monitor = AiMonitor.NULL_MONITOR;
    protected ToolLoopRequest request;

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
     * Loads the picture and queues it as a user message after this round's tool results (R-RP-1).
     * Load first: an invalid picture throws before anything is queued (R-RP-3).
     *
     * @return the tool result text
     * @throws IllegalArgumentException if the file is not a valid picture
     * @throws IllegalStateException if called without a loop request — the result text would lie
     */
    protected String attachPicture(Path file) {
        return attachPicture(file, file.toAbsolutePath().normalize().toString());
    }

    /** @param displayPath the path in the tool family's own dialect, named in every text (R-RP-7) */
    protected String attachPicture(Path file, String displayPath) {
        var picture = PictureFile.load(file, displayPath);
        if (request == null) {
            throw new IllegalStateException("no tool loop request — a picture cannot be attached to the next round");
        }
        request.appendAfterResults(PictureMessage.of(picture));
        onTool("📷 " + picture.displayPath() + " (" + picture.kb() + ")");
        return picture.resultText();
    }

    @Override
    public void withToolRequest(ToolLoopRequest request) {
        this.request = request;
        this.monitor = request == null ? AiMonitor.NULL_MONITOR : AiMonitor.nullSafety(request.getMonitor());
    }
}

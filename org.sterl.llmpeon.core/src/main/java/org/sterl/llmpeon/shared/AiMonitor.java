package org.sterl.llmpeon.shared;

import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

@FunctionalInterface
public interface AiMonitor {
    record AiFileUpdate(String file, String oldContent, String newContent) {}
    
    public static final AiMonitor NULL_MONITOR = new AiMonitor() {
        @Override
        public void onMessage(SimpleMessage m) {}
    };
    
    public static AiMonitor nullSafety(AiMonitor in) {
        return in == null ? NULL_MONITOR : in;
    }
    
    void onMessage(SimpleMessage message);
    
    default void onTool(String message) {
        onMessage(new SimpleMessage(Type.TOOL, message));
    }

    default void onFileUpdate(AiFileUpdate update) {
        onMessage(new SimpleMessage(Type.TOOL, "Updated: " + update.file()));
    }

    default void onProblem(String message) {
        onMessage(new SimpleMessage(Type.PROBLEM, message));
    }
    
    default boolean isCanceled() {
        return false;
    }
}

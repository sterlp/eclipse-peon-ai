package org.sterl.llmpeon.agent;

@FunctionalInterface
public interface AiMonitor {
    record AiFileUpdate(String file, String oldContent, String newContent) {}
    
    void onAction(String description);

    default void onFileUpdate(AiFileUpdate update) {
        onAction("Updated: " + update.file());
    }
    
    default void onThink(String think) {
        onAction("Think: " + think);
    }

    default void onProblem(String value) {
        onAction(value);
    }
}

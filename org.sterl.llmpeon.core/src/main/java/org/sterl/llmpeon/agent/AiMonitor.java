package org.sterl.llmpeon.agent;

@FunctionalInterface
public interface AiMonitor {
    record AiFileUpdate(String file, String oldContent, String newContent) {}
    
    public static final AiMonitor NULL_MONITOR = new AiMonitor() {
        @Override
        public void onAction(String description) {}
    };
    
    public static AiMonitor nullSafty(AiMonitor in) {
        return in == null ? NULL_MONITOR : in;
    }
    
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
    
    default boolean isCanceled() {
        return false;
    }
}

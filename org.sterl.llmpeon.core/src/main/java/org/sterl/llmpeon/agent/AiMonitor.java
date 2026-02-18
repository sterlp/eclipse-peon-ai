package org.sterl.llmpeon.agent;

@FunctionalInterface
public interface AiMonitor {
    void onAction(String description);
}

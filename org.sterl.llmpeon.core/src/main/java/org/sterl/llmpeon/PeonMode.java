package org.sterl.llmpeon;

public enum PeonMode {
    /** Read-only analyst: clarifies requirements and produces a User-Story (the WHAT). */
    PLAN("Peon-Plan"),
    /** Full-access developer: implements the plan (the HOW). */
    DEV("Peon-Dev"),
    /** Orchestrated plan -> dev loop backed by .plan/overview.md. */
    AGENT("Peon-Agent v0.1");
    
    private final String label;

    private PeonMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

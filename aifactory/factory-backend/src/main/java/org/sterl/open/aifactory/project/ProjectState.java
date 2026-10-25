package org.sterl.open.aifactory.project;

/**
 * Registry lifecycle of a project. Adding is synchronous, so only a project whose clone succeeded
 * is ever stored — a failed clone leaves no entry and no folder (R-WP-4).
 */
public enum ProjectState {
    /** Cloned and present on disk. */
    READY
}

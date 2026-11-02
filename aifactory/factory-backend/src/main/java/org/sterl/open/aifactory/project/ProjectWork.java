package org.sterl.open.aifactory.project;

/**
 * What one project is doing (R-WP-24): a turn runs, and the inputs waiting behind it. There is no state
 * "waiting" of its own — {@code queued > 0} only ever means "waiting <b>while</b> a turn runs"
 * ({@link #queued()} is {@code 0} whenever {@code working} is false), which is the invariant R-WP-10
 * gives the queue.
 */
public record ProjectWork(String projectId, boolean working, int queued) {

    /** A project nobody is working on — the answer for a registry entry without a runtime. */
    public static ProjectWork free(String projectId) {
        return new ProjectWork(projectId, false, 0);
    }
}

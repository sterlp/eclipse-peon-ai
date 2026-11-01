package org.sterl.open.aifactory.chat;

import org.sterl.open.aifactory.chat.dto.AgentAction;

/**
 * The one owner of "a turn runs in this project" and "an action runs in this project" — the reason a
 * Clear or a Compact can never meet a turn inside the same memory. Both answers are given inside this
 * object's monitor, which is what a pair of atomics could not do: either the action sees a turn in flight
 * and is refused, or the turn sees the claimed action and waits. There is no third outcome.
 *
 * <p><b>Waiting, never refusing</b> is the point of {@link #awaitNoAction()}: a send that arrives during
 * an action is an input the user still wants (R-WP-10, UC-WP-14), so its task blocks until the action is
 * released instead of the edge answering 409. Only an <em>action</em> is refused, and only while the
 * project is busy — a clear asked for in the middle of a turn is not a wish the user still holds
 * afterwards.</p>
 *
 * <p>Nothing here holds the monitor across core work: {@link ProjectRuntime} claims, then runs the action
 * outside, so reading the tiles and asking for a stop are never made to wait by a turn or a Compact.</p>
 */
final class TurnClaim {

    /** An action this project claimed for one tile — the tiles show it before the core knows. */
    record Action(String name, AgentAction action) {
    }

    private boolean turnInFlight;
    private Action action;

    /**
     * Called on the sender's own thread: the flag follows the <b>intent</b>, so a stop or an action
     * arriving one millisecond after a send already addresses the turn the user started and not the moment
     * a worker thread picked the task up (D-13).
     *
     * @return {@code true} when this send is the one that opens the turn — a later send of the same turn is
     *         a queued input and owns nothing, so it neither ends the turn nor resets its cancel flag
     */
    synchronized boolean openTurn() {
        boolean opens = !turnInFlight;
        turnInFlight = true;
        return opens;
    }

    synchronized boolean turnInFlight() {
        return turnInFlight;
    }

    /**
     * A stop may take the turn only while nothing else shares the monitor with it: the core reads the
     * project's one cancel flag, so stopping a turn that is only <em>waiting</em> behind a Compact would
     * cut that Compact in half (D-17). Refused rather than deferred — the turn stays open, so the same
     * click works again once the action has released it.
     */
    synchronized boolean turnIsStoppable() {
        return turnInFlight && action == null;
    }

    /** The end of the turn — only the task that owns it may call this (see {@link #openTurn()}). */
    synchronized void endTurn() {
        turnInFlight = false;
    }

    /** The claimed action, or {@code null} — read by the tiles, one owner and three readers. */
    synchronized Action action() {
        return action;
    }

    /**
     * Claims one Clear or Compact for one tile.
     *
     * @param teamBusy whether any agent of the project works or compacts — asked of the runtime, because
     *                 the core flags are the only truth about a delegation this object cannot see
     * @throws IllegalStateException a turn is in flight, the team works, or another action holds the claim
     *             (⇒ 409: an action is never queued behind a turn)
     */
    synchronized void claimAction(String name, AgentAction action, boolean teamBusy) {
        if (turnInFlight || teamBusy || this.action != null) {
            throw new IllegalStateException("a turn or another action is still running");
        }
        this.action = new Action(name, action);
    }

    /** Lets every waiting turn task go — the {@code finally} of the action task, before its last snapshot. */
    synchronized void releaseAction() {
        action = null;
        notifyAll();
    }

    /**
     * Blocks a turn task while an action holds the claim. It waits and never refuses: the input a user sent
     * during an action is answered after it, which is the whole of BUG-1.
     */
    synchronized void awaitNoAction() {
        while (action != null) {
            try {
                wait();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the running action",
                        interrupted);
            }
        }
    }
}

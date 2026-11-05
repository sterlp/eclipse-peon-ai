package org.sterl.open.aifactory.chat;

import java.util.concurrent.CancellationException;

import org.sterl.llmpeon.exception.ExceptionUtil;

/**
 * Whether the end of a turn is news for the user. A {@link CancellationException} is his own stop
 * (UC-WP-50) and not a failure — reporting it as one would show an error he asked for (D-16).
 *
 * <p>A rule of one question and no state, so it is also the place a test can hold: the core cancels in a
 * stream that a test cannot interrupt at a chosen moment, but it always reports the cancel as this one
 * exception type, possibly nested in a cause.</p>
 */
enum TurnOutcome {

    /** The user ended the turn — {@code done}, no {@code error}, no failed agent. */
    CANCELED,

    /** Anything else — the turn did not end the way anyone wanted. */
    FAILED;

    static TurnOutcome of(Throwable failure) {
        return ExceptionUtil.isCanceled(failure) ? CANCELED : FAILED;
    }
}

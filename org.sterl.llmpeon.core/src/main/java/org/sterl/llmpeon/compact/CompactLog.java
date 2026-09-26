package org.sterl.llmpeon.compact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log facade and test seam of the compact component (R-CIB-6, R-CC-11): the entry log ("exactly
 * ONE debug log") and the result line ("info stage 1 / warn stage 2 / error final stage") are BDD,
 * so tests inject a capturing implementation instead of a log-capture library.
 * Production default: slf4j.
 */
public interface CompactLog {

    void debug(String message, Object... args);

    void info(String message, Object... args);

    void warn(String message, Object... args);

    void error(String message, Object... args);

    /** The production default — plain slf4j. */
    static CompactLog slf4j() {
        Logger log = LoggerFactory.getLogger(CompactService.class);
        return new CompactLog() {
            @Override public void debug(String m, Object... a) { log.debug(m, a); }
            @Override public void info(String m, Object... a) { log.info(m, a); }
            @Override public void warn(String m, Object... a) { log.warn(m, a); }
            @Override public void error(String m, Object... a) { log.error(m, a); }
        };
    }
}

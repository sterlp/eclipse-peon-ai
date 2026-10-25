package org.sterl.open.aifactory.config;

/**
 * Where an effective value came from — R-WP-16 makes the view name it ("Woher ein Wert kommt, zeigt
 * der View"), so this enum is the one type that crosses the DTO boundary: it names a source, never a
 * value.
 */
public enum KeySource {

    /** The factory's own {@code config.properties}. */
    FILE,
    /** The process environment — start value only, the file wins (R-WP-9). */
    ENV,
    /** Neither carries it. */
    NONE
}

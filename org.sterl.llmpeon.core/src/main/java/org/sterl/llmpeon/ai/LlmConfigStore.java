package org.sterl.llmpeon.ai;

/**
 * String key-value store for the LLM configuration. The plugin adapts this to
 * {@code IEclipsePreferences}; core tests use a {@link java.util.Map}-based double.
 *
 * <p>All values are stored as strings — typed parsing (long/int/boolean/double, CSV maps) is the
 * responsibility of {@link LlmConfigLoader}, keeping this contract transport-agnostic.</p>
 */
public interface LlmConfigStore {

    /** Returns the value stored under {@code key}, or {@code defaultValue} if absent. */
    String get(String key, String defaultValue);

    /** Stores {@code value} under {@code key}. */
    void put(String key, String value);

    /** Removes {@code key}; no-op if absent. */
    void remove(String key);
}

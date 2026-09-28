package org.sterl.llmpeon.parts.config;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;
import org.sterl.llmpeon.ai.LlmConfigStore;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Adapts {@link IEclipsePreferences} to the core {@link LlmConfigStore}. Every {@code put}/{@code remove}
 * flushes immediately so a config change is persisted as soon as it is made (matching the historic
 * {@code prefs.put(...); prefs.flush();} pattern).
 */
public class EclipseLlmConfigStore implements LlmConfigStore {

    private final IEclipsePreferences prefs;

    public EclipseLlmConfigStore(IEclipsePreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public String get(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    @Override
    public void put(String key, String value) {
        prefs.put(key, value);
        flush();
    }

    @Override
    public void remove(String key) {
        prefs.remove(key);
        flush();
    }

    /**
     * Puts the value, or removes the key when it is blank (empty = unset — never a null put:
     * JFace's setValue(name, null) NPEs in EclipsePreferences.put and leaves a partial save).
     * Shared by the basic and the advanced preference pages.
     */
    public static void putOrRemove(EclipseLlmConfigStore store, String key, String value) {
        if (StringUtil.hasValue(value)) {
            store.put(key, value);
        } else {
            store.remove(key);
        }
    }

    private void flush() {
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            throw new IllegalStateException("Failed to persist LLM config preference", e);
        }
    }
}

package org.sterl.llmpeon.ai;

import java.util.HashMap;
import java.util.Map;

/** Map-backed {@link LlmConfigStore} for tests. */
class MapLlmConfigStore implements LlmConfigStore {

    private final Map<String, String> map = new HashMap<>();

    @Override
    public String get(String key, String defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    @Override
    public void put(String key, String value) {
        map.put(key, value);
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    Map<String, String> asMap() {
        return map;
    }
}

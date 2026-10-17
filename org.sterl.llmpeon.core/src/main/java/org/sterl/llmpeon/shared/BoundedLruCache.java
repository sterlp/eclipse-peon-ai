package org.sterl.llmpeon.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Bounded LRU cache shared by the RAM caches of this codebase
 * (docs/bounded-caches.md, R-BC-5): a RAM cache without self-truncation is a leak — every new
 * cache uses this class (or has a finite key-space and says so in its Javadoc).
 *
 * <p>True LRU: every access ({@link #get}, {@link #put}, {@link #computeIfAbsent} on an existing
 * key) refreshes the key's recency; when the bound is exceeded the least recently used entry is
 * evicted. Thread-safe via {@code synchronized} methods — deliberately not lock-free (load is
 * low, correctness wins).
 *
 * <p>Contract: {@code maxEntries > 0}. No null values: {@code put(key, null)} is a no-op and a
 * {@code null} mapping result is neither stored nor returned as a hit. No Map view is exposed —
 * the eviction behaviour must not leak to the outside.
 */
public final class BoundedLruCache<K, V> {

    private final int maxEntries;
    private final Map<K, V> map;

    public BoundedLruCache(int maxEntries) {
        this.maxEntries = maxEntries;
        this.map = new LinkedHashMap<>(16, 0.75f, /* accessOrder = */ true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * @return the cached value, or {@code null} if absent. Refreshes the key's recency.
     */
    public synchronized V get(K key) {
        return map.get(key);
    }

    /**
     * Inserts or replaces a value (refreshing recency) and evicts the least recently used entry
     * when the bound is exceeded. A {@code null} value is a no-op.
     *
     * @return the previous value for the key, or {@code null} if there was none
     */
    public synchronized V put(K key, V value) {
        if (value == null) return null;
        return map.put(key, value);
    }

    /**
     * Returns the existing value for the key (refreshing recency, no re-compute), or computes it
     * with the mapping function, stores and returns it. A {@code null} mapping result is neither
     * stored nor returned as a hit.
     */
    public synchronized V computeIfAbsent(K key, Function<K, V> mappingFunction) {
        V existing = map.get(key);
        if (existing != null) return existing;
        V computed = mappingFunction.apply(key);
        if (computed == null) return null;
        map.put(key, computed);
        return computed;
    }

    /**
     * @return the current entry count (always &le; {@code maxEntries})
     */
    public synchronized int size() {
        return map.size();
    }
}

package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BoundedLruCacheTest {

    // R-BC-1 (org.sterl.llmpeon.core/docs/bounded-caches.md)
    // Cap 3: put a,b,c then d -> the least recently used (a) is evicted, size stays 3.
    // Mutation: unbounded map (no removeEldestEntry) -> size 4, get(a) != null -> red.
    @Test
    void boundedLruEvictsLeastRecentlyUsed() {
        var cache = new BoundedLruCache<String, String>(3);
        cache.put("a", "va");
        cache.put("b", "vb");
        cache.put("c", "vc");
        cache.put("d", "vd");

        assertThat(cache.get("a")).isNull();
        assertThat(cache.get("b")).isEqualTo("vb");
        assertThat(cache.get("c")).isEqualTo("vc");
        assertThat(cache.get("d")).isEqualTo("vd");
        assertThat(cache.size()).isEqualTo(3);
    }

    // R-BC-1 (org.sterl.llmpeon.core/docs/bounded-caches.md)
    // Cap 3: put a,b,c, get(a) refreshes a, put d -> b (now least recently used) is evicted, a survives.
    // Mutation: FIFO (old WebFetchTool behavior, get does not refresh) -> a evicted instead of b -> red.
    @Test
    void getRefreshesRecency() {
        var cache = new BoundedLruCache<String, String>(3);
        cache.put("a", "va");
        cache.put("b", "vb");
        cache.put("c", "vc");
        assertThat(cache.get("a")).isEqualTo("va");
        cache.put("d", "vd");

        assertThat(cache.get("a")).isEqualTo("va");
        assertThat(cache.get("b")).isNull();
        assertThat(cache.get("c")).isEqualTo("vc");
        assertThat(cache.get("d")).isEqualTo("vd");
        assertThat(cache.size()).isEqualTo(3);
    }

    // R-BC-1 (org.sterl.llmpeon.core/docs/bounded-caches.md)
    // 8 threads x 2000 ops (put/get/computeIfAbsent) over 64 keys > cap 16: no exception, no
    // inconsistent value, size <= cap after join.
    // Mutation: unsynchronized LinkedHashMap -> ConcurrentModificationException or size > 16 -> red.
    @Timeout(30)
    @Test
    void boundedLruIsThreadSafe() throws Exception {
        var cache = new BoundedLruCache<Integer, String>(16);
        var failures = new AtomicReference<Throwable>();
        var threads = 8;
        var opsPerThread = 2000;
        var keySpace = 64;
        var start = new CountDownLatch(1);
        var workers = new Thread[threads];

        for (int t = 0; t < threads; t++) {
            final int thread = t;
            workers[t] = new Thread(() -> {
                try {
                    start.await();
                    for (int op = 0; op < opsPerThread; op++) {
                        int key = (thread * 31 + op) % keySpace;
                        String expected = "v" + key;
                        switch (op % 3) {
                            case 0 -> cache.put(key, expected);
                            case 1 -> {
                                var value = cache.get(key);
                                if (value != null && !expected.equals(value))
                                    throw new IllegalStateException("inconsistent value for key " + key + ": " + value);
                            }
                            case 2 -> {
                                var value = cache.computeIfAbsent(key, k -> "v" + k);
                                if (value != null && !expected.equals(value))
                                    throw new IllegalStateException("inconsistent value for key " + key + ": " + value);
                            }
                        }
                    }
                } catch (Throwable e) {
                    failures.compareAndSet(null, e);
                }
            });
        }
        for (var worker : workers) worker.start();
        start.countDown();
        for (var worker : workers) worker.join();

        if (failures.get() != null) throw new AssertionError("thread failure", failures.get());
        assertThat(cache.size()).isLessThanOrEqualTo(16);
        for (int key = 0; key < keySpace; key++) {
            var value = cache.get(key);
            if (value != null) assertThat(value).as("value of key %s", key).isEqualTo("v" + key);
        }
    }
}

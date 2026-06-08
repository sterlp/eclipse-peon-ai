package org.sterl.llmpeon.shared.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests thread safety of SimplePromptFile.buildShortInfo()
 */
class SimplePromptFileThreadSafetyTest {

    @Test
    void testConcurrentBuildShortInfo() throws InterruptedException {
        var promptFile = new SimplePromptFile("test-skill", "A test skill", Path.of("/tmp/test.md"));
        
        int threadCount = 100;
        var executor = Executors.newFixedThreadPool(threadCount);
        var startLatch = new CountDownLatch(1);
        var results = new ArrayList<String>();
        var errorCount = new AtomicInteger(0);
        
        // Run concurrent calls
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    String result = promptFile.buildShortInfo();
                    synchronized (results) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            });
        }
        
        // Release all threads at once
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        assertEquals(0, errorCount.get(), "No exceptions should occur during concurrent access");
        
        // Check all results are identical
        if (!results.isEmpty()) {
            String firstResult = results.get(0);
            long inconsistentCount = results.stream()
                .filter(r -> !firstResult.equals(r))
                .count();
            assertEquals(0, inconsistentCount, 
                "All threads should return identical content - thread safety violation detected!\n"
                + "Expected: " + firstResult);
        }
    }
}

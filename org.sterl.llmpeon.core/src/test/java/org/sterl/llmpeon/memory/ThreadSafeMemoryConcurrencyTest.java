package org.sterl.llmpeon.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ConcurrentModificationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

class ThreadSafeMemoryConcurrencyTest {

    @Test
    void forEachShouldNotThrowConcurrentModificationException() throws InterruptedException {
        // GIVEN a ThreadSafeMemory with some messages
        var memory = new ThreadSafeMemory();
        for (int i = 0; i < 50; i++) {
            if (i % 2 == 0) {
                memory.add(UserMessage.from("user-" + i));
            } else {
                memory.add(AiMessage.from("ai-" + i));
            }
        }
        
        var errors = new AtomicInteger(0);
        var iterations = 100;
        var threads = 4;
        
        // WHEN multiple threads concurrently use forEach while others modify
        try (var executor = Executors.newFixedThreadPool(threads)) {
            var latch = new CountDownLatch(iterations * threads);
            
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        try {
                            if ((threadId + i) % 2 == 0) {
                                // Add new message (synchronized)
                                memory.add(UserMessage.from("new-" + i));
                            } else {
                                // forEach — NOT synchronized!
                                memory.forEach(m -> { 
                                    if (m == null) throw new RuntimeException(); 
                                });
                            }
                        } catch (ConcurrentModificationException e) {
                            errors.incrementAndGet();
                        } catch (Exception e) {
                            // Catch any other exceptions too
                            System.err.println("Unexpected exception: " + e.getMessage());
                        }
                        latch.countDown();
                    }
                });
            }
            
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            if (!completed) {
                throw new RuntimeException("Latch did not complete in time - possible deadlock or hang");
            }
        }
        
        // THEN no ConcurrentModificationException should occur
        assertThat(errors.get())
            .as("Should have no concurrent modification exceptions from forEach")
            .isEqualTo(0);
    }
}

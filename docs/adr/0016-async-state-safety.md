# 0016 — Async State Safety

**Status:** Accepted · **Date:** 2026-07-25

## Context
When chaining background jobs or handling async callbacks, resetting references (e.g., `monitorRef.set(null)`) before reading state variables (e.g., `monitor.isCanceled()`, success responses) yields stale/incorrect states. The reset and the read may execute on different threads with no ordering guarantee.

## Decision
Always capture state variables *before* resetting their references:
```java
boolean wasCanceled = monitor.isCanceled(); // Capture FIRST
monitor.done();
monitorRef.set(new NullProgressMonitor());   // Reset AFTER
```

## Consequences
- Eliminates race conditions where cancellation status is read after reset, causing auto-chaining on aborted calls.
- Applies to any async pattern: job callbacks, streaming completions, and monitor lifecycles.

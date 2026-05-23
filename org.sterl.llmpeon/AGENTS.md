- `mvn clean verify` or in eclipse clean build
- Read parent `AGENTS.md` if needed
- ensure your implementation is thread save using Atomic* or ReentrantLock

# Error
Nicht verschlucken sondern zurückgeben - entweder als dialog, oder unter verwendung des Status objekt mit: PeonConstants

```java
// lock ui if needed
...
// start job
Job.create("Connecting MCP servers", monitor -> {
    Exception ex = null;
    try {
        // do stuff -> pass monitor
        // update UI state
        EclipseUtil.runInUiThread(parent, () -> ...);
        return PeonConstants.okStatus("xxxx");
    } catch (Exception e) {
        ex = e;
    } finally {
       // unlock ui if needed
    }
    return PeonConstants.status("xxxx", ex);
}).schedule();
```

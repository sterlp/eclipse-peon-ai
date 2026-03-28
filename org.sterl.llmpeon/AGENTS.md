# Build
`mvn clean verify` or in eclipse clean build

# Structure
Read parent `AGENTS.md` if needed

# Error
Nicht verschlicken sondern zurückgeben - entweder als dialog, oder unter verwendung des Status objekt mit: PeonConstants

```java
// lock ui if needed

// start job
Job.create("Connecting MCP servers", monitor -> {
    Exception ex = null;
    try {
        // do stuff -> pass monitor
        return PeonConstants.okStatus("xxxx");
    } catch (Exception e) {
        ex = e;
    } finnaly {
       // unlock ui if needed
    }
    return PeonConstants.status("xxxx", ex);
}).schedule();
```
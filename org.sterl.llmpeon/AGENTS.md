See parent: `/llmpeon-parent/AGENTS.md` (Global Rules, Thread Safety).

# Plugin Specifics

- **Error Handling**: Do not swallow exceptions. Return via dialog or `PeonConstants.status()`.
- **Background Jobs**: Use `Job.create(...)` for background work. Update UI only in the UI thread (`EclipseUtil.runInUiThread`).


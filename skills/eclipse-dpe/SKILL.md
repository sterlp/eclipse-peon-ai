---
name: eclipse-dpe
description: SWT-UI tests in PDE JUnit launches — reuse the workbench Display (SWT allows one per process), do all widget access on the UI thread, wait on observable state.
---

# SWT-UI tests in PDE JUnit (verified 2026-09-02, inc-25)

First working SWT-display test of the project: `org.sterl.llmpeon.test/ModelComboWidgetTest`.

## Rules

1. **Never `new Display()` in a PDE test** — the workbench already owns the process's single
   display; a second one throws `SWTError: Not implemented [multiple displays]`.
   Reuse it: `Display.getDefault()` (the workbench display is the first created in the process).
2. **PDE runs test methods on a NON-UI thread** (`NonUIThreadTestApplication`) — every SWT
   call (create widgets, read state, notifyListeners) must run on the UI thread. Use
   `EclipseUtil.runInUiThread(Supplier)` (asyncExec + CompletableFuture) and `.get(timeout)`
   from the test thread. No deadlock: the workbench UI thread keeps running its own event loop,
   so asyncExec applies (e.g. `applyModelList`) are processed without pumping.
3. **Wait on observable state, not on jobs**: poll a UI-thread condition (combo items, text)
   with a bounded deadline (5 s → `fail`). For non-observable transitions (e.g. a failed refresh
   re-applying the same list) use a bounded settle sleep (2 s) — make the failure observable by
   changing the mock server's response first, so a wrong apply would be caught.
4. **Assume-guard, don't fail**: wrap setup in try/catch → `Assume.assumeNoException(...)` so a
   missing workbench display skips the test instead of failing the suite.
5. **Dispose only what you created**: dispose your `Shell` in `@After`; NEVER dispose the
   workbench display.
6. **PDE fragment classpath is picky**: the test fragment's compile classpath did not resolve
   `org.eclipse.core.runtime.jobs.JobManager` (other `org.eclipse.core.runtime` packages did);
   adding `org.eclipse.core.runtime` to the fragment's `Require-Bundle` did not help without a
   PDE container re-resolution. Prefer SWT-free seams + observable-state waits over extra
   platform imports.

## Skeleton

```java
@Before public void setUp() {
    try {
        ui(() -> { display = Display.getDefault(); shell = new Shell(display); return null; });
    } catch (AssertionError e) {
        Assume.assumeNoException("no workbench display — SWT test skipped", e.getCause());
    }
}
@After public void tearDown() {
    if (shell != null && !shell.isDisposed()) ui(() -> { shell.dispose(); return null; });
}
private <T> T ui(Supplier<T> fn) {
    return EclipseUtil.runInUiThread(fn).get(10, TimeUnit.SECONDS); // wrap checked exceptions
}
```

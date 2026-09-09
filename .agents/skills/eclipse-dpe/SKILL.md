---
name: eclipse-dpe
description: Eclipse PDE development patterns for SWT tests, workspace resources, JDT project handles, path separator semantics, and Maven/Eclipse build synchronization.
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


## Deterministic PDE test infrastructure

- PDE launch workspace attributes use the stable `IPDELauncherConstants` values `location`,
  `clearws`, and `askclear`. Set `askclear=false`, `clearws=false`, and a stable location on both
  newly created and reused launch configurations; otherwise an old configuration retains dialog
  behavior. String literals avoid adding `org.eclipse.pde.launching` when the target may omit it.
- Resolve repository fixtures from an explicit system property first. Otherwise start at the test
  bundle location and search upward for a marker such as `test_project/.project`; never derive the
  fixture from the process working directory.
- Eclipse resource encoding can be declared per file in
  `.settings/org.eclipse.core.resources.prefs`, but do not trust that declaration to create
  byte-exact non-UTF-8 fixtures. Generate exact bytes with `Files.write(..., charset)` and verify
  the byte array.
- A workspace may expose the same disk content through a root project and an imported nested
  project. Scope searches to the intended project and avoid workspace-wide absolute counts.
- `eclipseReadFile` reads through `IFile.readString()` using the workspace file charset;
  `diskReadFile` uses `Files.readString()` and therefore UTF-8. Their range semantics are shared
  through `FileLines`; only character decoding legitimately differs.
- Tool results are passed unchanged into `ChatRequest.messages`. Shortened text seen in problem
  notifications is display-only and does not truncate the stored tool result.

## Workspace and JDT resource contracts

- `JavaCore.create(IProject)` returns a Java-project handle; it does not prove the Java project
  exists. Check `IJavaProject.exists()` before using it as a search scope.
- `IResource.refreshLocal(IResource.DEPTH_INFINITE, monitor)` reconciles external creations,
  deletions, and changes into the workspace resource tree. `IContainer` inherits this method,
  so a project-or-folder search can refresh one `List<IContainer>` scope. It is long-running and
  can throw `CoreException`, so run it off the UI thread and log or throw, never both.
- In PDE tests without a mocking library, expose a narrow `protected` operation seam and override
  it in an anonymous subclass to count calls. This makes rules such as “no refresh after a direct
  hit” deterministic without timing assertions.
- To prioritize a selected project without hiding other projects, use the existing
  `EclipseUtil.openProjectsPreferring(currentProject)`, then consume one result limit across the list.
- Maven commands require the repository's OS disk path, not its Eclipse workspace path. Obtain
  both with `eclipseListAllOpenProjects` when uncertain.
- After changing core APIs embedded in the plugin JAR, rebuild the reactor slice that recopies
  `llmpeon-core.jar`, then refresh and build the plugin and test projects before PDE tests.

## Console API in PDE tests

- `MessageConsoleStream` and `IOConsoleOutputStream` do not synchronously update the document: `IOConsolePartitioner.streamAppended` queues work for a `QueueProcessingJob extends UIJob`. Without a Display the document remains empty, so this path is unsuitable for display-free tests.
- `TextConsole.getDocument().set(String)` updates its `ConsoleDocument` synchronously without a Display and is the preferred test path.
- A minimal `TextConsole` subclass whose `getPartitioner()` returns `null` works while no console page or view is opened. Its `ConsolePatternMatcher.MatchJob` is a plain `Job`, not a `UIJob`.
- Register test consoles with `ConsolePlugin.getDefault().getConsoleManager().addConsoles(...)` and remove them in `@After` with `removeConsoles(...)`. Use `autoLifecycle=false` to keep the console inert.


## Test fixture & unattended PDE launches (verified 2026-09-03, cycle "Test-Setup")

- **Never let an integration test import the project under test into its own runtime workspace.**
  It drags along `bin/`, `target/` and the plugin's own settings, and the expected hit counts
  drift with every code change. Use a dedicated, versioned fixture project instead — here
  `/llmpeon-parent/test_project`, located via the system property `peon.test.project`
  (`PeonTestFixture`), see `docs/adr/0037-dedicated-test-fixture-project.md`.
- **A missing fixture must fail, not skip.** Silent `Assume`-skips hid 15 dead tests here for
  months. Skips are legitimate only for "no SWT display".
- **PDE launches block on a modal dialog** unless the launch config sets `ASKCLEAR=false`
  (plus `LOCATION` and `DOCLEAR`). Apply it in *every* config path of the runner, not just one —
  the second path is the one that hangs at 3 a.m.
- **The fixture is the only source of expected content.** Never derive expected match counts
  from production code. When adding fixture files, anchor them in the integrity test
  (`TestFixtureIntegrityTest`) so nobody removes them by accident, and avoid reusing an existing
  search token that another test counts exactly.
- **Don't trust a file's name about its encoding.** A file called `iso-test.txt` here was
  actually UTF-8, so its test never exercised ISO behaviour. Verify bytes (`xxd`) — shell is
  fine for read-only diagnosis, never for file I/O.
- **Known asymmetry:** `eclipseWriteFile`/`IoUtils` *read* with `IFile#getCharset()` but always
  *write* UTF-8. Round-tripping an ISO-8859-1 / Windows-1252 file corrupts it silently. Do not
  build fixtures for non-UTF-8 encodings via the write tools.


## Workspace search & grep — why "not found" lies (verified 2026-09-03, cycles 2b-1/2b-2)

The daily "No files found for a file that exists" was never a wildcard problem:

- **It is the limit, consumed in alphabetical project order.** Iterating `EclipseUtil.openProjects()`
  and dividing the limit per project means a big foreign project (`langchain4j`) exhausts the
  default limit of 100 before the selected project is reached. Use
  `EclipseUtil.openProjectsPreferring(currentProject)` and consume **one global** limit across the
  whole list — foreign projects stay reachable, only their order changes.
- **Nested open projects double every hit** (a file visible as `/parent/X` *and* `/X`), halving the
  effective limit. Deduplicate by **disk path**, not by workspace path.
- **Clamp an incoming `limit` at both ends.** A negative limit produced an instantly empty result —
  a false negative created by the fix itself. Lower bound 1.
- **Never let the first hit bypass a filter** (`results.isEmpty() || …` on the derived/`bin/`
  check). Filters apply without exception.
- **Refresh only on an empty result, exactly once, then search again**
  (`docs/adr/0038-refresh-on-empty-search.md`). Refreshing before every search costs seconds in a
  large workspace for a rare case. And refresh **only the selected or explicitly named scope** —
  the refresh target is not the search scope.
- **Say where you looked.** An empty result names the scope and the translated pattern, not just
  "No files found".
- **Regex first, literal on `PatternSyntaxException`, and always name the mode.** Character
  heuristics ("looks like code → literal") are guesswork. Note `C++` *is* a valid regex
  (possessive quantifier) and matches every `C` — without the mode in the output an agent cannot
  tell a surprising result from a correct one.
- **Extensionless files are not nothing.** `Dockerfile`, `Makefile`, `.gitignore` were silently
  skipped by the extension whitelist. Keep a shared `TextFileTypes` (extensions **and** file
  names) in core for both tool families, and name the type filter when the result is empty.



## Path strings and separators — `/` vs `\` (verified 2026-09-04, bug-hunt #9 follow-up)

- **`Path.of(...)` follows the host OS.** On Windows both `/` and `\` are separators; on POSIX
  `\` is an ordinary filename character (`a\b.txt` is one segment). The same input string behaves
  differently per platform — never rely on `Path` for platform-independent path semantics.
- **`Path#normalize()` is platform-dependent twice over:** it only resolves `..` for the host
  separator, and on Windows `toString()` re-emits `\`. For string-level matching (globs,
  allowlists) written with `/` (Eclipse convention) it breaks the match on Windows. Use
  `FileUtils.normalizeSegments` instead: `\`→`/` first (`normalizePath`), then `.`/`..` segment
  resolution — pure string, no filesystem, identical result on every OS.
- **Eclipse `IPath.fromPortableString` ("portable" = `/`) converts `\`→`/` only when
  `Constants.RUNNING_ON_WINDOWS`** (`Path(String, forWindows)` → `backslashToForward`). So
  workspace-relative paths with backslashes work on Windows and fail on macOS/Linux ("file not
  found") — an honest error, not a false negative.
- **Design rule (PO decision 2026-09-04):** normalize unconditionally at string-comparison
  choke-points (e.g. `AllowlistWriteValidator` — a `/`-glob matched against a `\`-path compares
  unlike with unlike, and a mismatch there is a *silent bypass*). Do NOT normalize in the file
  tools: Windows understands both separators natively, and POSIX normalization would make files
  literally named `a\b.txt` unaddressable (real loss for a hypothetical LLM slip; the tool's
  "file not found" is loud and self-healing). See `docs/resolved-points.md`.
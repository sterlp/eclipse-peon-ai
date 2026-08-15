# ADR-0027: OSGi Plugin Test Constraints & Setup

**Status:** Accepted  
**Context:** Eclipse plugin tests (`org.sterl.llmpeon.test`) run inside the OSGi runtime with a full Eclipse workbench startup, unlike standard Maven/JUnit tests. This imposes strict classpath and lifecycle constraints.

**Decision:**
- Plugin tests use **JUnit 4** (provided by Eclipse/OSGi). External assertion libraries (e.g., AssertJ) are not added unless explicitly bundled in the Target Platform, to avoid OSGi classpath overhead.
- Core module (`org.sterl.llmpeon.core`) remains a standard Maven project and uses **JUnit 5 + AssertJ**.
- Plugin tests cannot rely on local file system paths outside the workspace; they must use Eclipse APIs (`IWorkspaceRoot`, `IFile`) or Jimfs where applicable.
**Consequences:**
- No AssertJ in plugin tests — use `assertEquals`, `assertTrue`, etc.
- Dependencies added to `pom.xml` are invisible at runtime unless they are OSGi bundles in the Target Platform.
- Test setup must account for Eclipse startup overhead; keep plugin tests lightweight and isolated.
- **Test Split:** Plugin tests are split into two base classes:
  - `AbstractUnitTest` — no Eclipse dependency, runs fast everywhere, no `assumeTrue` gates.
  - `AbstractIntegrationTest` — handles workspace import, `isWorkspaceAvailable()` checks, and Eclipse-specific helpers (`eclipseWriteFile`, etc.). Only tests actually requiring the workspace extend this.
  - Prevents pure logic tests from being blocked by missing Eclipse runtime or Tycho workspace overlaps.

**Consequences:**
- No AssertJ in plugin tests — use `assertEquals`, `assertTrue`, etc.
- Dependencies added to `pom.xml` are invisible at runtime unless they are OSGi bundles in the Target Platform.
- Test setup must account for Eclipse startup overhead; keep plugin tests lightweight and isolated.

# Eclipse PDE Build Issues

## Incremental Build Produces Broken .class Files

### Symptom
After the first successful launch of the Eclipse Application, any subsequent launch (after saving a file) fails with:

```
java.lang.Error: Unresolved compilation problems:
    The import org.eclipse cannot be resolved
    The import jakarta cannot be resolved
    ...
```

The `.class` files in `bin/` contain `throw new Error("Unresolved compilation problems")` stubs, even though Eclipse shows **no compilation errors** in the Problems view.

### Root Cause
This appears to be an Eclipse 4.38 (2025-12) PDE/JDT bug. During incremental builds, the `requiredPlugins` classpath container becomes temporarily empty, causing JDT to compile all classes without access to Eclipse platform or Jakarta dependencies. The broken `.class` files are written to `bin/`, and JDT does not recompile them once the classpath resolves.

### Workaround
Do **Project > Clean** before re-launching the Eclipse Application. This forces a full rebuild with a properly resolved classpath.

### What Was Investigated (did NOT fix the issue)
- Removing m2e nature/builder from `.project`
- Removing redundant `Import-Package` entries from `MANIFEST.MF`
- Disabling `org.eclipse.jdt.core.compiler.release` in JDT settings
- Cleaning extra JARs from `lib/` directory

### Related Eclipse Bugs
- [JDT Bug 561287 - Incremental compilation reports spurious errors](https://bugs.eclipse.org/bugs/show_bug.cgi?id=561287)
- [PDE Issue #902 - javax.inject resolution failures](https://github.com/eclipse-pde/eclipse.pde/issues/902)
- [JDT Issue #1347 - Incremental build regression](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/1347)

---

## Build Configuration Notes

### MANIFEST.MF Bundle-ClassPath
The `Bundle-ClassPath` must list `.` (compiled classes) and all embedded JARs in `lib/`. These must be kept in sync with `build.properties` (`bin.includes`) and `.classpath` (lib entries).

### build.properties
`bin.includes` must contain `.` to include compiled Java classes in the Tycho-built JAR. Without it, the bundle JAR will contain no `.class` files.

### Maven Dependency Management
Maven dependencies are copied to `lib/` via `maven-dependency-plugin` during `process-resources`. The `includeGroupIds` filter limits which transitive dependencies are included. Run `mvn clean install` to refresh the `lib/` directory.

The `.classpath` lib entries must be maintained manually to match the JARs in `lib/`.

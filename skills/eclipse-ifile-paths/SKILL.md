---
name: eclipse-ifile-paths
description: Converts between Eclipse IFile, IPath, and filesystem paths. Use when working with Eclipse plugin file operations, resolving workspace-relative paths, or converting between IFile and absolute paths.
---

# Eclipse IFile and IPath Reference

## IFile Path Methods

Given an `IFile f` representing a file like `D:/dev/workset/archetypes/java-minimal/pom.xml` in project `java-minimal`:

| Method | Result | Description |
|---|---|---|
| `f.getFullPath().toOSString()` | `\java-minimal\pom.xml` | Workspace-relative path with OS separators |
| `f.getFullPath().toString()` | `/java-minimal/pom.xml` | Workspace-relative path with forward slashes |
| `f.getFullPath().toPortableString()` | `/java-minimal/pom.xml` | Workspace-relative, always forward slashes |
| `f.getRawLocation().toPortableString()` | `D:/dev/workset/archetypes/java-minimal/pom.xml` | Absolute filesystem path |
| `f.getRawLocation().toString()` | `D:/dev/workset/archetypes/java-minimal/pom.xml` | Absolute filesystem path |

## Preferred Default: `toPortableString()`

Always use **`getFullPath().toPortableString()`** as the default when converting an `IFile` path to a string. It is the best choice because:

- **Cross-platform**: always uses forward slashes, regardless of OS.
- **Consistent**: produces the same output as `toString()` but with an explicit portable contract.
- **Round-trips cleanly**: the output resolves back to an `IFile` via `IPath.fromOSString()` without any conversion.

```java
// Preferred way to get a path string from an IFile:
String path = file.getFullPath().toPortableString(); // "/java-minimal/pom.xml"

// Resolves back without issues:
IFile resolved = ResourcesPlugin.getWorkspace().getRoot()
    .getFile(IPath.fromOSString(path));
```

Use `getRawLocation().toPortableString()` only when you need the absolute filesystem path (e.g. for `java.io.File` or external process calls).

## Key Rules

- **`getFullPath()`** returns the **workspace-relative** path, always starting with `/<project-name>/`.
- **`getRawLocation()`** returns the **absolute filesystem** path on disk.
- `toString()` and `toPortableString()` both use forward slashes. `toOSString()` uses the OS separator (`\` on Windows).

## Looking Up an IFile from a Path

To resolve a workspace-relative path back to an `IFile`:

```java
// All three formats work with IPath.fromOSString:
IPath path = IPath.fromOSString("/java-minimal/pom.xml");
IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
boolean exists = file.exists(); // true
```

`IPath.fromOSString()` accepts both forward slashes and backslashes, so all `getFullPath()` output formats resolve correctly.

## Common Pitfalls

- **Do not use `getRawLocation()` with `getRoot().getFile()`** — it expects a workspace-relative path, not an absolute one.
- **`getFullPath()` includes the project name** as the first segment. It is not project-relative.
- To get a **project-relative** path, use `f.getProjectRelativePath()` which returns e.g. `pom.xml` (no leading project segment).

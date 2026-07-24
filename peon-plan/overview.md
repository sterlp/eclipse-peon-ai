# File Write Tools — Design & Behavior Sync

## Context

Two parallel tool classes provide file write/edit operations:
- **DiskFileWriteTool** (`org.sterl.llmpeon.tool.tools`) — real filesystem via NIO, configurable workingDir, can be disabled in config
- **EclipseWorkspaceWriteFileTool** (`org.sterl.llmpeon.parts.tools`) — Eclipse virtual filesystem via IResource API, workspace-scoped, always available

## Goal

Document both tool classes' design and sync their delete behavior — `diskDeleteFile` currently only deletes single files, while `eclipseDeleteResource` deletes recursively. Both should delete recursively for consistent agent expectations.

## Business Rules

### R1: Recursive Delete Behavior ✅
Both delete tools must delete recursively — files and non-empty directories.

**BDD:**
```
GIVEN a directory with files exists at /path/to/dir
WHEN diskDeleteFile("/path/to/dir") is called
THEN the directory and all its contents are deleted

GIVEN a directory with files exists at /Project/src/foo
WHEN eclipseDeleteResource("/Project/src/foo") is called
THEN the directory and all its contents are deleted
```

### R2: Eclipse Tools as Safe Sandbox ✅
Eclipse workspace tools operate within the Eclipse virtual file system, providing a bounded sandbox — the AI agent can only access files within open Eclipse projects. This is the safer default mode.

**BDD:**
```
GIVEN EclipseWorkspaceWriteFileTool is active
WHEN the agent tries to write to a path outside any open project
THEN the tool throws an IllegalArgumentException with open project list
```

### R3: Disk Tools as Configurable Override ✅
Disk tools can be disabled in global config. When enabled, they operate on the real filesystem with a configurable workingDir. When disabled, only Eclipse tools are available — enforcing the sandbox.

**BDD:**
```
GIVEN disk tools are disabled in the global config
WHEN the agent is active with default tool service
THEN no disk tools (DiskFileWriteTool, DiskFileReadTool, DiskGrepTool) are in the tool service
AND only Eclipse workspace tools are available

GIVEN disk tools are enabled and workingDir is set to /some/path
WHEN diskWriteFile("/some/path/file.txt", "content") is called
THEN the file is written to the real filesystem at /some/path/file.txt
```

## Tool Comparison

| Operation | Disk | Eclipse | Notes |
|-----------|------|---------|-------|
| Write/Update File | `diskWriteFile` | `eclipseWriteFile` | Both create parent dirs |
| Delete | `diskDeleteFile` | `eclipseDeleteResource` | **Both recursive** (R1) |
| Replace Lines | `diskReplaceLines` | `eclipseReplaceLines` | Same interface |
| Edit (string replace) | `diskEditFile` | `eclipseEditFile` | Same interface; Eclipse allows null newString |
| Rename/Move | `diskRenameResource` | `eclipseRenameResource` | Both create parent dirs |
| Insert Lines | `diskInsertLines` | `eclipseInsertLines` | Same interface |

## Affected Files

### Design docs (new)
- `docs/disk-file-write-tool.md` — DiskFileWriteTool design + sandbox rationale
- `docs/eclipse-workspace-write-file-tool.md` — EclipseWorkspaceWriteFileTool design

### Code changes
- `org.sterl.llmpeon.core/src/main/java/org/sterl/llmpeon/tool/tools/DiskFileWriteTool.java` — fix `diskDeleteFile` to delete recursively

### ADR
- `docs/adr/0015-eclipse-sandbox-boundary.md` — Eclipse VFS as AI sandbox boundary

### Registry
- `docs/index.md` — add both new design doc entries
- `docs/adr/index.md` — add ADR-0015

## Steps

1. Create ADR-0015: Eclipse VFS as sandbox boundary
2. Create `docs/disk-file-write-tool.md` design doc
3. Create `docs/eclipse-workspace-write-file-tool.md` design doc
4. Update `docs/index.md` and `docs/adr/index.md` registries
5. Fix `DiskFileWriteTool.diskDeleteFile` to use `Files.walk` + recursive delete
6. Add/update tests for recursive delete behavior
7. Build + run tests


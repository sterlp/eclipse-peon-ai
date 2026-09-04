# Disk File Write Tool

## Goal

Provide file write/edit operations on the real filesystem, scoped to a configurable working directory. Disabled by default in the global config — only enabled for agents that need real filesystem access (e.g. scaffold agent for `.peon` config).

## Package & Class

- **Package**: `org.sterl.llmpeon.tool.tools`
- **Class**: `DiskFileWriteTool extends AbstractTool`
- **Constructor**: `DiskFileWriteTool(Path workingDir)` — resolves and normalizes the working directory
- **isEditTool()**: `true`

## Tools

### `diskWriteFile(filePath, content)`
Write or overwrite a file. Creates parent directories. Returns "Created" or "Updated" with relative path.

### `diskDeleteFile(filePath)`
Delete a file or directory **recursively**. Works on both files and non-empty directories.

### `diskReplaceLines(filePath, line, newContent)`
Replace lines by 1-based line number. Reads entire file, replaces, writes back.

### `diskEditFile(filePath, oldString, newString)`
Replace **all** occurrences of the exact string — Replace-All is deliberate (saves tool rounds).
The tool output reports the number of replacements: `replaced N occurrence(s)`.
`newString = null` deletes the match (`deleted N occurrence(s)`).
Errors if 0 matches. Errors if oldString equals newString.

**✅ fixed (Bug-Hunt #1, 2026-09-04):** Javadoc und Tool-Beschreibung (disk **und** Eclipse) sagten
„first occurrence" — das Tool log über seine eigene Semantik. SOLL (User): Replace-All + Count-Disclosure;
identisch in beiden Tool-Familien (ein Verhalten, eine Implementierung).

### `diskRenameResource(sourcePath, targetPath)`
Rename or move. Creates target parent directories. Errors if target exists.

### `diskInsertLines(filePath, afterLine, newContent)`
Insert text at a position. `afterLine = null` appends, `0` prepends, `1..n` inserts after that line.

## Key Technical Decisions

| Concern | Decision |
|---------|----------|
| **Path resolution** | `FileUtils.resolve(workingDir, path)` — resolves relative to workingDir |
| **File I/O** | Java NIO `Files.*` API — no Eclipse dependencies |
| **Delete** | Recursive via `Files.walk` + reverse order delete (children before parents) |
| **Monitoring** | `monitor.onFileUpdate(AiFileUpdate)` for tracking changes |
| **WorkingDir** | Set via constructor or `setWorkingDir()` — can change at runtime (scaffold agent reads from config on each call) |

## See Also

- [ADR-0015](adr/0015-eclipse-sandbox-boundary.md) — Eclipse VFS as sandbox boundary
- [Eclipse Workspace Write Tool](eclipse-workspace-write-file-tool.md) — parallel Eclipse-scoped tool set


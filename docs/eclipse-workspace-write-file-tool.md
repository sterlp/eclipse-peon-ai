# Eclipse Workspace Write Tool

## Goal

Provide file write/edit operations within the Eclipse virtual file system, bounded to open Eclipse projects. Always available — no config toggle. Serves as the safe sandbox for AI agents.

## Package & Class

- **Package**: `org.sterl.llmpeon.parts.tools`
- **Class**: `EclipseWorkspaceWriteFileTool extends AbstractEclipseTool`
- **isEditTool()**: `true`
- **currentProject**: optional fallback project for path resolution when the target project cannot be determined

## Tools

### `eclipseWriteFile(filePath, content)`
Write or overwrite a file. Creates parent directories. Resolves target project from path or falls back to `currentProject`.

### `eclipseDeleteResource(filePath)`
Delete a file or directory **recursively** within the Eclipse workspace. Uses `IResource.delete()` with `KEEP_HISTORY`, retries with `FORCE` on failure.

### `eclipseReplaceLines(filePath, line, newContent)`
Replace lines by 1-based line number. Reads via `IFile.readString()`, replaces, writes back.

### `eclipseEditFile(filePath, oldString, newString)`
Replace **all** occurrences of the exact string — Replace-All + Count-Disclosure, identisch zu
`diskEditFile` (der bisherige Doc-Text „Errors if 0 or >1 matches" war Drift und ist korrigiert).
`newString = null` löscht die Treffer. **Edit-Guard:** `oldString` ist Pflicht-Anker (non-blank,
`trim().length() >= 3`) — siehe [disk-file-write-tool.md](disk-file-write-tool.md), ein Verhalten,
eine Implementierung (`FileUtils.applyEdit`). Das frühere `oldString == null → ""` entfällt — es
war der Root Cause der Insert-Korruption (2026-09-16).

### `eclipseUpdateOpenFile(oldString, newString)` *(neu dokumentiert — Lücke C2, Review 2026-09-17)*
Edit im **aktuell offenen Editor** über `IDocument` + Save — der einzige Weg in Edge-Cases wie
ABAB (Checkout/Lock). Läuft auf dem UI-Thread, Timeout 2 min, meldet `monitor.onFileUpdate`.
Derselbe **Edit-Guard** wie `eclipseEditFile` — auch hier entfällt das `oldString == null → ""`.
Erfolgsmeldung nennt Pfad + `replaced/deleted N occurrence(s)`; bei Save-Fehler:
`Save failed! Ask user to save editor.`

### `eclipseRenameResource(sourcePath, targetPath)`
Rename or move within the workspace. Creates target parent folders. Errors if target exists. Uses `IResource.move()` with `KEEP_HISTORY`.

### `eclipseInsertLines(filePath, afterLine, newContent)`
Insert text at a position. `afterLine = null` appends, `0` prepends, `1..n` inserts after that line.

## Key Technical Decisions

| Concern | Decision |
|---------|----------|
| **Path resolution** | `EclipseUtil.resolveInEclipse(filePath)` for existing resources; `EclipseUtil.findOpenProject(filePath)` + `currentProject` fallback for new files |
| **File I/O** | Eclipse `IResource` API — `IFile.readString()`, `IoUtils.writeFile()`, `IResource.delete()`, `IResource.move()` |
| **Delete** | Recursive via `IResource.delete(IResource.KEEP_HISTORY)`, retry with `FORCE` on failure |
| **Progress monitoring** | `IProgressMonitor` from `EclipseAiMonitor` — reports work on each tool call |
| **Derived resources** | `DERIVED_SOURCES` filter (`/target/`, `/bin/`, `.class`, `.git`) — excluded from search results |
| **Monitoring** | `monitor.onFileUpdate(AiFileUpdate)` with old/new content tracking |

## See Also

- [ADR-0015](adr/0015-eclipse-sandbox-boundary.md) — Eclipse VFS as sandbox boundary
- [Disk File Write Tool](disk-file-write-tool.md) — parallel disk-scoped tool set


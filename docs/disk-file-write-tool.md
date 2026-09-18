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
Write or overwrite a file. Creates parent directories. Returns "Created" or "Updated" — **with the absolute path** (workingDir resolved).

### Success-Message: absolute path ✅ (E5, User 2026-09-06)
Every disk tool that reports a path in its success message reports the **absolute path** (workingDir resolved), not a workingDir-relative one — in sync with the `eclipse*` tool family ("one behaviour, one implementation").

**BDD:**
```
GIVEN diskWriteFile succeeds for "sub/x.txt" in a configured workingDir
WHEN the success message is rendered
THEN it carries the absolute path of the written file (workingDir resolved)
```

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

### Edit-Guard: `oldString` ist Pflicht-Anker ❌ specified (2026-09-17)

`oldString` ist in **allen** Edit-Tools beider Familien (`diskEditFile`, `eclipseEditFile`,
`eclipseUpdateOpenFile`) ein verpflichtender Anker mit Mindestlänge. Der Check läuft **explizit und
up-front** im geteilten `FileUtils.applyEdit` — nicht als Nebeneffekt des Countings:

- `null`, leer oder blank → `IllegalArgumentException`, die die Anforderung nennt
- `trim().length() < 3` → dieselbe `IllegalArgumentException` — **mindestens 3
  nicht-Whitespace-Zeichen**

**WEIL:** Die Korruption vom 2026-09-16 (7,3 Mio. Zeilen) entstand, weil ein leerer `oldString`
still als `""` behandelt wurde — `String.replace("", x)` fügt an jeder Position ein, und der
Marker `replaced 0 occurrence(s)` log über das echte Verhalten (AGENTS.md: ein Tool lügt nie). Ein
Anker unter 3 Zeichen ist fast immer ein Agentenfehler; Mikro-Edits haben Auswege
(`replaceLines`, `insertLines`, `writeFile`). Zwischen „1 Zeichen nach Trim" und „3 Zeichen"
entschied der User bewusst für **eine einfache Schwelle ohne Trim-Sonderfälle** („schwierig" —
Einfachheit schlägt Randfall-Intelligenz).

> **IST-Notiz (2026-09-17):** Der Schutz existiert heute nur **zufällig** — `oldString=""` fällt
> durch `contains("")==true` und fliegt erst in `countOccurrences` mit der irreführenden Message
> „Content is empty or null - cannot count!". Der Bau ersetzt das durch den expliziten Check; der
> Count-Schutz bleibt als zweite Schranke bestehen.

**BDD:**
```
GIVEN diskEditFile mit oldString=null oder blank
WHEN der Edit läuft
THEN IllegalArgumentException mit benannter Anforderung („oldString is required, min 3
     non-whitespace chars") — kein stiller ""-Replace

GIVEN diskEditFile mit oldString="}}" (2 Zeichen)
WHEN der Edit läuft
THEN dieselbe IllegalArgumentException — Ausweg: replaceLines/insertLines/writeFile

GIVEN diskEditFile mit gültigem oldString (>= 3 nicht-Whitespace-Zeichen)
WHEN der Edit läuft
THEN Replace-All + Count-Disclosure wie gehabt
```

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


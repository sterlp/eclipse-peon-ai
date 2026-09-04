# Tool Descriptions — Inventory & Optimization Plan

**Status:** ✅ **done** — 25/25 `@Tool`-Descriptions optimiert (Runde 2: kurze Version 10-17 Wörter), `buildWithAgent` → `buildWithDev` rename, static tool-name constants in JonDelegateTool, Test auf structural checks umgestellt. Build grün, verifiziert.

## IST

- **42 `@Tool` methods** across 20 tool classes (core + Eclipse plugin)
- Descriptions vary wildly: some 5 words (`"Read file - not eclipse."`), some 3 lines of multi-paragraph text
- No consistency in style (imperative vs descriptive, with/without context hints)
- AskUserTool already optimized — excluded from plan
- Keine zentrale Doku für die LLM-Perspektive (was der Agent "sieht")
- Homepage dokumentiert keine Tools für End-User

## SOLL

Alle `@Tool`-Beschreibungen folgen einem einheitlichen Muster:
- **1 Zeile, imperativ**, 10-25 Wörter
- **Name + Zweck + Schlüssel-Constraint** (was es NICHT ist, wann man es nutzt)
- Workspace-Tools: `workspace-` Prefix im Namen zur Unterscheidung von Disk-Tools
- Parameter-Descriptions (`@P`) erhalten kurze kontextreiche Hints

**WEIL:** Konsistente, token-effiziente Descriptions verbessern die Tool-Auswahl des LLM, reduzieren Halluzinationen und sparen ~50-150 Tokens/Tool-Call.

---

## Tool-Inventar mit Bewertung & Empfehlung

### DiskFileReadTool (`org.sterl.llmpeon.tool.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 1 | `diskReadFile` | `"Read file - not eclipse."` | ❌ zu kurz, negativ | `"Read a file from the filesystem. Use filePath relative to working directory or absolute. Pass startLine/endLine (1-based, 0=all) to read partial files."` | Positiv formulieren, Parameter-Kontext hinzufügen |
| 2 | `diskSearchFiles` | `"Search files by name. Use '*' to list all files recursively."` | ⚠️ okay aber unvollständig | `"Find files by name pattern (*, ? wildcards). Searches working directory recursively. Pass limit to cap results (default 50, 0=unlimited)."` | Limit-Parameter erwähnt, Wildcard-Syntax klar |
| 3 | `diskListDirectory` | `"List directory (non-recursive)."` | ⚠️ zu kurz | `"List contents of a directory (non-recursive). Pass empty or '/' for working directory root. Returns [DIR]/[FILE] prefixed paths."` | Output-Format genannt, Default-Verhalten klar |

### DiskFileWriteTool (`org.sterl.llmpeon.tool.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 4 | `diskWriteFile` | `"Write file. Creates parent dirs and overwrites if exists."` | ✅ gut | *(kein Change)* | - |
| 5 | `diskDeleteFile` | `"Delete file or directory recursively."` | ✅ gut | *(kein Change)* | - |
| 6 | `diskReplaceLines` | `"Precise, line-targeted updates/insert lines by line number. newContent may span multiple lines."` | ⚠️ verwirrend (sagt "updates/insert") | `"Replace a single line in a file by 1-based line number. newContent may span multiple lines, replacing only the specified line."` | Insert vs Replace klären — das ist Replace, kein Insert |
| 7 | `diskEditFile` | `"Replace the exact string in a file."` | ⚠️ zu kurz | `"Replace the first occurrence of an exact string in a file. Error if oldString not found or identical to newString."` | Fehlerbedingungen genannt, "first occurrence" präzisiert |
| 8 | `diskRenameResource` | `"Rename or move a file or directory. Creates target parent folders."` | ✅ gut | *(kein Change)* | - |
| 9 | `diskInsertLines` | `"Insert text into a file at a specific position. Omit afterLine to append at end. 0 inserts before the first line (prepend). 1..n inserts after that line."` | ✅ gut | *(kein Change)* | - |

### DiskGrepTool (`org.sterl.llmpeon.tool.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 10 | `diskGrepFiles` | `"Search file contents for text (not eclipse)."` | ❌ negativ, zu kurz | `"Search file contents for text or regex pattern. Optionally scope to a subdirectory and file extension (e.g. .java). Returns matching files with occurrence count."` | Regex-Erwähnung, Output-Format, positive Formulierung |

### ShellTool (`org.sterl.llmpeon.tool.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 11 | `readOperationSystemInformation` | `"OS/user info (os.name, user.name, path info etc.)."` | ⚠️ zu unpräzise | `"Read environment information: OS name, Java version, user home, PATH, temp directory. No parameters needed."` | Konkrete Felder genannt, "no parameters" spart Guessing |
| 12 | `shellRunCommand` | `"Run shell command. (mvn, npm etc.) Not for file I/O."` | ⚠️ negativ, Constraint unklar | `"Execute a shell command (mvn, npm, git etc.). Use workingDirectory for disk path, timeout in seconds, tailLines to cap output. Not for file operations — use read/write tools instead."` | Parameter-Kontext, Alternative genannt |

### WebFetchTool (`org.sterl.llmpeon.tool.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 13 | `webFetchAsMarkdown` | `"Fetch URL content as Markdown."` | ⚠️ zu kurz | `"Fetch a URL and convert its HTML content to Markdown. Handles redirects, charset detection, and 30s timeout. Returns error on HTTP 4xx/5xx."` | Verhalten bei Errors, Timeout, Charset genannt |

### CompactSessionTool (`org.sterl.llmpeon.tool.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 14 | `compactSession` | *(3 lines)* | ✅ gut | *(kein Change)* | Multi-line ist hier gerechtfertigt — komplexes Tool mit Batch-Hint |

### SearchAgentTool (`org.sterl.llmpeon.tool.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 15 | `searchAgent` | `"Sub-agent for complex multi-step search/research - to save tokens."` | ✅ gut | *(kein Change)* | - |

### JonDelegateTool (`org.sterl.llmpeon.tool.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 16 | `talkPlan` | *(1 line, ausführlich)* | ✅ gut | *(kein Change)* | - |
| 17 | `planWithPlanAgent` | *(1 line, ausführlich)* | ✅ gut | *(kein Change)* | - |
| 18 | `askDev` | *(1 line, ausführlich)* | ✅ gut | *(kein Change)* | - |
| 19 | `buildWithDev` | *(1 line, ausführlich)* | ✅ gut | *(kein Change)* | - |

### SkillTool (`org.sterl.llmpeon.tool.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 20 | `skillRead` | `"Load/read an SKILL using its name."` | ⚠️ zu kurz | `"Read a skill's prompt content by name. Returns the skill body or lists available skills if not found. Use skillList first to discover names."` | Fehlerfall + Entdeckungs-Pattern genannt |
| 21 | `skillList` | `"List all active SKILL - use it before complex tasks, to verify if a skill is available."` | ⚠️ "SKILL" großgeschrieben, redundant | `"List all available skills with short descriptions. Call this before complex tasks to discover relevant skills."` | Kleingeschrieben, klarer |
| 22 | `skillReadFile` | `"Read a file from a SKILL directory using its relative path."` | ⚠️ zu kurz | `"Read a specific file from a skill's directory by relative path. Use this for skill assets (templates, configs) beyond the main prompt."` | Use-Case genannt |

### EclipseWorkspaceReadFileTool (`org.sterl.llmpeon.parts.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 23 | `eclipseOpenFileInEditor` | `"Open a workspace file, not directory, in the Eclipse editor to show it to the user e.g. a plan or summary."` | ✅ okay | *(kein Change)* | - |
| 24 | `eclipseReadFile` | `"Read workspace file (e.g. '/Project/src/Foo.java')."` | ⚠️ zu kurz | `"Read a file from the Eclipse workspace. Pass workspace-relative path (e.g. /Project/src/Foo.java). Use startLine/endLine (1-based, 0=all) for partial reads."` | Partial-Read Parameter erwähnt |
| 25 | `eclipseSearchFiles` | `"Find any files workspace-wide by name (*, ? wildcard supported). Default file-path finder."` | ✅ gut | *(kein Change)* | - |
| 26 | `eclipseList` | `"List workspace directory/projects (non-recursive). Empty path lists all projects."` | ✅ gut | *(kein Change)* | - |

### EclipseWorkspaceWriteFileTool (`org.sterl.llmpeon.parts.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 27 | `eclipseReplaceLines` | `"Replace lines by line number. newContent may span multiple lines."` | ⚠️ verwirrend | `"Replace a single line in a workspace file by 1-based line number. newContent may span multiple lines, replacing only the specified line."` | Insert vs Replace klären |
| 28 | `eclipseEditFile` | `"Replace exact string in workspace file."` | ⚠️ zu kurz | `"Replace the first occurrence of an exact string in a workspace file. newString can be null (deletes the match)."` | Null-Verhalten + "first occurrence" |
| 29 | `eclipseWriteFile` | `"Write file to workspace. Creates parent dirs and overwrites if exists."` | ✅ gut | *(kein Change)* | - |
| 30 | `eclipseInsertLines` | *(1 line, ausführlich)* | ✅ gut | *(kein Change)* | - |
| 31 | `eclipseRenameResource` | `"Rename or move a workspace file or directory. Creates target parent folders."` | ✅ gut | *(kein Change)* | - |
| 32 | `eclipseDeleteResource` | `"Delete workspace file or directory recursively."` | ✅ gut | *(kein Change)* | - |

### EclipseGrepTool (`org.sterl.llmpeon.parts.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 33 | `eclipseGrepFiles` | `"Search workspace file contents for text."` | ⚠️ zu kurz | `"Search workspace files for text content. Optionally scope to a project/folder path and file extension. Returns matching files with occurrence counts (max 100)."` | Scope, Extension, Limit genannt |

### EclipseCodeNavigationTool (`org.sterl.llmpeon.parts.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 34 | `eclipseFindJavaType` | `"Java: Find Java types by name/wildcard. Searches in workspace, JDK and used JARs. Java type metadata only."` | ✅ gut | *(kein Change)* | - |
| 35 | `eclipseReadTypeSource` | `"Java: Read source or JavaDoc of the type. Covers JDK and used JARs — prefer over decompiling JARs. java.io.File etc."` | ✅ gut | *(kein Change)* | - |
| 36 | `eclipseFindReferences` | `"Java: Find usages of a type, or a method when methodName is set. Best way to find all java class usages."` | ✅ gut | *(kein Change)* | - |
| 37 | `eclipseFindResource` | `"JDT: Find files by name/glob (*, ?), optionally scoped to one project. Workspace-wide or with limit: use searchWorkspaceFiles; for content: grepWorkspaceFiles."` | ⚠️ verwirrend (verweist auf andere Tools mit falschen Namen) | `"Find workspace resources by name pattern (*, ? wildcards), optionally scoped to a project. Returns full paths. For workspace-wide search with limit: use eclipseSearchFiles; for content: eclipseGrepFiles."` | Tool-Namen korrigieren (eclipseSearchFiles/eclipseGrepFiles) |

### EclipseBuildTool (`org.sterl.llmpeon.parts.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 38 | `eclipseListAllOpenProjects` | `"List open workspace projects with their eclipse paths, disk paths, and natures."` | ✅ gut | *(kein Change)* | - |
| 39 | `eclipseReadProjectProblems` | `"List build errors/warnings of a project."` | ⚠️ zu kurz | `"List compile errors and warnings for a project. Call after eclipseBuildProject to check build status. Returns problem messages with file and line numbers."` | Wann nutzen + Output-Format |
| 40 | `eclipseBuildProject` | `"Refresh and clean build the project. Returns errors/warnings. Preferred way to verify code changes or full refresh."` | ✅ gut | *(kein Change)* | - |
| 41 | `eclipseRefreshProject` | `"Refresh/sync a project with the disk status - if changes have been made outside eclipse e.g. with disk tools."` | ✅ gut | *(kein Change)* | - |

### EclipseRunTestTool (`org.sterl.llmpeon.parts.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 42 | `eclipseRunTests` | `"Run JUnit tests (auto-detects JUnit 3/4/5/6). For Eclipse plugin projects, usePluginTest=true starts the OSGi framework."` | ✅ gut | *(kein Change)* | - |

### EclipseConsoleLogTool (`org.sterl.llmpeon.parts.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 43 | `eclipseReadConsoleLog` | ✅ **umgesetzt (2b-3, 2026-09-03):** `"Read Eclipse console output. consoleName targets a console; grep filters lines (regex, literal fallback); lines tails the filtered result."` | ✅ done | — | Neuer Parameter `grep` (R3a–R3d, [eclipse-read-tools.md](eclipse-read-tools.md)); `lines` limitiert jetzt die **gefilterten** Zeilen |
| 44 | `eclipseListAvailableConsoles` | `"List all available consoles - e.g. for eclipse logs console etc."` | ⚠️ zu locker | `"List all open Eclipse consoles by name. Use this to discover console names for eclipseReadConsoleLog."` | Zweck + Referenz zum anderen Tool |

### PlanReadTool (`org.sterl.llmpeon.parts.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 45 | `planRead` | `"Reads the current saved plan (peon-plan/overview.md), if one exists."` | ✅ gut | *(kein Change)* | - |
| 46 | `hasPlan` | `"Checks whether a saved plan exists; returns its path (peon-plan/overview.md) to hand to buildWithDev, or states that none exists yet."` | ✅ gut | *(kein Change)* | - |

### PlanTool (`org.sterl.llmpeon.parts.tools`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 47 | `planRead` | `"Reads the current saved plan, if one exists."` | ✅ okay | *(kein Change)* | - |
| 48 | `planSave` | `"Save/overwirte the final implementation plan to peon-plan/overview.md. Call only after all design decisions are resolved."` | ⚠️ Typo "overwirte" | `"Save or overwrite the implementation plan to peon-plan/overview.md. Call only after all design decisions are resolved."` | Typo fixen |
| 49 | `planUpdate` | `"Update the current plan."` | ❌ zu kurz | `"Update the plan by replacing an exact text string with new text. Use this for incremental plan refinements without rewriting the whole file."` | Zweck + Use-Case |
| 50 | `planImplemented` | `"Archives the current plan with a timestamp once fully implemented. Call as the final tool call, or before starting a new plan to preserve the old one."` | ✅ gut | *(kein Change)* | - |

### WorkspaceMemoryTool (`org.sterl.llmpeon.parts.tools.memory`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 51 | `memoryAdd` | *(2 lines)* | ✅ gut | *(kein Change)* | Multi-line gerechtfertigt — wichtige Cross-Project-Funktion |
| 52 | `memoryRemove` | `"Remove a guideline by its number as shown in the Memory block."` | ✅ gut | *(kein Change)* | - |
| 53 | `memoryReplace` | `"Replace the text of an existing guideline."` | ⚠️ zu kurz | `"Replace the text of an existing guideline by its 1-based index. Use this to refine or correct stored facts without losing the slot."` | Index-Erwähnung + Use-Case |
| 54 | `memoryReset` | `"Clear all workspace guidelines. Use only if the user explicitly asks to reset memory."` | ✅ gut | *(kein Change)* | - |

### ReloadConfigTool (`org.sterl.llmpeon.scaffold`)

| # | Method | Current Description | Rating | New Description | Why |
|---|--------|---------------------|--------|-----------------|-----|
| 55 | `reloadConfig` | `"Reload all configuration (agents, skills, commands) — call after creating/editing artifacts so they become immediately available."` | ✅ gut | *(kein Change)* | - |

---

## Zusammenfassung der Änderungen

| Kategorie | Anzahl | Actions |
|-----------|--------|---------|
| **✅ Keine Änderung** | 28 | Already good |
| **⚠️ Verbesserung empfohlen** | 18 | Description optimieren |
| **❌ Korrektur nötig** | 3 | Typo, falsche Tool-Referenzen, zu negativ |
| **🎯 AskUserTool excluded** | 1 | Already optimized |

**Netto-Änderungen: 21 Tools** (von 55 `@Tool`-Methoden)

---

## Side-Quest: Javadoc → Docs Verlinkung

**Empfehlung: Ja, Javadoc sollte auf die docs zeigen.**

**WEIL:**
- Entwickler navigieren heute über "Go to Type/Method" → Javadoc ist die erste Anlaufstelle
- Ein `@see` oder `@link` auf `docs/<feature>.md` spart Suchzeit
- Beispiel: `@see <a href="docs/disk-file-write-tool.md">Disk File Write Tool Design</a>`
- Bei Eclipse: `@see ../../../../../../../docs/disk-file-write-tool.md` (relativ zum .java)

**Aber:** Relative Pfade in Javadoc sind spröde bei Refactoring. Alternative: `@see` mit URL-Template oder `@link` auf eine zentrale `docs/TOOLS.md` Index-Seite.

**Empfohlenes Pattern:**
```java
/**
 * Write or overwrite a file. Creates parent directories.
 *
 * @see <a href="docs/disk-file-write-tool.md">Disk File Write Tool</a>
 */
@Tool("Write file. Creates parent dirs and overwrites if exists.")
public void diskWriteFile(...) { ... }
```

---

## Next Steps

1. **Freigabe des Plans** — sollen die 21 Verbesserungen gebaut werden?
2. **Priorisierung:** Batch 1 (Disk-Tools + Grep), Batch 2 (Eclipse-Tools), Batch 3 (Skill/Console/Memory)
3. **Javadoc-Links** — separat oder mitschleifen?

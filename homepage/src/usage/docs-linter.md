# Docs Linter

The Docs Linter keeps Markdown requirements and the tests that cover them in sync. It scans opted-in Markdown files for requirement / use-case definitions, reads test sources for ID comment lines, and reports gaps such as missing coverage or orphaned IDs.

It is a **read-only** tool: it never writes files and only returns a text summary. Because it reads the project from the file system, every returned summary starts with the caveat:

```
Disk read — unsaved editor changes are not included.
```

The linter is always available and works independently of the **Disk tools enabled** setting.

## What is checked

- Every Markdown file under the configured `docRoots` that contains a frontmatter `idPrefix` is considered opted-in.
- Within opted-in docs, headings that define IDs matching the pattern are tracked as definitions.
- Test source files are scanned for lines that consist **only** of a comment prefix followed by one or more IDs:
  - `// UC-DL-34`
  - `# UC-DL-35, UC-DL-36`
  - `-- UC-DL-37`
- Such a comment line is the coverage evidence itself. A recognized function / method name next to it is optional and only adds context to the report.
- Definitions that are marked `✅` but have no evidence are reported as `UNBELEGT_ERLEDIGT`.
- IDs found in tests without a matching definition are reported as `VERWAIST`.

## Tool surface

The functionality is split into two read-only facades over one shared core:

| Facade | Available to | Tools |
|---|---|---|
| `DocsLinterTool` | `Peon-Dev` / `Da Mek`, `Peon-Plan` / `Da Thinka`, `Peon-Review` / `Da Dok`, and `Peon-PO (Jon)` | `lintDocs`, `lintDocsAndTests` |
| `DocsIdTool` | `Peon-PO (Jon)` only | `nextIds` |

Both facades share the same project root and the same discovery rules. They do not write files.

### `lintDocs`

Reads only the Markdown docs and reports definition issues. Does not read tests.

### `lintDocsAndTests`

Full lint: reads docs and test sources, matches evidence against definitions, and reports `UNBELEGT`, `UNBELEGT_ERLEDIGT`, and `VERWAIST`.

Parameters:

| Parameter | Default | Description |
|---|---|---|
| `root` | project root | Absolute disk path to lint from. |
| `docRoots` | `["docs"]` | One or more directories that contain the Markdown docs. |
| `testRoots` | `["."]` | One or more directories that contain test sources. |
| `testGlobs` | all code extensions | List of globs for test source files. If omitted, all code extensions known to Peon are scanned except plain text/data formats. |
| `idPattern` | `UC-[A-Z]+-...` | Regex that valid IDs must match. |

`testGlobs` is a list: multiple globs are combined with OR, overlapping roots or globs are deduplicated. Files inside any resolved `docRoot` are always excluded from the test scan, even if an explicit glob would match them.

### `nextIds` (DocsIdTool)

Returns the next available rule and use-case numbers per prefix found in opted-in docs. This method is only exposed through `DocsIdTool` and therefore only available to Jon.

## Default test scan

When `testGlobs` is omitted, the linter scans all file extensions that Peon treats as source code (for example `.java`, `.kt`, `.ts`, `.tsx`, `.js`, `.py`, `.go`, `.sql`, `.rs`, `.c`, `.cpp`, `.h`, `.cs`).

The following extensions are explicitly excluded because they are plain text or data files, even though Peon can read them:

- `md`, `txt`, `json`, `xml`, `csv`, `yaml`, `yml`, `properties`, `cfg`, `ini`, `toml`

Markdown files under `docRoots` are never treated as test evidence, regardless of the glob.

## Opting in a doc

Add an `idPrefix` to the frontmatter:

```md
---
idPrefix: DL
---

# UC-DL-1: sample use case
```

Without `idPrefix`, the file is skipped unless it contains definitions that match the configured pattern.

## Reading the result

The summary starts with the disk-read caveat and then shows the discovered scope, for example:

```
Disk read — unsaved editor changes are not included.

Scanned: 3 doc file(s): 2 linted, 1 not participating; 5 test source file(s), 3 carrying UC ids.
```

A docs-only run says `tests: not read` instead of reporting zero test files.

If findings exist, every single one is listed with its type, ID, and location (`file:line`). The summary is never truncated.
